package pe.factura.application.port.in;

import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Impersonar a un usuario de un cliente desde el backoffice (#184): el administrador mira el portal como ese usuario, para reproducir lo que ve cuando
 * reporta un problema. Es la función más sensible del backoffice, y por eso es lo más acotada posible: el token es una sesión de soporte (el filtro de JWT
 * la deja **solo leer**: no cambia contraseñas, credenciales SOL, API keys ni emite), vence a los {@link #DURACION} y no se puede renovar (no tiene refresh), y
 * queda en la bitácora quién impersonó a quién, cuándo y por cuánto tiempo, en la misma transacción. El cliente ve esos accesos en su propio historial.
 */
public interface ImpersonarUsuarioUseCase {
    /** Lo que dura una sesión de soporte. Corta a propósito: para seguir mirando se impersona de nuevo, y cada vez queda su registro. */
    Duration DURACION = Duration.ofMinutes(15);

    /**
     * {@code REQUIERE_ADMINISTRADOR} si el actor es la clave de plataforma (no identifica a nadie); {@code NO_ENCONTRADO} si el usuario no existe o no es de
     * esa cuenta; {@code USUARIO_INACTIVO} si está desactivado.
     */
    Impersonacion impersonar(ActorAdmin actor, UUID cuentaId, UUID usuarioId);

    /** El token de la sesión de soporte (solo se entrega aquí), hasta cuándo vale y a quién se está mirando. */
    record Impersonacion(String token, Instant expiraEn, Usuario usuario) {}
}
