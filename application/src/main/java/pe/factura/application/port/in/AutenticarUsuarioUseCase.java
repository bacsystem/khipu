package pe.factura.application.port.in;

import pe.factura.domain.cuenta.Usuario;

import java.util.UUID;

public interface AutenticarUsuarioUseCase {
    /** access: JWT de corta vida; refresh: token opaco de larga vida (solo se guarda su hash). */
    record Tokens(String access, String refresh, Usuario usuario) {}

    /**
     * Crea la cuenta con el correo sin verificar y le manda el enlace de verificación (#22); {@code urlBase} es la del portal. Entra
     * igual: puede ver el portal, pero no crear empresas ni emitir hasta verificar.
     */
    Tokens registrar(String nombreCuenta, String email, String password, String telefono, String urlBase);
    Tokens login(String email, String password);
    Tokens refrescar(String refresh);
    void logout(String refresh);
    Usuario me(UUID usuarioId);
    /** Siempre termina sin error para no revelar si el correo existe. */
    void solicitarRecuperacion(String email, String urlBase);
    /** También verifica el correo: abrir el enlace demuestra que es suyo (#22). */
    void restablecer(String token, String nuevaPassword);

    /** Verifica el correo con el enlace (#22): vale 24 horas y una sola vez. */
    void verificarCorreo(String token);

    /** Manda otro enlace de verificación; {@code CORREO_YA_VERIFICADO} si no hace falta. */
    void reenviarVerificacion(UUID usuarioId, String urlBase);
}
