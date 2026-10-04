package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SuscripcionTest {
    static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
    static final UUID CUENTA = UUID.randomUUID();
    static final UUID PLAN = UUID.randomUUID();

    static Suscripcion de(Instant inicia, Instant vence, int gracia, Instant termina) {
        return new Suscripcion(UUID.randomUUID(), CUENTA, PLAN, inicia, vence, gracia, termina);
    }

    static String codigo(Runnable r) { return catchThrowableOfType(DomainException.class, r::run).codigo(); }

    /** La misma regla, sin necesitar la suscripción entera (la usa el listado de consumo): los mismos bordes exactos. */
    @Test void elEstadoDeLaVigenteSoloNecesitaElVencimientoYLaGracia() {
        Instant vence = T0.plusSeconds(86400);
        Instant finGracia = vence.plusSeconds(3 * 86400L);

        assertThat(Suscripcion.estadoDeLaVigente(null, 0, T0.plusSeconds(999_999_999))).isEqualTo(EstadoSuscripcion.VIGENTE);
        assertThat(Suscripcion.estadoDeLaVigente(vence, 3, vence.minusSeconds(1))).isEqualTo(EstadoSuscripcion.VIGENTE);
        assertThat(Suscripcion.estadoDeLaVigente(vence, 3, vence)).isEqualTo(EstadoSuscripcion.EN_GRACIA);
        assertThat(Suscripcion.estadoDeLaVigente(vence, 3, finGracia.minusSeconds(1))).isEqualTo(EstadoSuscripcion.EN_GRACIA);
        assertThat(Suscripcion.estadoDeLaVigente(vence, 3, finGracia)).isEqualTo(EstadoSuscripcion.VENCIDA);
        assertThat(Suscripcion.estadoDeLaVigente(vence, 0, vence)).isEqualTo(EstadoSuscripcion.VENCIDA);
    }

    @Test void estadoEnYEstadoDeLaVigenteDicenLoMismo() {
        Suscripcion s = de(T0, T0.plusSeconds(86400), 3, null);

        for (long segundos : new long[]{0, 86399, 86400, 86401, 86400 + 3 * 86400L - 1, 86400 + 3 * 86400L, 999_999}) {
            Instant ahora = T0.plusSeconds(segundos);
            assertThat(s.estadoEn(ahora)).as("%d s", segundos).isEqualTo(Suscripcion.estadoDeLaVigente(s.venceEn(), s.diasDeGracia(), ahora));
        }
    }

    @Test void sinVencimientoSiempreEstaVigente() {
        Suscripcion s = de(T0, null, 0, null);

        assertThat(s.estadoEn(T0)).isEqualTo(EstadoSuscripcion.VIGENTE);
        assertThat(s.estadoEn(T0.plusSeconds(10L * 365 * 86400))).isEqualTo(EstadoSuscripcion.VIGENTE);
        assertThat(s.hastaCuandoCubre()).isNull();
    }

    @Test void antesDelVencimientoEstaVigente() {
        Suscripcion s = de(T0, T0.plusSeconds(86400), 3, null);

        assertThat(s.estadoEn(T0.plusSeconds(86399))).isEqualTo(EstadoSuscripcion.VIGENTE);
    }

    /** El vencimiento es exclusivo: en el instante exacto ya no está vigente. */
    @Test void enElInstanteDelVencimientoEmpiezaLaGracia() {
        Suscripcion s = de(T0, T0.plusSeconds(86400), 3, null);

        assertThat(s.estadoEn(T0.plusSeconds(86400))).isEqualTo(EstadoSuscripcion.EN_GRACIA);
    }

    @Test void laGraciaDuraLosDiasPactadosYLuegoVence() {
        Suscripcion s = de(T0, T0.plusSeconds(86400), 3, null);
        Instant finGracia = T0.plusSeconds(86400).plusSeconds(3 * 86400L);

        assertThat(s.hastaCuandoCubre()).isEqualTo(finGracia);
        assertThat(s.estadoEn(finGracia.minusSeconds(1))).isEqualTo(EstadoSuscripcion.EN_GRACIA);
        assertThat(s.estadoEn(finGracia)).isEqualTo(EstadoSuscripcion.VENCIDA);
    }

    @Test void sinGraciaVenceDeUnaVez() {
        Suscripcion s = de(T0, T0.plusSeconds(86400), 0, null);

        assertThat(s.estadoEn(T0.plusSeconds(86400))).isEqualTo(EstadoSuscripcion.VENCIDA);
    }

    @Test void unaSuscripcionTerminadaFueReemplazadaAunqueNoHayaVencido() {
        Suscripcion s = de(T0, T0.plusSeconds(86400), 3, T0.plusSeconds(100));

        assertThat(s.activa()).isFalse();
        assertThat(s.estadoEn(T0.plusSeconds(50))).isEqualTo(EstadoSuscripcion.REEMPLAZADA);
    }

    @Test void terminarlaDejaLaFechaYNoToca() {
        Suscripcion s = de(T0, T0.plusSeconds(86400), 3, null);

        Suscripcion t = s.terminarEn(T0.plusSeconds(100));

        assertThat(t.terminaEn()).isEqualTo(T0.plusSeconds(100));
        assertThat(t.id()).isEqualTo(s.id());
        assertThat(t.venceEn()).isEqualTo(s.venceEn());
        assertThat(s.activa()).isTrue();
    }

    @Test void noSeTerminaDosVecesNiAntesDeEmpezar() {
        Suscripcion s = de(T0, null, 0, null);

        assertThat(codigo(() -> s.terminarEn(T0.minusSeconds(1)))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
        assertThat(codigo(() -> s.terminarEn(T0.plusSeconds(5)).terminarEn(T0.plusSeconds(9)))).isEqualTo("SUSCRIPCION_YA_TERMINADA");
    }

    @Test void terminarEnElInstanteDeInicioEsValido() {
        assertThat(de(T0, null, 0, null).terminarEn(T0).terminaEn()).isEqualTo(T0);
    }

    @Test void losDatosObligatoriosSeExigen() {
        assertThat(codigo(() -> new Suscripcion(null, CUENTA, PLAN, T0, null, 0, null))).isEqualTo("SUSCRIPCION_INVALIDA");
        assertThat(codigo(() -> new Suscripcion(UUID.randomUUID(), null, PLAN, T0, null, 0, null))).isEqualTo("SUSCRIPCION_INVALIDA");
        assertThat(codigo(() -> new Suscripcion(UUID.randomUUID(), CUENTA, null, T0, null, 0, null))).isEqualTo("SUSCRIPCION_INVALIDA");
        assertThat(codigo(() -> new Suscripcion(UUID.randomUUID(), CUENTA, PLAN, null, null, 0, null))).isEqualTo("SUSCRIPCION_INVALIDA");
    }

    @Test void laGraciaNoPuedeSerNegativa() {
        assertThat(codigo(() -> de(T0, T0.plusSeconds(10), -1, null))).isEqualTo("GRACIA_INVALIDA");
        assertThat(de(T0, T0.plusSeconds(10), 0, null).diasDeGracia()).isZero();
    }

    @Test void venceDespuesDeEmpezar() {
        assertThat(codigo(() -> de(T0, T0, 0, null))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
        assertThat(codigo(() -> de(T0, T0.minusSeconds(1), 0, null))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
    }

    @Test void noTerminaAntesDeEmpezar() {
        assertThat(codigo(() -> de(T0, null, 0, T0.minusSeconds(1)))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
    }
}
