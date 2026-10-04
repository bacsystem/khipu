package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.IdempotenciaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LimpiarIdempotenciaServiceTest {
    Instant[] limiteClaves = new Instant[1];
    Instant[] limiteRespuestas = new Instant[1];
    IdempotenciaRepository repo = new IdempotenciaRepository() {
        public Optional<Registro> reservar(String a, String c, String h) { return Optional.empty(); }
        public Optional<Registro> buscar(String a, String c) { return Optional.empty(); }
        public void completar(String a, String c, UUID r, byte[] respuesta) {}
        public int borrarAnterioresA(Instant l) { limiteClaves[0] = l; return 3; }
        public int olvidarRespuestasAnterioresA(Instant l) { limiteRespuestas[0] = l; return 2; }
    };

    /** Las claves viven 24 horas: lo bastante para cualquier reintento tras un corte, sin que la tabla crezca sin límite. */
    @Test void borraLasClavesDeMasDe24Horas() {
        int borradas = new LimpiarIdempotenciaService(repo, Fakes.CLOCK).limpiar();

        assertThat(borradas).isEqualTo(3 + 2);
        assertThat(limiteClaves[0]).isEqualTo(Instant.parse("2026-09-12T15:00:00Z"));
    }

    /** Una respuesta guardada puede llevar un secreto (la API key del alta, #219): se olvida a la hora, la clave sigue un día. */
    @Test void olvidaLasRespuestasDeMasDeUnaHora() {
        new LimpiarIdempotenciaService(repo, Fakes.CLOCK).limpiar();

        assertThat(limiteRespuestas[0]).isEqualTo(Instant.parse("2026-09-13T14:00:00Z"));
    }
}
