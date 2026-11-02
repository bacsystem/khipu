package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.cuenta.AceptacionDeTerminos;

import java.time.Instant;

/** Lo que quedó guardado al aceptar los términos (#342): la versión vigente y desde cuándo la aceptó. */
@Schema(name = "AceptacionDeTerminosResponse")
public record AceptacionDeTerminosResponse(
        @Schema(example = "2026-10-20", description = "La versión vigente de los términos del servicio y la política de privacidad, la que se aceptó") String version,
        @Schema(example = "2026-10-20T15:00:00Z", description = "Cuándo la aceptó por primera vez") Instant aceptadaEn) {
    public static AceptacionDeTerminosResponse de(AceptacionDeTerminos a) {
        return new AceptacionDeTerminosResponse(a.version(), a.aceptadaEn());
    }
}
