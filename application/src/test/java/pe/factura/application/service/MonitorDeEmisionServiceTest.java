package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.MonitorearEmisionUseCase.Franja;
import pe.factura.application.port.in.MonitorearEmisionUseCase.Monitor;
import pe.factura.application.port.in.MonitorearEmisionUseCase.ResumenesSinRespuesta;
import pe.factura.application.port.out.MonitorDeEmisionRepository;
import pe.factura.application.port.out.MonitorDeEmisionRepository.Cola;
import pe.factura.application.port.out.MonitorDeEmisionRepository.Conteo;
import pe.factura.application.port.out.MonitorDeEmisionRepository.ResumenesPendientes;
import pe.factura.application.port.out.SondeoDeSunat;
import pe.factura.application.port.out.SondeoDeSunat.Resultado;
import pe.factura.application.port.out.SondeoDeSunat.Servicio;
import pe.factura.domain.documento.EstadoDocumento;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.domain.documento.EstadoDocumento.*;

/**
 * El monitor global de emisión (#195): la serie de 24 horas sin huecos, qué estados cuentan en cada categoría (con las reglas de {@link EstadoDocumento}), el día de Lima
 * y cuándo se da la alerta del outbox.
 */
class MonitorDeEmisionServiceTest {
    /** 2026-10-15 15:20 UTC = 10:20 en Lima. */
    static final Instant AHORA = Instant.parse("2026-10-15T15:20:00Z");
    static final Instant HORA_ACTUAL = Instant.parse("2026-10-15T15:00:00Z");
    static final Instant MEDIANOCHE_LIMA = Instant.parse("2026-10-15T05:00:00Z");

    /** El umbral con que se arma el servicio (en producción sale de `app.monitor.resumen-sin-respuesta-horas`). */
    static final Duration UMBRAL_DE_RESUMENES = Duration.ofHours(6);

    static class Monitores implements MonitorDeEmisionRepository {
        final List<Conteo> conteos = new ArrayList<>();
        final List<Instant> desdes = new ArrayList<>();
        final List<Instant> ahoras = new ArrayList<>();
        final List<Instant> limites = new ArrayList<>();
        Cola cola = new Cola(0, 0, null, null);
        ResumenesPendientes resumenes = new ResumenesPendientes(0, null);
        BoletasPendientes boletas = new BoletasPendientes(0, 0, null);
        final List<java.time.LocalDate> hoys = new ArrayList<>();
        public List<Conteo> porHoraYEstado(Instant desde) { desdes.add(desde); return conteos; }
        public Cola cola(Instant ahora) { ahoras.add(ahora); return cola; }
        public ResumenesPendientes resumenesSinRespuesta(Instant creadosAntesDe) { limites.add(creadosAntesDe); return resumenes; }
        public BoletasPendientes boletasSinInformar(java.time.LocalDate hoy) { hoys.add(hoy); return boletas; }
    }

    static class Sondeo implements SondeoDeSunat {
        int llamadas;
        List<Resultado> resultados = List.of();
        public List<Resultado> sondear() { llamadas++; return resultados; }
    }

    Monitores repo = new Monitores();
    Sondeo sondeo = new Sondeo();
    MonitorDeEmisionService service = new MonitorDeEmisionService(repo, sondeo, Clock.fixed(AHORA, ZoneOffset.UTC), UMBRAL_DE_RESUMENES);

    MonitorDeEmisionServiceTest en(Instant hora, EstadoDocumento e, long n) { repo.conteos.add(new Conteo(hora, e, n)); return this; }

    Instant hace(int horas) { return HORA_ACTUAL.minus(Duration.ofHours(horas)); }

    // --- la serie de horas ------------------------------------------------------------------------------------------------------------------

    @Test void laSerieSiempreTiene24HorasContiguasDeLaMasVieja_aLaActual() {
        Monitor m = service.monitorear();

        assertThat(m.horas()).hasSize(24);
        assertThat(m.horas().get(0).desde()).isEqualTo(hace(23));
        assertThat(m.horas().get(23).desde()).isEqualTo(HORA_ACTUAL);
        for (int i = 1; i < 24; i++) assertThat(m.horas().get(i).desde()).isEqualTo(m.horas().get(i - 1).desde().plus(Duration.ofHours(1)));
    }

    @Test void lasHorasSinComprobantesVanEnCeroEnLugarDeFaltar() {
        en(hace(3), ACEPTADO, 4);

        Monitor m = service.monitorear();

        assertThat(m.horas().get(20).aceptados()).isEqualTo(4);
        assertThat(m.horas()).filteredOn(f -> !f.desde().equals(hace(3))).allMatch(f -> f.total() == 0);
    }

    @Test void consultaElRepositorioDesdeElInicioDeLaHoraDe23HorasAtras() {
        service.monitorear();

        assertThat(repo.desdes).containsExactly(hace(23));
    }

    @Test void generadoEnEsElInstanteDeLaLectura_yLaColaSeConsultaConEseInstante() {
        Monitor m = service.monitorear();

        assertThat(m.generadoEn()).isEqualTo(AHORA);
        assertThat(repo.ahoras).containsExactly(AHORA);
    }

    @Test void unConteoFueraDeLaVentanaSeIgnoraEnLugarDeRomperLaSerie() {
        en(hace(24), ACEPTADO, 9);
        en(HORA_ACTUAL.plus(Duration.ofHours(1)), ACEPTADO, 9);

        Monitor m = service.monitorear();

        assertThat(m.horas()).allMatch(f -> f.total() == 0);
        assertThat(m.hoy().total()).as("lo que todavía no ocurrió tampoco entra en el día").isZero();
    }

    // --- las categorías ---------------------------------------------------------------------------------------------------------------------

    @Test void cadaEstadoCaeEnUnaSolaCategoriaYNingunoSePierde() {
        for (EstadoDocumento e : EstadoDocumento.values()) en(HORA_ACTUAL, e, 1);

        Franja f = service.monitorear().horas().get(23);

        assertThat(f.total()).isEqualTo(EstadoDocumento.values().length);
    }

    @Test void lasCategoriasSiguenLasReglasDelEnum() {
        en(HORA_ACTUAL, ACEPTADO, 1);
        en(HORA_ACTUAL, ACEPTADO_CON_OBS, 2);
        en(HORA_ACTUAL, RECHAZADO, 4);
        en(HORA_ACTUAL, ERROR_ENVIO, 8);
        en(HORA_ACTUAL, FUERA_DE_PLAZO, 16);
        en(HORA_ACTUAL, FIRMADO, 32);
        en(HORA_ACTUAL, ENVIADO, 64);
        en(HORA_ACTUAL, PENDIENTE_AGRUPACION, 128);
        en(HORA_ACTUAL, RECIBIDO, 256);
        en(HORA_ACTUAL, INVALIDO, 512);
        en(HORA_ACTUAL, ANULADO, 1024);

        Franja f = service.monitorear().horas().get(23);

        assertThat(f.aceptados()).as("aceptado y aceptado con observaciones").isEqualTo(3);
        assertThat(f.rechazados()).isEqualTo(4);
        assertThat(f.conError()).as("error de envío y fuera de plazo").isEqualTo(24);
        assertThat(f.enCamino()).as("firmado, enviado y pendiente de agrupación").isEqualTo(224);
        assertThat(f.otros()).as("recibido, inválido y anulado").isEqualTo(1792);
    }

    @Test void unaMismaHoraSumaLosConteosDeSusEstados() {
        en(hace(1), ACEPTADO, 3);
        en(hace(1), ACEPTADO_CON_OBS, 2);

        assertThat(service.monitorear().horas().get(22).aceptados()).isEqualTo(5);
    }

    // --- la tasa de rechazo -----------------------------------------------------------------------------------------------------------------

    @Test void laTasaDeRechazoEsRechazadosSobreLosQueSunatResolvio() {
        en(HORA_ACTUAL, ACEPTADO, 6);
        en(HORA_ACTUAL, ACEPTADO_CON_OBS, 2);
        en(HORA_ACTUAL, RECHAZADO, 2);
        en(HORA_ACTUAL, ERROR_ENVIO, 50);
        en(HORA_ACTUAL, ENVIADO, 50);

        assertThat(service.monitorear().horas().get(23).tasaDeRechazo()).isEqualTo(0.2);
    }

    @Test void sinResueltosLaTasaEsNulaYNoCero() {
        en(HORA_ACTUAL, ENVIADO, 5);

        assertThat(service.monitorear().horas().get(23).tasaDeRechazo()).isNull();
        assertThat(service.monitorear().hoy().tasaDeRechazo()).isNull();
    }

    @Test void todoRechazadoEsTasaUno() {
        en(HORA_ACTUAL, RECHAZADO, 3);

        assertThat(service.monitorear().hoy().tasaDeRechazo()).isEqualTo(1.0);
    }

    // --- el día de Lima ---------------------------------------------------------------------------------------------------------------------

    @Test void elDiaEmpiezaALaMedianocheDeLimaYSumaLasHorasDesdeEntonces() {
        en(MEDIANOCHE_LIMA.minus(Duration.ofHours(1)), ACEPTADO, 100);
        en(MEDIANOCHE_LIMA, ACEPTADO, 10);
        en(MEDIANOCHE_LIMA.plus(Duration.ofHours(4)), RECHAZADO, 5);
        en(HORA_ACTUAL, ENVIADO, 1);

        Franja hoy = service.monitorear().hoy();

        assertThat(hoy.desde()).isEqualTo(MEDIANOCHE_LIMA);
        assertThat(hoy.aceptados()).as("lo de antes de la medianoche de Lima es de ayer").isEqualTo(10);
        assertThat(hoy.rechazados()).isEqualTo(5);
        assertThat(hoy.enCamino()).isEqualTo(1);
        assertThat(hoy.total()).isEqualTo(16);
    }

    @Test void justoDespuesDeLaMedianocheDeLimaElDiaYaEsElNuevo() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-16T05:00:30Z"), ZoneOffset.UTC);
        MonitorDeEmisionService s = new MonitorDeEmisionService(repo, sondeo, reloj, UMBRAL_DE_RESUMENES);
        repo.conteos.add(new Conteo(Instant.parse("2026-10-16T04:00:00Z"), ACEPTADO, 7));
        repo.conteos.add(new Conteo(Instant.parse("2026-10-16T05:00:00Z"), ACEPTADO, 2));

        assertThat(s.monitorear().hoy().desde()).isEqualTo(Instant.parse("2026-10-16T05:00:00Z"));
        assertThat(s.monitorear().hoy().aceptados()).isEqualTo(2);
    }

    @Test void justoAntesDeLaMedianocheDeLimaElDiaSigueSiendoElAnterior() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-16T04:59:59Z"), ZoneOffset.UTC);
        MonitorDeEmisionService s = new MonitorDeEmisionService(repo, sondeo, reloj, UMBRAL_DE_RESUMENES);
        repo.conteos.add(new Conteo(Instant.parse("2026-10-15T05:00:00Z"), ACEPTADO, 7));

        Monitor m = s.monitorear();

        assertThat(m.hoy().desde()).isEqualTo(Instant.parse("2026-10-15T05:00:00Z"));
        assertThat(m.hoy().aceptados()).as("las 24 horas cubren todo el día de Lima").isEqualTo(7);
    }

    // --- las boletas que nadie informó (#356) -----------------------------------------------------------------------------------------------

    @Test void sinBoletasPorInformarNoHayAlerta() {
        var b = service.monitorear().boletas();

        assertThat(b.cantidad()).isZero();
        assertThat(b.porVencerElPlazo()).isZero();
        assertThat(b.masAntiguaEmitidaEl()).isNull();
        assertThat(b.alerta()).isFalse();
    }

    /** Una sola boleta ya en el 5.º día alcanza: nadie la ha informado y quedan tres días. El monitor dice cuántas están ya en el último día del resumen o lo pasaron. */
    @Test void unaBoletaSinInformarDaAlertaYDiceCuantasYaVencenElPlazo() {
        repo.boletas = new MonitorDeEmisionRepository.BoletasPendientes(5, 2, java.time.LocalDate.of(2026, 10, 8));

        var b = service.monitorear().boletas();

        assertThat(b.cantidad()).isEqualTo(5);
        assertThat(b.porVencerElPlazo()).isEqualTo(2);
        assertThat(b.masAntiguaEmitidaEl()).isEqualTo(java.time.LocalDate.of(2026, 10, 8));
        assertThat(b.alerta()).isTrue();
    }

    /** La fecha de hoy es la del reloj del servicio (en develop se puede desplazar): de ella depende qué boleta ya pide atención. */
    @Test void elRepositorioRecibeLaFechaDeHoyDelReloj() {
        service.monitorear();

        assertThat(repo.hoys).containsExactly(java.time.LocalDate.of(2026, 10, 15));
    }

    // --- el outbox --------------------------------------------------------------------------------------------------------------------------

    @Test void unaColaVaciaNoDaAlerta() {
        assertThat(service.monitorear().outbox()).isEqualTo(new pe.factura.application.port.in.MonitorearEmisionUseCase.Outbox(0, 0, null, null, false));
    }

    @Test void laColaPasaLosNumerosYElMasViejoTalCual() {
        Instant viejo = AHORA.minus(Duration.ofHours(3));
        repo.cola = new Cola(12, 0, viejo, null);

        var o = service.monitorear().outbox();

        assertThat(o.pendientes()).isEqualTo(12);
        assertThat(o.vencidos()).isZero();
        assertThat(o.masViejoDesde()).isEqualTo(viejo);
        assertThat(o.vencidoHace()).isNull();
        assertThat(o.alerta()).isFalse();
    }

    @Test void unVencidoRecienteNoDaAlerta() {
        repo.cola = new Cola(3, 1, AHORA.minusSeconds(900), AHORA.minusSeconds(60));

        var o = service.monitorear().outbox();

        assertThat(o.vencidoHace()).isEqualTo(Duration.ofSeconds(60));
        assertThat(o.alerta()).isFalse();
    }

    @Test void justoEnElUmbralDeCincoMinutosTodaviaNoDaAlerta() {
        repo.cola = new Cola(1, 1, AHORA.minusSeconds(900), AHORA.minusSeconds(300));

        assertThat(service.monitorear().outbox().alerta()).isFalse();
    }

    @Test void pasadoElUmbralDeCincoMinutosDaAlerta() {
        repo.cola = new Cola(1, 1, AHORA.minusSeconds(900), AHORA.minusSeconds(301));

        var o = service.monitorear().outbox();

        assertThat(o.alerta()).isTrue();
        assertThat(o.vencidoHace()).isEqualTo(Duration.ofSeconds(301));
    }

    @Test void unaColaLargaPeroSinVencidosNoDaAlertaAunqueElMasViejoSeaAntiguo() {
        repo.cola = new Cola(500, 0, AHORA.minus(Duration.ofDays(2)), null);

        assertThat(service.monitorear().outbox().alerta()).as("esperar un reintento programado no es un trabajo caído").isFalse();
    }

    @Test void unaHoraDeRelojAtrasadaNoDaUnaDuracionNegativa() {
        repo.cola = new Cola(1, 1, AHORA, AHORA.plusSeconds(10));

        var o = service.monitorear().outbox();

        assertThat(o.vencidoHace()).isEqualTo(Duration.ZERO);
        assertThat(o.alerta()).isFalse();
    }

    // --- los resúmenes con ticket sin respuesta (#362 B14) ----------------------------------------------------------------------------------

    @Test void sinResumenesPendientesNoHayAlerta() {
        var r = service.monitorear().resumenes();

        assertThat(r).isEqualTo(new ResumenesSinRespuesta(0, null, null, UMBRAL_DE_RESUMENES, false));
    }

    /** El repositorio recibe «creados antes de» = ahora − umbral: los más nuevos no son un problema todavía. */
    @Test void preguntaPorLosResumenesCreadosAntesDeAhoraMenosElUmbral() {
        service.monitorear();

        assertThat(repo.limites).containsExactly(AHORA.minus(UMBRAL_DE_RESUMENES));
    }

    @Test void conResumenesPasadosDelUmbralDaAlertaConCuantosYDesdeCuando() {
        Instant viejo = AHORA.minus(Duration.ofHours(9));
        repo.resumenes = new ResumenesPendientes(2, viejo);

        var r = service.monitorear().resumenes();

        assertThat(r.cantidad()).isEqualTo(2);
        assertThat(r.masViejoDesde()).isEqualTo(viejo);
        assertThat(r.masViejoHace()).isEqualTo(Duration.ofHours(9));
        assertThat(r.umbral()).isEqualTo(UMBRAL_DE_RESUMENES);
        assertThat(r.alerta()).isTrue();
    }

    @Test void unRelojAtrasadoNoDaUnaDuracionNegativa() {
        repo.resumenes = new ResumenesPendientes(1, AHORA.plusSeconds(30));

        var r = service.monitorear().resumenes();

        assertThat(r.masViejoHace()).isEqualTo(Duration.ZERO);
        assertThat(r.alerta()).isTrue();
    }

    @Test void laAlertaDeLosResumenesNoTocaLaDelOutbox() {
        repo.resumenes = new ResumenesPendientes(1, AHORA.minus(Duration.ofHours(7)));

        var m = service.monitorear();

        assertThat(m.resumenes().alerta()).isTrue();
        assertThat(m.outbox().alerta()).as("el trabajo del outbox está bien: es SUNAT la que no responde el ticket").isFalse();
    }

    // --- SUNAT ------------------------------------------------------------------------------------------------------------------------------

    @Test void elEstadoDeSunatSePasaTalCualDelSondeo() {
        sondeo.resultados = List.of(new Resultado(Servicio.ENVIO_PRODUCCION, true, 120L, null), new Resultado(Servicio.ENVIO_BETA, false, null, "Sin conexión"));

        assertThat(service.monitorear().sunat()).isSameAs(sondeo.resultados);
        assertThat(sondeo.llamadas).isEqualTo(1);
    }
}
