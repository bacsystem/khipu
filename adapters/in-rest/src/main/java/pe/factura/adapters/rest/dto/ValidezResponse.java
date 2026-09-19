package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ValidezResponse(
        @Schema(example = "ACEPTADO", description = "`ACEPTADO`, `RECHAZADO`, `DE_BAJA`, `NO_EXISTE`, `AJENO` o `ERROR_CONSULTA`") String estado,
        @Schema(example = "0001", description = "Código de retorno de SUNAT") String codigo,
        @Schema(example = "El comprobante existe y está aceptado.") String mensaje) {}
