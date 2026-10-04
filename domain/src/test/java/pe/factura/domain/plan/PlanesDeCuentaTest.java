package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Las dos reglas del issue (#189): una cuenta nunca se queda sin plan y nunca tiene dos suscripciones activas. Viven en el agregado, así que no hay manera de
 * construir —ni de llegar por un cambio— a un estado que las rompa.
 */
class PlanesDeCuentaTest {
    static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
    static final UUID CUENTA = UUID.randomUUID();
    static final UUID GRATIS = UUID.randomUUID();
    static final UUID NEGOCIO = UUID.randomUUID();

    static Suscripcion activa(UUID plan, Instant inicia) { return new Suscripcion(UUID.randomUUID(), CUENTA, plan, inicia, null, 0, null); }

    static Suscripcion cerrada(UUID plan, Instant inicia, Instant termina) { return new Suscripcion(UUID.randomUUID(), CUENTA, plan, inicia, null, 0, termina); }

    static String codigo(Runnable r) { return catchThrowableOfType(DomainException.class, r::run).codigo(); }

    @Test void unaCuentaConUnaActivaEsValida() {
        Suscripcion s = activa(GRATIS, T0);

        PlanesDeCuenta p = new PlanesDeCuenta(CUENTA, List.of(s));

        assertThat(p.activa()).isEqualTo(s);
    }

    @Test void sinSuscripcionesLaCuentaQuedariaSinPlan() {
        assertThat(codigo(() -> new PlanesDeCuenta(CUENTA, List.of()))).isEqualTo("CUENTA_SIN_PLAN");
        assertThat(codigo(() -> new PlanesDeCuenta(CUENTA, null))).isEqualTo("CUENTA_SIN_PLAN");
    }

    @Test void conTodasTerminadasTampocoTienePlan() {
        assertThat(codigo(() -> new PlanesDeCuenta(CUENTA, List.of(cerrada(GRATIS, T0, T0.plusSeconds(10)))))).isEqualTo("CUENTA_SIN_PLAN");
    }

    @Test void dosActivasNoSePermiten() {
        assertThat(codigo(() -> new PlanesDeCuenta(CUENTA, List.of(activa(GRATIS, T0), activa(NEGOCIO, T0.plusSeconds(5))))))
                .isEqualTo("SUSCRIPCIONES_ACTIVAS_MULTIPLES");
    }

    @Test void unaSuscripcionDeOtraCuentaNoCabe() {
        Suscripcion ajena = new Suscripcion(UUID.randomUUID(), UUID.randomUUID(), GRATIS, T0, null, 0, null);

        assertThat(codigo(() -> new PlanesDeCuenta(CUENTA, List.of(ajena)))).isEqualTo("SUSCRIPCION_AJENA");
    }

    @Test void conHistorialLaActivaEsLaUnicaSinTerminar() {
        Suscripcion vieja = cerrada(GRATIS, T0, T0.plusSeconds(100));
        Suscripcion nueva = activa(NEGOCIO, T0.plusSeconds(100));

        PlanesDeCuenta p = new PlanesDeCuenta(CUENTA, List.of(vieja, nueva));

        assertThat(p.activa()).isEqualTo(nueva);
        assertThat(p.suscripciones()).containsExactly(vieja, nueva);
    }

    @Test void cambiarDePlanCierraLaActivaYAbreLaNuevaEnElMismoInstante() {
        Suscripcion gratis = activa(GRATIS, T0);
        PlanesDeCuenta p = new PlanesDeCuenta(CUENTA, List.of(gratis));
        UUID idNueva = UUID.randomUUID();

        PlanesDeCuenta despues = p.cambiarA(idNueva, NEGOCIO, T0.plusSeconds(500), T0.plusSeconds(500 + 30 * 86400L), 5);

        assertThat(despues.activa().id()).isEqualTo(idNueva);
        assertThat(despues.activa().planId()).isEqualTo(NEGOCIO);
        assertThat(despues.activa().iniciaEn()).isEqualTo(T0.plusSeconds(500));
        assertThat(despues.activa().diasDeGracia()).isEqualTo(5);
        assertThat(despues.suscripciones()).hasSize(2);
        Suscripcion cerrada = despues.suscripciones().get(0);
        assertThat(cerrada.id()).isEqualTo(gratis.id());
        assertThat(cerrada.terminaEn()).isEqualTo(T0.plusSeconds(500));
    }

    /** El cambio devuelve otro agregado: el original sigue con su plan, y el resultado siempre tiene exactamente una activa. */
    @Test void cambiarNoMutaElOriginalYSiempreDejaUnaActiva() {
        PlanesDeCuenta p = new PlanesDeCuenta(CUENTA, List.of(activa(GRATIS, T0)));

        PlanesDeCuenta despues = p.cambiarA(UUID.randomUUID(), NEGOCIO, T0.plusSeconds(1), null, 0);
        PlanesDeCuenta otraVez = despues.cambiarA(UUID.randomUUID(), GRATIS, T0.plusSeconds(2), null, 0);

        assertThat(p.activa().planId()).isEqualTo(GRATIS);
        assertThat(p.suscripciones()).hasSize(1);
        assertThat(otraVez.suscripciones()).hasSize(3);
        assertThat(otraVez.suscripciones().stream().filter(Suscripcion::activa)).hasSize(1);
        assertThat(otraVez.activa().planId()).isEqualTo(GRATIS);
    }

    @Test void noSePuedeCambiarConUnaFechaAnteriorAlInicioDeLaActual() {
        PlanesDeCuenta p = new PlanesDeCuenta(CUENTA, List.of(activa(GRATIS, T0)));

        assertThat(codigo(() -> p.cambiarA(UUID.randomUUID(), NEGOCIO, T0.minusSeconds(1), null, 0))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
    }

    @Test void unCambioConDatosInvalidosNoDejaALaCuentaSinPlan() {
        PlanesDeCuenta p = new PlanesDeCuenta(CUENTA, List.of(activa(GRATIS, T0)));

        assertThat(codigo(() -> p.cambiarA(UUID.randomUUID(), NEGOCIO, T0.plusSeconds(10), T0.plusSeconds(5), 0))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
        assertThat(codigo(() -> p.cambiarA(UUID.randomUUID(), NEGOCIO, T0.plusSeconds(10), null, -2))).isEqualTo("GRACIA_INVALIDA");
        assertThat(p.activa().planId()).isEqualTo(GRATIS);
    }

    @Test void laListaDeSuscripcionesNoSePuedeAlterarPorFuera() {
        PlanesDeCuenta p = new PlanesDeCuenta(CUENTA, List.of(activa(GRATIS, T0)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> p.suscripciones().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
