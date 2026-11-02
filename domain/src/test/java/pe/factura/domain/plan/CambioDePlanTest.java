package pe.factura.domain.plan;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 332-H3: cuándo manda un cambio programado, en un solo lugar para el plan vigente de la cuenta y para el tope de documentos. */
class CambioDePlanTest {
    final Instant desde = Instant.parse("2026-11-01T05:00:00Z");
    final CambioDePlan cambio = new CambioDePlan(UUID.randomUUID(), desde, null, 0);

    @Test void mandaDesdeSuFechaInclusive() {
        assertThat(cambio.mandaEn(desde.minusSeconds(1))).isFalse();
        assertThat(cambio.mandaEn(desde)).as("en el instante exacto ya manda").isTrue();
        assertThat(cambio.mandaEn(desde.plusSeconds(300))).isTrue();
    }
}
