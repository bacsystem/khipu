package pe.factura.application.port.in;

import pe.factura.domain.cuenta.Usuario;

import java.util.UUID;

public interface AutenticarUsuarioUseCase {
    /** access: JWT de corta vida; refresh: token opaco de larga vida (solo se guarda su hash). */
    record Tokens(String access, String refresh, Usuario usuario) {}

    Tokens registrar(String nombreCuenta, String email, String password);
    Tokens login(String email, String password);
    Tokens refrescar(String refresh);
    void logout(String refresh);
    Usuario me(UUID usuarioId);
    /** Siempre termina sin error para no revelar si el correo existe. */
    void solicitarRecuperacion(String email, String urlBase);
    void restablecer(String token, String nuevaPassword);
}
