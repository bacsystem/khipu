package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;

public record EstablecimientoResponse(
        @Schema(example = "0002") String codigo,
        @Schema(example = "Tienda Miraflores") String nombre,
        DomicilioResponse domicilio,
        @Schema(example = "true", description = "`false` = dado de baja: no admite series nuevas ni emisiones") boolean activo,
        @Schema(example = "false", description = "`true` solo en el `0000`: es el domicilio fiscal de la empresa, se edita en datos fiscales y no se puede dar de baja") boolean principal) {

    public static EstablecimientoResponse de(Establecimiento e) {
        return new EstablecimientoResponse(e.codigo(), e.nombre(), DomicilioResponse.de(e.domicilio()), e.activo(), false);
    }

    /** El domicilio fiscal como establecimiento `0000`, para que la lista tenga todos los puntos desde los que se emite. */
    public static EstablecimientoResponse principal(Domicilio d) {
        return new EstablecimientoResponse(Domicilio.ESTABLECIMIENTO_PRINCIPAL, "Domicilio fiscal", DomicilioResponse.de(d), true, true);
    }
}
