package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiKeyResponse(
        @Schema(example = "fac_live_9k2m4p1q7r3s5t8u", description = "Solo se muestra una vez, en texto plano") String apiKey) {}
