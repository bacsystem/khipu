package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.VigilarOperacionUseCase.Resultado;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.AvisosRepository;
import pe.factura.application.port.out.CanalDeAlertas;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.MonitorDeEmisionRepository;
import pe.factura.application.port.out.VigiladoRepository;
import pe.factura.application.port.out.VigiladoRepository.EstadoAlerta;
import pe.factura.application.service.VigilarOperacionService.Umbrales;
import pe.factura.domain.plataforma.MotivoDeAviso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El vigilante de la operación (#341): cada condición avisa una vez al aparecer, se repite pasado el intervalo, avisa al resolverse, no se registra si ningún canal entregó
 * y no se repite en cada pasada.
 */
class VigilarOperacionServiceTest {
    static final Instant AHORA = Instant.parse("2026-10-18T15:00:00Z");
    static final Umbrales UMBRALES = new Umbrales(Duration.ofHours(24), Duration.ofMinutes(30), 5, Duration.ofHours(6), Duration.ZERO, Duration.ofHours(24));

    static class Monitores implements MonitorDeEmisionRepository {
        Cola cola = new Cola(0, 0, null, null);
        ResumenesPendientes resumenes = new ResumenesPendientes(0, null);
        BoletasPendientes boletas = new BoletasPendientes(0, 0, null);
        public List<Conteo> porHoraYEstado(Instant desde) { return List.of(); }
        public Cola cola(Instant ahora) { return cola; }
        public ResumenesPendientes resumenesSinRespuesta(Instant enviadosAntesDe) { return resumenes; }
        public BoletasPendientes boletasSinInformar(LocalDate hoy) { return boletas; }
    }

    static class Avisos implements AvisosRepository {
        long certificados, credenciales;
        public List<FilaCertificado> certificados(LocalDate hoy, int pagina, int porPagina) { throw new UnsupportedOperationException(); }
        public long contarCertificados(LocalDate hoy) { return certificados; }
        public List<FilaSol> credencialesSol(int pagina, int porPagina) { throw new UnsupportedOperationException(); }
        public long contarCredencialesSol() { return credenciales; }
        public List<FilaBoletas> boletasSinInformar(LocalDate hoy) { throw new UnsupportedOperationException(); }
        public Optional<Situacion> situacionDe(UUID empresaId, LocalDate hoy) { throw new UnsupportedOperationException(); }
        public Map<UUID, AvisoRegistrado> ultimosAvisos(Collection<UUID> empresas, MotivoDeAviso motivo) { throw new UnsupportedOperationException(); }
        public Optional<AvisoRegistrado> reservar(AvisoRegistrado aviso, Instant desde) { throw new UnsupportedOperationException(); }
        public void anular(UUID avisoId) { throw new UnsupportedOperationException(); }
    }

    static class Vigilado implements VigiladoRepository {
        long enError, fueraDePlazo;
        Instant antesDeError, desdeFueraDePlazo;
        Optional<Instant> latido = Optional.of(AHORA);
        final Map<String, EstadoAlerta> alertas = new HashMap<>();
        public long enErrorDeEnvioDesde(Instant antesDe) { antesDeError = antesDe; return enError; }
        public long fueraDePlazoDesde(Instant desde) { desdeFueraDePlazo = desde; return fueraDePlazo; }
        public Optional<Instant> ultimoLatido(String clave) { return latido; }
        public Map<String, EstadoAlerta> alertas() { return Map.copyOf(alertas); }
        public void guardarAlerta(String clave, EstadoAlerta estado) { alertas.put(clave, estado); }
    }

    static class Correo implements CorreoSender {
        boolean entrega = true;
        public void enviar(String para, String asunto, String cuerpoTexto) {}
        public void enviar(String para, String asunto, String cuerpoTexto, List<Adjunto> adjuntos) {}
        @Override public boolean entregaDeVerdad() { return entrega; }
    }

    static class Canal implements CanalDeAlertas {
        final List<String> asuntos = new ArrayList<>();
        final List<String> cuerpos = new ArrayList<>();
        boolean configurado = true, falla;
        public String nombre() { return "prueba"; }
        public boolean configurado() { return configurado; }
        public void enviar(String asunto, String cuerpo) {
            if (falla) throw new IllegalStateException("sin red");
            asuntos.add(asunto);
            cuerpos.add(cuerpo);
        }
    }

    Monitores monitor = new Monitores();
    Avisos avisos = new Avisos();
    Vigilado vigilado = new Vigilado();
    Correo correo = new Correo();
    Canal canal = new Canal();
    Instant reloj = AHORA;

    Resultado pasada() { return pasada(UMBRALES); }

    Resultado pasada(Umbrales umbrales) {
        return new VigilarOperacionService(monitor, avisos, vigilado, correo, List.of(canal), Clock.fixed(reloj, ZoneOffset.UTC), umbrales).vigilar();
    }

    void outboxAtascadoDesde(Instant desde) { monitor.cola = new MonitorDeEmisionRepository.Cola(12, 12, desde.minusSeconds(60), desde); }

    @Test void sinProblemasNoAvisaNiRegistraNada() {
        Resultado r = pasada();

        assertThat(r).isEqualTo(new Resultado(0, 0, List.of()));
        assertThat(canal.asuntos).isEmpty();
        assertThat(vigilado.alertas).isEmpty();
    }

    @Test void unOutboxAtascadoAvisaUnaVezYNoEnCadaPasada() {
        outboxAtascadoDesde(AHORA.minus(Duration.ofMinutes(20)));

        assertThat(pasada().avisadas()).isEqualTo(1);
        assertThat(canal.asuntos).singleElement().asString().startsWith("ALERTA").contains("outbox");
        assertThat(canal.cuerpos.get(0)).contains("12 envío(s) vencido(s)").contains("20 min");

        reloj = AHORA.plus(Duration.ofMinutes(5));
        assertThat(pasada().avisadas()).isZero();
        reloj = AHORA.plus(Duration.ofHours(23));
        assertThat(pasada().avisadas()).isZero();
        assertThat(canal.asuntos).hasSize(1);
    }

    @Test void unOutboxVencidoHaceMenosDelUmbralTodaviaNoAlerta() {
        outboxAtascadoDesde(AHORA.minus(Duration.ofMinutes(4)));

        assertThat(pasada().avisadas()).isZero();
        assertThat(canal.asuntos).isEmpty();
    }

    @Test void siElProblemaSigueSeRepiteCadaIntervaloDiciendoDesdeCuando() {
        outboxAtascadoDesde(AHORA.minus(Duration.ofMinutes(20)));
        pasada();

        reloj = AHORA.plus(Duration.ofHours(24));
        outboxAtascadoDesde(reloj.minus(Duration.ofHours(24)));
        assertThat(pasada().avisadas()).isEqualTo(1);

        assertThat(canal.asuntos).hasSize(2);
        assertThat(canal.cuerpos.get(1)).contains("Sigue sin resolverse desde " + AHORA);
        assertThat(vigilado.alertas.get("OUTBOX_ATASCADO")).isEqualTo(new EstadoAlerta(true, AHORA, reloj));
    }

    @Test void alResolverseAvisaUnaVezYSeDesactiva() {
        outboxAtascadoDesde(AHORA.minus(Duration.ofMinutes(20)));
        pasada();

        monitor.cola = new MonitorDeEmisionRepository.Cola(0, 0, null, null);
        reloj = AHORA.plus(Duration.ofMinutes(10));
        Resultado r = pasada();

        assertThat(r).isEqualTo(new Resultado(0, 1, List.of()));
        assertThat(canal.asuntos.get(1)).startsWith("RESUELTO").contains("outbox");
        assertThat(vigilado.alertas.get("OUTBOX_ATASCADO").activa()).isFalse();

        reloj = AHORA.plus(Duration.ofMinutes(20));
        assertThat(pasada()).isEqualTo(new Resultado(0, 0, List.of()));
        assertThat(canal.asuntos).hasSize(2);
    }

    @Test void siElProblemaVuelveDespuesDeResueltoSeAvisaDeNuevoDesdeCero() {
        outboxAtascadoDesde(AHORA.minus(Duration.ofMinutes(20)));
        pasada();
        monitor.cola = new MonitorDeEmisionRepository.Cola(0, 0, null, null);
        reloj = AHORA.plus(Duration.ofMinutes(10));
        pasada();

        reloj = AHORA.plus(Duration.ofMinutes(30));
        outboxAtascadoDesde(reloj.minus(Duration.ofMinutes(10)));
        assertThat(pasada().avisadas()).isEqualTo(1);

        assertThat(canal.asuntos.get(2)).startsWith("ALERTA");
        assertThat(canal.cuerpos.get(2)).doesNotContain("Sigue sin resolverse");
        assertThat(vigilado.alertas.get("OUTBOX_ATASCADO").desde()).isEqualTo(reloj);
    }

    @Test void siNingunCanalEntregoLaAlertaNoSeRegistraYSeReintenta() {
        outboxAtascadoDesde(AHORA.minus(Duration.ofMinutes(20)));
        canal.falla = true;

        Resultado r = pasada();

        assertThat(r).isEqualTo(new Resultado(0, 0, List.of("OUTBOX_ATASCADO")));
        assertThat(vigilado.alertas).isEmpty();

        canal.falla = false;
        reloj = AHORA.plus(Duration.ofMinutes(5));
        assertThat(pasada().avisadas()).isEqualTo(1);
        assertThat(canal.asuntos).hasSize(1);
    }

    @Test void sinCanalConfiguradoQuedaEnElLogYSeRegistraParaNoRepetirse() {
        outboxAtascadoDesde(AHORA.minus(Duration.ofMinutes(20)));
        canal.configurado = false;

        assertThat(pasada().avisadas()).isEqualTo(1);
        assertThat(canal.asuntos).isEmpty();
        assertThat(vigilado.alertas.get("OUTBOX_ATASCADO").activa()).isTrue();

        reloj = AHORA.plus(Duration.ofMinutes(5));
        assertThat(pasada().avisadas()).isZero();
    }

    @Test void losEnviosConErrorAlertanDesdeElUmbralDeCantidadYMiranElTiempoConfigurado() {
        vigilado.enError = 4;
        assertThat(pasada().avisadas()).isZero();
        assertThat(vigilado.antesDeError).isEqualTo(AHORA.minus(Duration.ofMinutes(30)));

        vigilado.enError = 5;
        assertThat(pasada().avisadas()).isEqualTo(1);
        assertThat(canal.cuerpos.get(0)).contains("5 comprobante(s)").contains("30 min").contains("ERROR_ENVIO");
    }

    @Test void cualquierComprobanteFueraDePlazoAlertaYSeCuentaEnLaVentana() {
        vigilado.fueraDePlazo = 1;

        assertThat(pasada().avisadas()).isEqualTo(1);
        assertThat(vigilado.desdeFueraDePlazo).isEqualTo(AHORA.minus(Duration.ofHours(24)));
        assertThat(canal.asuntos.get(0)).contains("FUERA_DE_PLAZO");
    }

    @Test void losResumenesSinRespuestaAlertan() {
        monitor.resumenes = new MonitorDeEmisionRepository.ResumenesPendientes(2, AHORA.minus(Duration.ofHours(9)));

        assertThat(pasada().avisadas()).isEqualTo(1);
        assertThat(canal.cuerpos.get(0)).contains("2 resumen(es)").contains("6 h");
    }

    @Test void lasBoletasAlQuintoDiaNoAlertanAlOperadorPeroLasDelSeptimoSi() {
        monitor.boletas = new MonitorDeEmisionRepository.BoletasPendientes(3, 0, LocalDate.of(2026, 10, 13));
        assertThat(pasada().avisadas()).isZero();

        monitor.boletas = new MonitorDeEmisionRepository.BoletasPendientes(3, 1, LocalDate.of(2026, 10, 11));
        assertThat(pasada().avisadas()).isEqualTo(1);
        assertThat(canal.cuerpos.get(0)).contains("1 boleta(s)").contains("2026-10-11");
    }

    @Test void elRespaldoApagadoConCeroNoAlertaAunqueNoHayaLatido() {
        vigilado.latido = Optional.empty();

        assertThat(pasada().avisadas()).isZero();
    }

    @Test void elRespaldoAtrasadoOSinLatidoAlerta() {
        Umbrales conRespaldo = new Umbrales(UMBRALES.repetirCada(), UMBRALES.errorDeEnvioDesde(), UMBRALES.errorDeEnvioCantidad(), UMBRALES.resumenesSinRespuesta(), Duration.ofHours(26), UMBRALES.fueraDePlazoVentana());
        vigilado.latido = Optional.of(AHORA.minus(Duration.ofHours(25)));
        assertThat(pasada(conRespaldo).avisadas()).isZero();

        vigilado.latido = Optional.of(AHORA.minus(Duration.ofHours(27)));
        assertThat(pasada(conRespaldo).avisadas()).isEqualTo(1);
        assertThat(canal.cuerpos.get(0)).contains("26 h").contains("deploy/respaldo");

        vigilado.latido = Optional.empty();
        reloj = AHORA.plus(Duration.ofHours(25));
        assertThat(pasada(conRespaldo).avisadas()).isEqualTo(1);
        assertThat(canal.cuerpos.get(1)).contains("Todavía no hay ningún respaldo");
    }

    @Test void elCorreoQueNoEntregaAlerta() {
        correo.entrega = false;

        assertThat(pasada().avisadas()).isEqualTo(1);
        assertThat(canal.asuntos.get(0)).contains("correo");
    }

    @Test void losCertificadosEnRiesgoYLasCredencialesSolAlertan() {
        avisos.certificados = 3;
        avisos.credenciales = 2;

        assertThat(pasada().avisadas()).isEqualTo(2);
        assertThat(canal.cuerpos).anyMatch(c -> c.contains("3 empresa(s) tienen el certificado"));
        assertThat(canal.cuerpos).anyMatch(c -> c.contains("2 empresa(s) tienen envíos fallando"));
    }
}
