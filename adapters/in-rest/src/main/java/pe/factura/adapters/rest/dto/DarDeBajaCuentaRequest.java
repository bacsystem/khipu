package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo opcional de «dar de baja una cuenta» (#201). */
public record DarDeBajaCuentaRequest(
        @Schema(example = "Cerró su negocio", maxLength = 200,
                description = "Por qué se da de baja; es opcional y queda en la bitácora de auditoría. Nunca debe llevar secretos") String motivo) {}
