package pe.factura.application.port.in;

import pe.factura.domain.cuenta.Rol;

import java.time.Instant;

/** Lo que hace el invitado con el enlace que le llegó (#21), sin sesión: ver a qué lo invitaron y aceptar creando su contraseña. */
public interface AceptarInvitacionUseCase {
    record Detalle(String email, String cuenta, Rol rol, Instant expiraEn) {}

    /** {@code TOKEN_INVALIDO} si el enlace no existe, venció, se revocó, se reemplazó por un reenvío o ya se usó. */
    Detalle consultar(String token);

    /**
     * Crea su usuario en la cuenta con el rol de la invitación y el correo ya verificado (el enlace llegó a ese correo). Después entra con su correo y la contraseña
     * elegida. {@code TOKEN_INVALIDO} como en {@link #consultar}; {@code USUARIO_EXISTENTE} si el correo ya tiene un usuario.
     */
    void aceptar(String token, String password);
}
