package pe.factura.application.port.out;

import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository {
    /** Crea o reescribe el usuario entero. Para cambiar un dato de un usuario que ya existe, los métodos de abajo: no pisan lo que otro cambió a la vez. */
    void guardar(Usuario u);
    Optional<Usuario> buscar(UUID id);
    Optional<Usuario> buscarPorEmail(String email);

    /**
     * Marca el correo verificado sin reescribir el resto del usuario (#22): un cambio de contraseña que entre a la vez no se pierde. Si
     * ya estaba verificado conserva la primera fecha.
     */
    void marcarCorreoVerificado(UUID id, Instant cuando);

    /*
     * #21 (H2): cambios de una sola columna. Un `guardar` con el usuario leído antes pisaba en silencio lo que otro hubiera cambiado entre la lectura y la escritura
     * (un administrador que lo desactivó o le cambió el rol mientras restablecía su contraseña). La base los implementa con un UPDATE de esa columna; lo de abajo es
     * solo para los dobles de prueba en memoria, donde no hay concurrencia.
     */

    /** Solo la contraseña, y el correo verificado si todavía no lo estaba (el enlace llegó a su correo). */
    default void cambiarPassword(UUID id, String passwordHash, Instant correoVerificadoEn) {
        buscar(id).ifPresent(u -> guardar(u.conPasswordHash(passwordHash).conCorreoVerificado(correoVerificadoEn)));
    }

    /** Solo el rol. */
    default void cambiarRol(UUID id, Rol rol) {
        buscar(id).ifPresent(u -> guardar(u.conRol(rol)));
    }

    /** Solo si está activo. */
    default void cambiarActivo(UUID id, boolean activo) {
        buscar(id).ifPresent(u -> guardar(activo ? u.reactivar() : u.desactivar()));
    }
}
