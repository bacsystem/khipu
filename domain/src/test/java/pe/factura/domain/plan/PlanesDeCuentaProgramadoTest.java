package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * La bajada de plan no corta a nadie a mitad de ciclo (#191): queda programada para el inicio del ciclo siguiente y la cuenta sigue con el plan de hoy hasta entonces.
 * Siempre hay exactamente una suscripción activa; lo programado es aparte y nunca es una segunda.
 */
class PlanesDeCuentaProgramadoTest {
    static final Instant T0 = Instant.parse("2026-10-01T05:00:00Z");
    static final Instant AHORA = Instant.parse("2026-10-15T15:00:00Z");
    static final Instant PROXIMO_CICLO = Instant.parse("2026-11-01T05:00:00Z");
    static final UUID CUENTA = UUID.randomUUID();
    static final UUID NEGOCIO = UUID.randomUUID();
    static final UUID EMPRENDE = UUID.randomUUID();
    static final UUID PRO = UUID.randomUUID();

    static PlanesDeCuenta enNegocio() {
        return new PlanesDeCuenta(CUENTA, List.of(new Suscripcion(UUID.randomUUID(), CUENTA, NEGOCIO, T0, T0.plus(Duration.ofDays(60)), 5, null)));
    }

    static CambioDePlan aEmprende() { return new CambioDePlan(EMPRENDE, PROXIMO_CICLO, PROXIMO_CICLO.plus(Duration.ofDays(30)), 3); }

    static String codigo(Runnable r) { return catchThrowableOfType(DomainException.class, r::run).codigo(); }

    // --- CambioDePlan -----------------------------------------------------------------------------------------------------------------------

    @Test void unCambioDePlanNecesitaPlanYFecha() {
        assertThat(codigo(() -> new CambioDePlan(null, PROXIMO_CICLO, null, 0))).isEqualTo("CAMBIO_INVALIDO");
        assertThat(codigo(() -> new CambioDePlan(EMPRENDE, null, null, 0))).isEqualTo("CAMBIO_INVALIDO");
    }

    @Test void elVencimientoVaDespuesDeLaFechaDelCambioYLaGraciaNoEsNegativa() {
        assertThat(codigo(() -> new CambioDePlan(EMPRENDE, PROXIMO_CICLO, PROXIMO_CICLO, 0))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
        assertThat(codigo(() -> new CambioDePlan(EMPRENDE, PROXIMO_CICLO, PROXIMO_CICLO.minusSeconds(1), 0))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
        assertThat(codigo(() -> new CambioDePlan(EMPRENDE, PROXIMO_CICLO, null, -1))).isEqualTo("GRACIA_INVALIDA");
        assertThat(new CambioDePlan(EMPRENDE, PROXIMO_CICLO, null, 0).venceEn()).isNull();
    }

    // --- programar --------------------------------------------------------------------------------------------------------------------------

    @Test void unaCuentaNuevaNoTieneNadaProgramado() {
        assertThat(enNegocio().programado()).isNull();
    }

    @Test void programarDejaLaSuscripcionActivaIntactaYAnotaElCambio() {
        PlanesDeCuenta p = enNegocio();

        PlanesDeCuenta programada = p.programar(aEmprende());

        assertThat(programada.programado()).isEqualTo(aEmprende());
        assertThat(programada.activa()).isEqualTo(p.activa());
        assertThat(programada.suscripciones()).hasSize(1);
        assertThat(p.programado()).isNull();
    }

    @Test void unSegundoCambioProgramadoReemplazaAlPrimero() {
        PlanesDeCuenta p = enNegocio().programar(aEmprende());
        CambioDePlan aOtro = new CambioDePlan(UUID.randomUUID(), PROXIMO_CICLO, null, 0);

        assertThat(p.programar(aOtro).programado()).isEqualTo(aOtro);
    }

    @Test void noSeProgramaAntesDeQueEmpiezaLaSuscripcionActual() {
        assertThat(codigo(() -> enNegocio().programar(new CambioDePlan(EMPRENDE, T0.minusSeconds(1), null, 0)))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
    }

    @Test void cancelarLoProgramadoLoQuita() {
        assertThat(enNegocio().programar(aEmprende()).cancelarProgramado().programado()).isNull();
        assertThat(enNegocio().cancelarProgramado().programado()).isNull();
    }

    /** Un cambio inmediato (una subida, una renovación) deja sin efecto la bajada que estaba esperando. */
    @Test void cambiarDePlanAhoraCancelaLaBajadaProgramada() {
        PlanesDeCuenta p = enNegocio().programar(aEmprende());

        PlanesDeCuenta despues = p.cambiarA(UUID.randomUUID(), PRO, AHORA, null, 0);

        assertThat(despues.programado()).isNull();
        assertThat(despues.activa().planId()).isEqualTo(PRO);
    }

    // --- el plan vigente en un instante -----------------------------------------------------------------------------------------------------

    @Test void antesDelCicloSiguienteLaCuentaSigueConSuPlanDeHoy() {
        PlanesDeCuenta p = enNegocio().programar(aEmprende());

        assertThat(p.planVigenteEn(AHORA)).isEqualTo(NEGOCIO);
        assertThat(p.planVigenteEn(PROXIMO_CICLO.minusMillis(1))).isEqualTo(NEGOCIO);
    }

    /** En el instante exacto en que empieza el ciclo siguiente, ya manda el plan nuevo. */
    @Test void enElInstanteDelCicloSiguienteMandaElPlanNuevo() {
        PlanesDeCuenta p = enNegocio().programar(aEmprende());

        assertThat(p.planVigenteEn(PROXIMO_CICLO)).isEqualTo(EMPRENDE);
        assertThat(p.planVigenteEn(PROXIMO_CICLO.plusSeconds(86_400))).isEqualTo(EMPRENDE);
    }

    @Test void sinNadaProgramadoMandaLaSuscripcionActiva() {
        assertThat(enNegocio().planVigenteEn(PROXIMO_CICLO.plusSeconds(999_999))).isEqualTo(NEGOCIO);
    }

    // --- aplicar lo programado --------------------------------------------------------------------------------------------------------------

    @Test void aplicarAntesDeLaFechaNoCambiaNada() {
        PlanesDeCuenta p = enNegocio().programar(aEmprende());

        assertThat(p.aplicarProgramadoEn(PROXIMO_CICLO.minusMillis(1), UUID.randomUUID())).isEqualTo(p);
    }

    @Test void aplicarSinNadaProgramadoNoCambiaNada() {
        PlanesDeCuenta p = enNegocio();

        assertThat(p.aplicarProgramadoEn(PROXIMO_CICLO.plusSeconds(10), UUID.randomUUID())).isEqualTo(p);
    }

    /** Llegada la fecha, la activa termina y la nueva empieza **en la fecha programada** (no cuando alguien se acordó de aplicarla), con su vencimiento y su gracia. */
    @Test void aplicarLlegadaLaFechaCierraLaActivaYAbreLaNuevaDesdeLaFechaProgramada() {
        PlanesDeCuenta p = enNegocio().programar(aEmprende());
        UUID idNueva = UUID.randomUUID();

        PlanesDeCuenta aplicada = p.aplicarProgramadoEn(PROXIMO_CICLO.plusSeconds(3 * 3600), idNueva);

        assertThat(aplicada.programado()).isNull();
        assertThat(aplicada.suscripciones()).hasSize(2);
        assertThat(aplicada.suscripciones().get(0).terminaEn()).isEqualTo(PROXIMO_CICLO);
        Suscripcion nueva = aplicada.activa();
        assertThat(nueva.id()).isEqualTo(idNueva);
        assertThat(nueva.planId()).isEqualTo(EMPRENDE);
        assertThat(nueva.iniciaEn()).isEqualTo(PROXIMO_CICLO);
        assertThat(nueva.venceEn()).isEqualTo(PROXIMO_CICLO.plus(Duration.ofDays(30)));
        assertThat(nueva.diasDeGracia()).isEqualTo(3);
    }

    @Test void aplicarEnElInstanteExactoYaCuenta() {
        assertThat(enNegocio().programar(aEmprende()).aplicarProgramadoEn(PROXIMO_CICLO, UUID.randomUUID()).activa().planId()).isEqualTo(EMPRENDE);
    }

    @Test void despuesDeAplicarElPlanVigenteEsElNuevoSinNadaProgramado() {
        PlanesDeCuenta aplicada = enNegocio().programar(aEmprende()).aplicarProgramadoEn(PROXIMO_CICLO, UUID.randomUUID());

        assertThat(aplicada.planVigenteEn(PROXIMO_CICLO)).isEqualTo(EMPRENDE);
        assertThat(aplicada.programado()).isNull();
    }
}
