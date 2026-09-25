package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.plataforma.Administrador;

import java.util.UUID;

public record AdministradorResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID id,
        @Schema(example = "ana@khipu.pe") String email) {
    public static AdministradorResponse de(Administrador a) {
        return new AdministradorResponse(a.id(), a.email());
    }
}
