package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** El ciclo es el mes calendario en America/Lima (UTC-5, sin horario de verano): ahí empieza el consumo de cada mes y ahí entran los cambios de límites. */
class CicloMensualTest {
    @Test void elSiguienteCicloEmpiezaElPrimeroDelMesSiguienteALaMedianocheDeLima() {
        Instant ahora = Instant.parse("2026-10-15T15:00:00Z");

        assertThat(CicloMensual.inicioDelSiguiente(ahora)).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
    }

    /** 01:00 UTC del 1 de noviembre todavía es el 31 de octubre en Lima: el ciclo de octubre sigue en curso. */
    @Test void elPrimeroEnUtcPuedeSerTodaviaElMesAnteriorEnLima() {
        Instant ahora = Instant.parse("2026-11-01T01:00:00Z");

        assertThat(CicloMensual.inicioDelSiguiente(ahora)).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
    }

    @Test void justoEnElInstanteDeInicioYaEsElCicloNuevo() {
        Instant inicio = Instant.parse("2026-11-01T05:00:00Z");

        assertThat(CicloMensual.inicioDelSiguiente(inicio)).isEqualTo(Instant.parse("2026-12-01T05:00:00Z"));
        assertThat(CicloMensual.inicioDelSiguiente(inicio.minusMillis(1))).isEqualTo(inicio);
    }

    @Test void diciembreSaltaAlAnioSiguiente() {
        assertThat(CicloMensual.inicioDelSiguiente(Instant.parse("2026-12-20T12:00:00Z"))).isEqualTo(Instant.parse("2027-01-01T05:00:00Z"));
    }

    @Test void elUltimoDiaDelMesTambienVaAlSiguiente() {
        assertThat(CicloMensual.inicioDelSiguiente(Instant.parse("2026-09-30T23:00:00Z"))).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
        assertThat(CicloMensual.inicioDelSiguiente(Instant.parse("2028-02-29T12:00:00Z"))).isEqualTo(Instant.parse("2028-03-01T05:00:00Z"));
    }
}
