package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.PurgaDeRecuperaciones;
import pe.factura.application.port.out.PurgaDeVerificaciones;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurgarTokensDeAccesoServiceTest {
    static final Duration RETENCION = Duration.ofDays(7);

    /** Devuelve los lotes de la lista en orden (lleno, lleno, parcial…) y anota con qué corte y tamaño se le pidió cada uno. */
    static class PurgaFake implements PurgaDeRecuperaciones, PurgaDeVerificaciones {
        final List<String> llamadas = new ArrayList<>();
        final List<Instant> cortesRecuperaciones = new ArrayList<>();
        final List<Instant> cortesVerificaciones = new ArrayList<>();
        final List<Integer> lotes = new ArrayList<>();
        final int[] respuestasRecuperaciones;
        final int[] respuestasVerificaciones;
        PurgaFake(int[] recuperaciones, int[] verificaciones) {
            this.respuestasRecuperaciones = recuperaciones;
            this.respuestasVerificaciones = verificaciones;
        }
        public int purgarRecuperacionesVencidas(Instant vencidasAntesDe, int lote) {
            llamadas.add("recuperaciones");
            lotes.add(lote);
            return responder(cortesRecuperaciones, vencidasAntesDe, respuestasRecuperaciones);
        }
        public int purgarVerificacionesVencidas(Instant vencidasAntesDe, int lote) {
            llamadas.add("verificaciones");
            lotes.add(lote);
            return responder(cortesVerificaciones, vencidasAntesDe, respuestasVerificaciones);
        }
        private static int responder(List<Instant> cortes, Instant corte, int[] respuestas) {
            int n = cortes.size();
            cortes.add(corte);
            return n < respuestas.length ? respuestas[n] : 0;
        }
    }

    static int[] r(int... respuestas) { return respuestas; }

    static int[] siempreLleno() {
        int[] todos = new int[PurgarTokensDeAccesoService.MAX_LOTES * 2];
        Arrays.fill(todos, PurgarTokensDeAccesoService.LOTE);
        return todos;
    }

    PurgarTokensDeAccesoService servicio(PurgaFake purga) { return new PurgarTokensDeAccesoService(purga, purga, Fakes.CLOCK, RETENCION); }

    @Test void elCorteEsElRelojMenosLaRetencionParaLasDosTablas() {
        PurgaFake purga = new PurgaFake(r(0), r(0));

        servicio(purga).purgar();

        Instant esperado = Instant.parse("2026-09-06T15:00:00Z");
        assertThat(Instant.now(Fakes.CLOCK).minus(RETENCION)).isEqualTo(esperado);
        assertThat(purga.cortesRecuperaciones).containsExactly(esperado);
        assertThat(purga.cortesVerificaciones).containsExactly(esperado);
    }

    @Test void sumaLoBorradoDeLasDosTablas() {
        PurgaFake purga = new PurgaFake(r(37), r(5));

        assertThat(servicio(purga).purgar()).isEqualTo(42);
        assertThat(purga.llamadas).as("un lote parcial es que ya no queda más: un solo lote por tabla").containsExactly("recuperaciones", "verificaciones");
        assertThat(purga.lotes).containsOnly(PurgarTokensDeAccesoService.LOTE);
    }

    @Test void sinNadaQueBorrarPideUnSoloLotePorTabla() {
        PurgaFake purga = new PurgaFake(r(), r());

        assertThat(servicio(purga).purgar()).isZero();
        assertThat(purga.llamadas).containsExactly("recuperaciones", "verificaciones");
    }

    @Test void repiteMientrasElLoteLlegaLlenoYTodosUsanElMismoCorte() {
        int lote = PurgarTokensDeAccesoService.LOTE;
        PurgaFake purga = new PurgaFake(r(lote, lote, 5), r(lote, 9));

        assertThat(servicio(purga).purgar()).isEqualTo(2 * lote + 5 + lote + 9);
        assertThat(purga.cortesRecuperaciones).hasSize(3);
        assertThat(purga.cortesVerificaciones).hasSize(2);
        assertThat(purga.cortesRecuperaciones).containsOnly(purga.cortesRecuperaciones.get(0));
        assertThat(purga.cortesVerificaciones).containsOnly(purga.cortesRecuperaciones.get(0));
    }

    /** El tope vale por tabla: un atraso enorme en una no le quita su pasada a la otra. */
    @Test void sePareEnElTopeDeLotesPorTablaYLaOtraTablaTambienCorre() {
        PurgaFake purga = new PurgaFake(siempreLleno(), siempreLleno());

        int borradas = servicio(purga).purgar();

        assertThat(purga.cortesRecuperaciones).hasSize(PurgarTokensDeAccesoService.MAX_LOTES);
        assertThat(purga.cortesVerificaciones).hasSize(PurgarTokensDeAccesoService.MAX_LOTES);
        assertThat(borradas).isEqualTo(2 * PurgarTokensDeAccesoService.MAX_LOTES * PurgarTokensDeAccesoService.LOTE);
    }

    @Test void siFallaLaPrimeraTablaElErrorSePropagaParaQueElWorkerLoRegistre() {
        PurgaDeRecuperaciones rota = (corte, lote) -> { throw new IllegalStateException("la base no responde"); };
        PurgaDeVerificaciones intacta = (corte, lote) -> 0;

        assertThatThrownBy(() -> new PurgarTokensDeAccesoService(rota, intacta, Fakes.CLOCK, RETENCION).purgar())
                .isInstanceOf(IllegalStateException.class);
    }
}
