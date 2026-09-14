package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CrearTenantResponse(
        @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID tenantId,
        @Schema(example = "20123456789") String ruc,
        @Schema(example = "fac_live_9k2m4p1q7r3s5t8u", description = "Solo se muestra una vez, en texto plano") String apiKey) {}
