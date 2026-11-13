package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.PurgaDeSesiones;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurgarSesionesServiceTest {
    static final Duration RETENCION = Duration.ofDays(7);

    /** Devuelve los lotes de la lista en orden (lleno, lleno, parcial…) y anota con qué corte y tamaño se le pidió cada uno. */
    static class PurgaFake implements PurgaDeSesiones {
        final List<Instant> cortes = new ArrayList<>();
        final List<Integer> lotes = new ArrayList<>();
        final int[] respuestas;
        PurgaFake(int... respuestas) { this.respuestas = respuestas; }
        public int purgarVencidas(Instant vencidasAntesDe, int lote) {
            int n = cortes.size();
            cortes.add(vencidasAntesDe);
            lotes.add(lote);
            return n < respuestas.length ? respuestas[n] : 0;
        }
    }

    PurgarSesionesService servicio(PurgaFake purga) { return new PurgarSesionesService(purga, Fakes.CLOCK, RETENCION); }

    @Test void elCorteEsElRelojMenosLaRetencion() {
        PurgaFake purga = new PurgaFake(0);

        servicio(purga).purgar();

        assertThat(purga.cortes).containsExactly(Instant.now(Fakes.CLOCK).minus(RETENCION));
        assertThat(purga.cortes.get(0)).isEqualTo(Instant.parse("2026-09-06T15:00:00Z"));
    }

    @Test void conUnLoteParcialSeDetieneYDevuelveLoBorrado() {
        PurgaFake purga = new PurgaFake(37);

        assertThat(servicio(purga).purgar()).isEqualTo(37);
        assertThat(purga.cortes).as("un lote que no llegó lleno es que ya no queda más").hasSize(1);
        assertThat(purga.lotes).containsExactly(PurgarSesionesService.LOTE);
    }

    @Test void sinNadaQueBorrarPideUnSoloLote() {
        PurgaFake purga = new PurgaFake();

        assertThat(servicio(purga).purgar()).isZero();
        assertThat(purga.cortes).hasSize(1);
    }

    @Test void repiteMientrasElLoteLlegaLleno() {
        PurgaFake purga = new PurgaFake(PurgarSesionesService.LOTE, PurgarSesionesService.LOTE, 5);

        assertThat(servicio(purga).purgar()).isEqualTo(2 * PurgarSesionesService.LOTE + 5);
        assertThat(purga.cortes).hasSize(3);
        assertThat(purga.cortes).as("todos los lotes de una pasada usan el mismo corte").containsOnly(purga.cortes.get(0));
    }

    /** Un atraso enorme no ocupa el hilo del planificador sin fin: a lo más {@code MAX_LOTES} lotes por pasada, el resto en la siguiente. */
    @Test void sePareEnElTopeDeLotesPorPasada() {
        int[] siempreLleno = new int[PurgarSesionesService.MAX_LOTES * 2];
        java.util.Arrays.fill(siempreLleno, PurgarSesionesService.LOTE);
        PurgaFake purga = new PurgaFake(siempreLleno);

        int borradas = servicio(purga).purgar();

        assertThat(purga.cortes).hasSize(PurgarSesionesService.MAX_LOTES);
        assertThat(borradas).isEqualTo(PurgarSesionesService.MAX_LOTES * PurgarSesionesService.LOTE);
    }
}
