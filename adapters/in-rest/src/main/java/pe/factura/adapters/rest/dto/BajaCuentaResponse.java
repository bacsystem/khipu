package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.DarDeBajaCuentaUseCase.EstadoDeBaja;

import java.time.Instant;
import java.util.UUID;

/** Cómo quedó una cuenta después de darla de baja o reponerla (#201). */
public record BajaCuentaResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID cuentaId,
        @Schema(example = "2026-10-03T09:00:00Z", description = "Desde cuándo está dada de baja; ausente si la cuenta quedó repuesta") Instant bajaEn) {
    public static BajaCuentaResponse de(EstadoDeBaja e) {
        return new BajaCuentaResponse(e.cuentaId(), e.bajaEn());
    }
}
