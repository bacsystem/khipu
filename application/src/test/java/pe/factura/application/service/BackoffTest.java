package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class BackoffTest {
    private final Instant ahora = Instant.parse("2026-09-13T15:00:00Z");
    @Test void exponencialConTope() {
        assertThat(Backoff.siguiente(1, ahora)).isEqualTo(ahora.plus(Duration.ofMinutes(2)));
        assertThat(Backoff.siguiente(3, ahora)).isEqualTo(ahora.plus(Duration.ofMinutes(8)));
        assertThat(Backoff.siguiente(8, ahora)).isEqualTo(ahora.plus(Duration.ofMinutes(256)));
        assertThat(Backoff.siguiente(9, ahora)).isEqualTo(ahora.plus(Duration.ofHours(6)));
        assertThat(Backoff.siguiente(20, ahora)).isEqualTo(ahora.plus(Duration.ofHours(6)));
    }
}
