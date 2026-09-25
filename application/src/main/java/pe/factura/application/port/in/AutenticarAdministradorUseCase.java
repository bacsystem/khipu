package pe.factura.application.port.in;

import pe.factura.domain.plataforma.Administrador;

import java.util.UUID;

public interface AutenticarAdministradorUseCase {
    record Sesion(String accessToken, Administrador administrador) {}

    Sesion login(String email, String password);
    Administrador me(UUID administradorId);
}
