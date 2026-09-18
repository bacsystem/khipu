package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.tenant.Domicilio;

public record DomicilioResponse(
        @Schema(example = "150122") String ubigeo,
        @Schema(example = "Av. Javier Prado Este 123 Of. 501") String direccion,
        @Schema(example = "Urb. Jardín") String urbanizacion,
        @Schema(example = "MIRAFLORES") String distrito,
        @Schema(example = "LIMA") String provincia,
        @Schema(example = "LIMA") String departamento,
        @Schema(example = "0000") String codigoEstablecimiento) {
    public static DomicilioResponse de(Domicilio d) {
        return d == null ? null : new DomicilioResponse(d.ubigeo(), d.direccion(), d.urbanizacion(), d.distrito(), d.provincia(), d.departamento(), d.codigoEstablecimiento());
    }
}
