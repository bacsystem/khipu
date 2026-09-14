package pe.factura.adapters.rest.dto;

import pe.factura.domain.cuenta.Usuario;

import java.util.UUID;

public record UsuarioResponse(UUID id, UUID cuentaId, String email, String rol) {
    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.id(), u.cuentaId(), u.email(), u.rol().name());
    }
}
