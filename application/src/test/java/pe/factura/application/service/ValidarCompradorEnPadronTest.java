package pe.factura.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ValidarCompradorUseCase.Aviso;
import pe.factura.application.port.out.PadronRucRepository.CargaRegistrada;
import pe.factura.application.port.out.PadronRucRepository.Contribuyente;
import pe.factura.application.port.out.PadronRucRepository.ResultadoCarga;
import pe.factura.application.service.ConsultarPadronRucServiceTest.PadronEnMemoria;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Receptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #378: las reglas de SUNAT sobre el RUC del comprador. Solo 1083 (no existe) rechaza; 4013 (no activo) y 4014 (no habido) son observaciones: SUNAT acepta el
 * comprobante. Y solo se confía en el padrón mientras sea reciente.
 */
class ValidarCompradorEnPadronTest {
    static final Instant AHORA = Instant.parse("2026-10-16T15:00:00Z");
    static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);
    static final Duration TOPE = Duration.ofDays(3);
    static final String RUC = "20601234565";
    static final Receptor COMPRADOR = new Receptor("6", RUC, "CLIENTE SAC", "AV 1");

    PadronEnMemoria padron = new PadronEnMemoria();
    ValidarCompradorEnPadron validador = new ValidarCompradorEnPadron(padron, RELOJ, TOPE);

    @BeforeEach void padronAlDia() { padron.cargada = AHORA.minus(Duration.ofHours(10)); }

    private void registrar(String estado, String condicion) {
        padron.contribuyentes.add(new Contribuyente(RUC, "CLIENTE SAC", estado, condicion, "150101", "AV 1"));
    }

    // --- 1083: el RUC no existe ---------------------------------------------------------------------------------------------------

    @Test void unRucQueElPadronNoTieneSeRechazaConElCodigoDeSunat() {
        assertThatThrownBy(() -> validador.exigirQueExista(COMPRADOR))
                .isInstanceOf(DomainException.class).hasMessageContaining("1083").hasMessageContaining(RUC)
                .extracting("codigo").isEqualTo("RECEPTOR_INVALIDO");
    }

    @Test void unRucQueElPadronTieneNoSeRechaza() {
        registrar("ACTIVO", "HABIDO");

        validador.exigirQueExista(COMPRADOR);
    }

    /** Dar de baja no borra el RUC de SUNAT: figura en el padrón y SUNAT solo lo observa (4013), no lo rechaza (1083). */
    @Test void unRucDeBajaOSinHabidoNoSeRechaza() {
        registrar("BAJA DEFINITIVA", "NO HABIDO");

        validador.exigirQueExista(COMPRADOR);
    }

    // --- 4013 y 4014: se acepta con observación --------------------------------------------------------------------------------

    @Test void unRucActivoYHabidoNoTieneAvisos() {
        registrar("ACTIVO", "HABIDO");

        assertThat(validador.avisosDe(COMPRADOR)).isEmpty();
    }

    @Test void unRucQueNoEstaActivoAvisaConElCodigo4013() {
        registrar("SUSPENSION TEMPORAL", "HABIDO");

        assertThat(validador.avisosDe(COMPRADOR)).singleElement().satisfies(a -> {
            assertThat(a.codigo()).isEqualTo("4013");
            assertThat(a.mensaje()).contains(RUC).contains("SUSPENSION TEMPORAL").contains("SUNAT aceptará");
        });
    }

    @Test void unRucNoHabidoAvisaConElCodigo4014() {
        registrar("ACTIVO", "NO HABIDO");

        assertThat(validador.avisosDe(COMPRADOR)).singleElement().satisfies(a -> {
            assertThat(a.codigo()).isEqualTo("4014");
            assertThat(a.mensaje()).contains(RUC).contains("no habido");
        });
    }

    /** SUNAT solo observa la condición 12 («NO HABIDO», desde el control de cambios del 24/06/2019): «NO HALLADO …», «PENDIENTE» o «POR VERIFICAR» no se observan. */
    @Test void soloNoHabidoEsLaCondicionQueSunatObserva() {
        for (String condicion : new String[]{"HABIDO", "NO HALLADO SE MUDO D", "NO HALLADO", "PENDIENTE", "POR VERIFICAR", "NO APLICABLE", "-"}) {
            padron.contribuyentes.clear();
            registrar("ACTIVO", condicion);
            assertThat(validador.avisosDe(COMPRADOR)).as("condición " + condicion).isEmpty();
        }
    }

    @Test void unRucDeBajaYNoHabidoAvisaLosDosCodigos() {
        registrar("BAJA DE OFICIO", "NO HABIDO");

        assertThat(validador.avisosDe(COMPRADOR)).extracting(Aviso::codigo).containsExactly("4013", "4014");
    }

    @Test void unRucQueElPadronNoTieneNoDaAvisos() {
        assertThat(validador.avisosDe(COMPRADOR)).as("ese caso lo rechaza exigirQueExista; avisar no lo repite").isEmpty();
    }

    // --- el padrón no siempre es confiable -------------------------------------------------------------------------------------

    /** Un RUC recién inscrito no está en un padrón viejo, y SUNAT sí lo conoce: sin un padrón reciente no se rechaza. */
    @Test void sinPadronCargadoNoSeRechazaNiSeAvisa() {
        padron.cargada = null;

        validador.exigirQueExista(COMPRADOR);
        assertThat(validador.avisosDe(COMPRADOR)).isEmpty();
        assertThat(padron.busquedas).as("ni siquiera lo busca").isZero();
    }

    @Test void conElPadronMasAtrasadoQueElTopeNoSeRechazaNiSeAvisa() {
        padron.cargada = AHORA.minus(TOPE).minusSeconds(1);
        registrar("BAJA DEFINITIVA", "NO HABIDO");

        validador.exigirQueExista(new Receptor("6", "20100070970", "OTRO SA", "AV 2"));
        assertThat(validador.avisosDe(COMPRADOR)).isEmpty();
        assertThat(padron.busquedas).isZero();
    }

    @Test void conElPadronJustoEnElTopeTodaviaSeRechaza() {
        padron.cargada = AHORA.minus(TOPE);

        assertThatThrownBy(() -> validador.exigirQueExista(COMPRADOR)).isInstanceOf(DomainException.class).hasMessageContaining("1083");
    }

    // --- a quién se aplica -------------------------------------------------------------------------------------------------------

    /** Un RUC 10 (persona natural) es un RUC como cualquier otro para SUNAT. */
    @Test void unRucDePersonaNaturalSeValidaIgual() {
        Receptor persona = new Receptor("6", "10456789012", "PEREZ JUAN", "AV 3");

        assertThatThrownBy(() -> validador.exigirQueExista(persona)).hasMessageContaining("1083");
    }

    /** Los avisos se piden con el comprobante ya emitido: si la base falla en ese momento, la emisión no se convierte en error (el cliente reintentaría y duplicaría la factura). */
    @Test void siElPadronFallaAlAvisarSeRespondeSinAvisos() {
        var caido = new pe.factura.application.port.out.PadronRucRepository() {
            public java.util.Optional<Contribuyente> buscar(String ruc) { throw new IllegalStateException("se cayó la base"); }
            public java.util.Optional<CargaRegistrada> ultimaCargaExitosa() { return java.util.Optional.of(new CargaRegistrada(AHORA, AHORA, true, "x", 1, 0, 0, 0, 0, null)); }
            public ResultadoCarga reemplazar(java.util.stream.Stream<Contribuyente> filas, long minimoDeFilas) { throw new UnsupportedOperationException(); }
            public void registrar(CargaRegistrada carga) { throw new UnsupportedOperationException(); }
        };

        assertThat(new ValidarCompradorEnPadron(caido, RELOJ, TOPE).avisosDe(COMPRADOR)).isEmpty();
    }

    @Test void sinRucNoSeConsultaElPadron() {
        Receptor[] otros = {
                new Receptor("1", "45678912", "PEREZ JUAN", null),
                new Receptor("-", "-", "CLIENTES VARIOS", null),
                new Receptor("4", "001234567", "EXTRANJERO", null),
                null};
        for (Receptor r : otros) {
            validador.exigirQueExista(r);
            assertThat(validador.avisosDe(r)).isEmpty();
        }
        assertThat(padron.busquedas).isZero();
    }
}
