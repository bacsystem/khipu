package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.tenant.Entorno;

/** Cuerpo de «cambiar el entorno de una empresa» (#187). */
public record CambiarEntornoRequest(
        @Schema(example = "PRODUCCION", description = "El entorno al que pasa la empresa: `BETA` o `PRODUCCION`. Cualquier otro valor responde 400") Entorno entorno) {}
