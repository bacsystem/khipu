package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.tenant.Tenant;

import java.util.UUID;

public record EmpresaVistaResponse(
        @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID id,
        @Schema(example = "20123456786") String ruc,
        @Schema(example = "Comercial Andina SAC") String razonSocial,
        @Schema(example = "BETA") String entorno,
        @Schema(example = "true") boolean tieneCertificado,
        @Schema(example = "true") boolean tieneCredencialesSol,
        @Schema(example = "true", description = "Si el domicilio fiscal ya está configurado (va en el XML del emisor)") boolean tieneDomicilio) {

    public static EmpresaVistaResponse de(Tenant t) {
        return new EmpresaVistaResponse(t.id(), t.ruc(), t.razonSocial(), t.entorno().name(),
                t.certificado() != null, t.sol() != null, t.domicilio() != null);
    }
}
