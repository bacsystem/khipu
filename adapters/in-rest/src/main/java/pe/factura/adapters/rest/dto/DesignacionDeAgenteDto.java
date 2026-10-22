package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase.CambioDeDesignacion;
import pe.factura.domain.tenant.DesignacionDeAgente;

import java.util.UUID;

/** Si SUNAT designó a la empresa agente de retención o de percepción (#40): con cada una emite comprobantes 20 o 40. */
@Schema(name = "DesignacionDeAgente")
public record DesignacionDeAgenteDto(
        @NotNull @Schema(example = "true", description = "Agente de retención del IGV (comprobantes 20, series R###)") Boolean retencion,
        @NotNull @Schema(example = "false", description = "Agente de percepción del IGV (comprobantes 40, series P###)") Boolean percepcion) {

    public static DesignacionDeAgenteDto de(DesignacionDeAgente d) { return new DesignacionDeAgenteDto(d.retencion(), d.percepcion()); }

    public DesignacionDeAgente aDominio() { return new DesignacionDeAgente(Boolean.TRUE.equals(retencion), Boolean.TRUE.equals(percepcion)); }

    /** Cómo quedó la empresa después de registrar su designación. */
    @Schema(name = "CambioDeDesignacionDeAgente")
    public record Cambio(@Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID empresaId,
                         @Schema(description = "La designación que tenía") DesignacionDeAgenteDto desde,
                         @Schema(description = "La que tiene ahora") DesignacionDeAgenteDto hacia) {
        public static Cambio de(CambioDeDesignacion c) { return new Cambio(c.empresaId(), DesignacionDeAgenteDto.de(c.desde()), DesignacionDeAgenteDto.de(c.hacia())); }
    }
}
