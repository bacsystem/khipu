package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.CambioDeEntorno;
import pe.factura.domain.tenant.Entorno;

import java.util.UUID;

/** Cómo quedó una empresa después de cambiarle el entorno (#187). */
public record CambioDeEntornoResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID empresaId,
        @Schema(description = "El entorno que tenía") Entorno desde,
        @Schema(description = "El entorno que tiene ahora") Entorno hacia) {
    public static CambioDeEntornoResponse de(CambioDeEntorno c) {
        return new CambioDeEntornoResponse(c.empresaId(), c.desde(), c.hacia());
    }
}
