package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OrigenResponse(
        @Schema(example = "203.0.113.7", description = "IP que el backend resolvió para esta petición: la misma que la bitácora de auditoría registraría") String ip) {}
