package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SerieResponse(
        @Schema(example = "01", description = "01=factura, 03=boleta, 07=nota de crédito, 08=nota de débito") String tipo,
        @Schema(example = "F001") String serie,
        @Schema(example = "125") long ultimoNumero,
        @Schema(example = "true") boolean activa) {}
