package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ResolverErroresUseCase.Reintento;

import java.util.UUID;

/** Cómo terminó el reintento de un envío desde el backoffice (#196). Que SUNAT vuelva a fallar no es un error: el estado lo dice. */
public record ReintentoResponse(
        UUID comprobanteId,
        @Schema(example = "ACEPTADO", description = "El estado en que quedó: aceptado, rechazado, o `ERROR_ENVIO` si SUNAT volvió a fallar") String estado,
        @Schema(example = "4", description = "Cuántas veces falló el envío en total") int intentos,
        @Schema(description = "El fault de SUNAT si volvió a fallar o lo rechazó; ausente si no") ErrorDeEmisionResponse.Fault fault) {

    public static ReintentoResponse de(Reintento r) {
        return new ReintentoResponse(r.comprobanteId(), r.estado().name(), r.intentos(), ErrorDeEmisionResponse.Fault.de(r.fault()));
    }
}
