package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.tenant.ApiKey;

import java.time.Instant;
import java.util.UUID;

/** Vista de una API key en listados: nunca incluye el secreto (solo se entrega al crearla). */
public record ApiKeyResumenResponse(
        @Schema(example = "3b9d5c1e-7f2a-4d8b-9c6e-1a2b3c4d5e6f") UUID id,
        @Schema(example = "fk_9k2m4p1q", description = "Primeros 10 caracteres de la key, para identificarla") String prefijo,
        @Schema(example = "true") boolean activa,
        @Schema(example = "2026-09-15T10:00:00Z") Instant creadaEn,
        @Schema(example = "null", description = "Presente solo si fue revocada") Instant revocadaEn) {
    public static ApiKeyResumenResponse de(ApiKey k) { return new ApiKeyResumenResponse(k.id(), k.prefijo(), k.activa(), k.creadaEn(), k.revocadaEn()); }
}
