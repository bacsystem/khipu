package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Cuerpo para cambiar el plan de una cuenta (#191). */
public record CambiarPlanRequest(
        @Schema(description = "El plan al que pasa; tiene que estar en la oferta") UUID planId,
        @Schema(example = "2026-11-01T05:00:00Z", description = "Hasta cuándo queda pagado (exclusivo: en ese instante empieza la gracia). Obligatorio para un plan de pago; opcional para uno gratis") Instant venceEn,
        @Schema(example = "5", minimum = "0", maximum = "90", description = "Días que se sigue sirviendo después de vencido; 0 si no se indica") Integer diasDeGracia) {}
