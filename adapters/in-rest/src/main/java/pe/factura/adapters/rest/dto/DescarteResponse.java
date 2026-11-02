package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ResolverErroresUseCase.Descarte;

import java.util.UUID;

/** El comprobante que un administrador descartó (#196). */
public record DescarteResponse(UUID comprobanteId, @Schema(example = "DESCARTADO") String estado) {
    public static DescarteResponse de(Descarte d) { return new DescarteResponse(d.comprobanteId(), d.estado().name()); }
}
