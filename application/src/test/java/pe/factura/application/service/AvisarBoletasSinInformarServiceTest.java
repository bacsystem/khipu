package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.AvisosRepository.AvisoRegistrado;
import pe.factura.application.port.out.AvisosRepository.Destino;
import pe.factura.application.port.out.AvisosRepository.FilaBoletas;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.domain.plataforma.MotivoDeAviso;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El aviso automático de boletas sin informar (#356): al cliente, por correo, el día que una boleta llega al 5.º sin informarse y otra vez cuando la más antigua llega al 7.º; nunca
 * dos veces lo mismo en una semana, nunca si el servidor no entrega correos, y un correo que falla no frena a los demás ni deja un aviso fantasma.
 */
class AvisarBoletasSinInformarServiceTest {
    static final String PORTAL = "https://app.khipu.pe";
    static final LocalDate HOY = LocalDate.of(2026, 9, 13);
    static final Instant AHORA = Fakes.CLOCK.instant();

    UUID empresa = UUID.randomUUID();
    UUID cuenta = UUID.randomUUID();
    AvisosFake avisos = new AvisosFake();
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Correo correo = new Correo();
    ConfiguracionFake.Plantillas plantillasGuardadas = new ConfiguracionFake.Plantillas();
    AvisarBoletasSinInformarService service;

    /** Guarda lo que se mandó; puede fallar para un destinatario, o decir que no entrega (sin SMTP). */
    static class Correo implements CorreoSender {
        final List<String[]> enviados = new ArrayList<>();
        final List<Integer> avisosAlEnviar = new ArrayList<>();
        String fallaPara;
        boolean entrega = true;
        AvisosFake avisos;
        public void enviar(String para, String asunto, String cuerpo) {
            avisosAlEnviar.add(avisos.avisos.size());
            if (para.equals(fallaPara)) throw new IllegalStateException("SMTP caído");
            enviados.add(new String[]{para, asunto, cuerpo});
        }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { throw new AssertionError("los avisos no llevan adjuntos"); }
        public boolean entregaDeVerdad() { return entrega; }
    }

    {
        correo.avisos = avisos;
        avisos.uow = uow;
        service = new AvisarBoletasSinInformarService(avisos, correo, uow, Fakes.CLOCK, new PlantillasDeCorreo(plantillasGuardadas));
    }

    Destino destino() { return new Destino(cuenta, "Ana", "ana@negocio.pe"); }

    /** Una empresa con {@code pendientes} boletas sin informar, {@code urgentes} de ellas en el 7.º día o más, la más antigua emitida hace {@code diasLaMasAntigua}. */
    FilaBoletas fila(UUID id, Destino d, long pendientes, long urgentes, int diasLaMasAntigua) {
        return new FilaBoletas(id, "20100066603", "BODEGA SAN JUAN SAC", d, pendientes, urgentes, HOY.minusDays(diasLaMasAntigua));
    }

    void hay(FilaBoletas f) { avisos.boletas.add(f); }

    // --- el aviso del quinto día ------------------------------------------------------------------------------------------------------------

    @Test void alQuintoDiaAvisaALaCuentaCuantasBoletasFaltanYHastaCuando() {
        hay(fila(empresa, destino(), 3, 0, 5));

        int enviados = service.avisar(PORTAL).enviados();

        assertThat(enviados).isEqualTo(1);
        assertThat(correo.enviados).singleElement().satisfies(c -> {
            assertThat(c[0]).isEqualTo("ana@negocio.pe");
            assertThat(c[1]).isEqualTo("3 boletas de BODEGA SAN JUAN SAC sin informar a SUNAT");
            assertThat(c[2]).contains("BODEGA SAN JUAN SAC (RUC 20100066603)").contains("3 boletas sin informar").contains("15/09/2026 (en 2 días)").contains(PORTAL);
        });
        assertThat(avisos.avisos).singleElement().satisfies(a -> {
            assertThat(a.motivo()).isEqualTo(MotivoDeAviso.BOLETAS_POR_INFORMAR);
            assertThat(a.empresaId()).isEqualTo(empresa);
            assertThat(a.cuentaId()).isEqualTo(cuenta);
            assertThat(a.destinatario()).isEqualTo("ana@negocio.pe");
            assertThat(a.enviadoPor()).as("lo mandó el sistema, no un administrador").isNull();
        });
    }

    @Test void conLaMasAntiguaEnElSeptimoDiaElAvisoEsElUrgente() {
        hay(fila(empresa, destino(), 2, 1, 7));

        service.avisar(PORTAL);

        assertThat(avisos.avisos).singleElement().extracting(AvisoRegistrado::motivo).isEqualTo(MotivoDeAviso.BOLETAS_POR_VENCER_PLAZO);
        assertThat(correo.enviados.get(0)[2]).contains("13/09/2026 (hoy)").contains("fuera de plazo");
    }

    @Test void sinBoletasPorInformarNoHaceNada() {
        assertThat(service.avisar(PORTAL).enviados()).isZero();
        assertThat(correo.enviados).isEmpty();
        assertThat(avisos.avisos).isEmpty();
    }

    // --- sin repetir -------------------------------------------------------------------------------------------------------------------------

    @Test void noRepiteElMismoAvisoAntesDeUnaSemanaYLoRepiteDespues() {
        hay(fila(empresa, destino(), 1, 0, 5));
        service.avisar(PORTAL);

        assertThat(service.avisar(PORTAL).enviados()).as("la misma hora, otra vez").isZero();
        assertThat(correo.enviados).hasSize(1);

        avisos.avisos.set(0, retrasado(avisos.avisos.get(0), Duration.ofDays(6).plusHours(23)));
        assertThat(service.avisar(PORTAL).enviados()).as("hace 6 días y 23 horas: todavía no").isZero();

        avisos.avisos.set(0, retrasado(avisos.avisos.get(0), Duration.ofDays(7).plusMinutes(1)));
        assertThat(service.avisar(PORTAL).enviados()).as("hace más de una semana").isEqualTo(1);
        assertThat(correo.enviados).hasSize(2);
    }

    /** Pasar del aviso del 5.º día al del 7.º es un motivo nuevo: sale aunque no haya pasado la semana (solo pasaron dos días). */
    @Test void elAvisoUrgenteSaleAunqueElDelQuintoDiaSeHayaMandadoHaceDosDias() {
        hay(fila(empresa, destino(), 1, 0, 5));
        service.avisar(PORTAL);
        avisos.boletas.clear();
        hay(fila(empresa, destino(), 1, 1, 7));

        assertThat(service.avisar(PORTAL).enviados()).isEqualTo(1);

        assertThat(avisos.avisos).extracting(AvisoRegistrado::motivo).containsExactly(MotivoDeAviso.BOLETAS_POR_INFORMAR, MotivoDeAviso.BOLETAS_POR_VENCER_PLAZO);
        assertThat(service.avisar(PORTAL).enviados()).as("y el urgente tampoco se repite").isZero();
    }

    private static AvisoRegistrado retrasado(AvisoRegistrado a, Duration hace) {
        return new AvisoRegistrado(a.id(), a.empresaId(), a.cuentaId(), a.motivo(), a.destinatario(), AHORA.minus(hace), a.enviadoPor());
    }

    // --- a quién y cuándo ----------------------------------------------------------------------------------------------------------------------

    @Test void unaEmpresaSinCuentaConCorreoNoSeAvisaYLasDemasSi() {
        UUID integracion = UUID.randomUUID();
        hay(fila(integracion, new Destino(null, null, null), 4, 0, 6));
        hay(fila(empresa, destino(), 1, 0, 5));

        assertThat(service.avisar(PORTAL).enviados()).isEqualTo(1);

        assertThat(avisos.avisos).extracting(AvisoRegistrado::empresaId).containsExactly(empresa);
    }

    @Test void cadaEmpresaRecibeSuPropioAviso() {
        UUID otra = UUID.randomUUID();
        UUID otraCuenta = UUID.randomUUID();
        hay(fila(empresa, destino(), 1, 0, 5));
        hay(fila(otra, new Destino(otraCuenta, "Luis", "luis@negocio.pe"), 2, 0, 6));

        assertThat(service.avisar(PORTAL).enviados()).isEqualTo(2);

        assertThat(correo.enviados).extracting(c -> c[0]).containsExactly("ana@negocio.pe", "luis@negocio.pe");
    }

    /** Sin SMTP el correo no sale. Reservar el aviso igual lo daría por mandado una semana y el cliente no se enteraría: no se reserva nada y se reintenta cuando haya correo. */
    @Test void sinEnvioDeCorreosNoSeReservaNada() {
        correo.entrega = false;
        hay(fila(empresa, destino(), 1, 0, 5));

        assertThat(service.avisar(PORTAL).enviados()).isZero();

        assertThat(avisos.avisos).isEmpty();
        assertThat(correo.enviados).isEmpty();
        assertThat(avisos.consultas).as("ni siquiera consulta").isEmpty();

        correo.entrega = true;
        assertThat(service.avisar(PORTAL).enviados()).isEqualTo(1);
    }

    // --- el orden: reservar, mandar, y deshacer si no salió ----------------------------------------------------------------------------------

    @Test void reservaDentroDeUnaTransaccionYMandaElCorreoDespuesConElAvisoYaReservado() {
        hay(fila(empresa, destino(), 1, 0, 5));

        service.avisar(PORTAL);

        assertThat(avisos.reservadoDentro).containsExactly(true);
        assertThat(correo.avisosAlEnviar).as("al mandar el correo el aviso ya estaba reservado").containsExactly(1);
        assertThat(uow.dentro).as("el correo salió fuera de la transacción").isFalse();
    }

    @Test void siElCorreoFallaSeAnulaLaReservaSeSigueConLasDemasYSePuedeReintentar() {
        UUID otra = UUID.randomUUID();
        correo.fallaPara = "ana@negocio.pe";
        hay(fila(empresa, destino(), 1, 0, 5));
        hay(fila(otra, new Destino(UUID.randomUUID(), "Luis", "luis@negocio.pe"), 1, 0, 5));

        var resultado = service.avisar(PORTAL);

        assertThat(resultado.enviados()).isEqualTo(1);
        assertThat(resultado.fallos()).singleElement().asString().contains("20100066603").contains("ana@negocio.pe").contains("SMTP caído");
        assertThat(avisos.anulados).hasSize(1);
        assertThat(avisos.avisos).extracting(AvisoRegistrado::empresaId).as("solo queda lo que salió").containsExactly(otra);

        correo.fallaPara = null;
        assertThat(service.avisar(PORTAL).enviados()).as("la que falló se reintenta").isEqualTo(1);
        assertThat(avisos.avisos).extracting(AvisoRegistrado::empresaId).containsExactlyInAnyOrder(empresa, otra);
    }

    @Test void laFechaDeHoyEsLaDelRelojDelServicio() {
        service.avisar(PORTAL);

        assertThat(avisos.hoyVisto).isEqualTo(HOY);
    }
}
