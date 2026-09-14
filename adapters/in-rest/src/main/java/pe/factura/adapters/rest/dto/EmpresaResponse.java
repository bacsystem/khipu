package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.tenant.Tenant;

import java.time.LocalDate;
import java.util.UUID;

public record EmpresaResponse(
        @Schema(example = "5f2c1e6a-7b3d-4a2e-9c1f-3a2b1c4d5e6f") UUID id,
        @Schema(example = "20123456789") String ruc,
        @Schema(example = "Comercial Andina SAC") String razonSocial,
        @Schema(example = "BETA") String entorno,
        @Schema(example = "true") boolean tieneCredencialesSol,
        @Schema(example = "2027-12-31") LocalDate certificadoVigenciaHasta) {

    public static EmpresaResponse de(Tenant t) {
        return new EmpresaResponse(t.id(), t.ruc(), t.razonSocial(), t.entorno().name(), t.sol() != null,
                t.certificado() == null ? null : t.certificado().vigenciaHasta());
    }
}
