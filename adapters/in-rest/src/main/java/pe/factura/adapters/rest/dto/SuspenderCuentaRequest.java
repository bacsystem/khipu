package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo opcional de «suspender una cuenta» (#182). */
public record SuspenderCuentaRequest(
        @Schema(example = "Factura de septiembre sin pagar", maxLength = 200,
                description = "Por qué se suspende; es opcional y queda en la bitácora de auditoría. Nunca debe llevar secretos") String motivo) {}
