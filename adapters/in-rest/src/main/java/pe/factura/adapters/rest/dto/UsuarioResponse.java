package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.cuenta.Usuario;

import java.util.UUID;

public record UsuarioResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID id,
        @Schema(example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d") UUID cuentaId,
        @Schema(example = "facturacion@comercialandina.pe") String email,
        @Schema(example = "ADMIN") String rol) {
    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.id(), u.cuentaId(), u.email(), u.rol().name());
    }
}
