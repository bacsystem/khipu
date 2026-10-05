package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.IdempotenciaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LimpiarIdempotenciaServiceTest {
    /** Las claves viven 24 horas: lo bastante para cualquier reintento tras un corte, sin que la tabla crezca sin límite. */
    @Test void borraLasClavesDeMasDe24Horas() {
        Instant[] limite = new Instant[1];
        IdempotenciaRepository repo = new IdempotenciaRepository() {
            public Optional<Registro> buscar(String a, String c) { return Optional.empty(); }
            public Optional<Registro> reservar(String a, String c, String h) { return Optional.empty(); }
            public void completar(String a, String c, UUID r) {}
            public int borrarAnterioresA(Instant l) { limite[0] = l; return 3; }
        };

        int borradas = new LimpiarIdempotenciaService(repo, Fakes.CLOCK).limpiar();

        assertThat(borradas).isEqualTo(3);
        assertThat(limite[0]).isEqualTo(Instant.parse("2026-09-12T15:00:00Z"));
    }
}
