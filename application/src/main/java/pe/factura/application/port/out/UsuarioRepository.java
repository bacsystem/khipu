package pe.factura.application.port.out;

import pe.factura.domain.cuenta.Usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository {
    void guardar(Usuario u);
    Optional<Usuario> buscar(UUID id);
    Optional<Usuario> buscarPorEmail(String email);

    /**
     * Marca el correo verificado sin reescribir el resto del usuario (#22): un cambio de contraseña que entre a la vez no se pierde. Si
     * ya estaba verificado conserva la primera fecha.
     */
    void marcarCorreoVerificado(UUID id, Instant cuando);
}
