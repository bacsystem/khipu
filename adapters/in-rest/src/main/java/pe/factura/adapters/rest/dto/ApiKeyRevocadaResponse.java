package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.ApiKeyRevocada;

import java.time.Instant;
import java.util.UUID;

/** Qué API key se revocó y cuándo (#187). Nunca lleva la clave ni su hash. */
public record ApiKeyRevocadaResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID apiKeyId,
        @Schema(example = "2026-10-03T09:00:00Z") Instant revocadaEn) {
    public static ApiKeyRevocadaResponse de(ApiKeyRevocada r) {
        return new ApiKeyRevocadaResponse(r.apiKeyId(), r.revocadaEn());
    }
}
