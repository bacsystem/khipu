package pe.factura.application.port.out;

import pe.factura.domain.cuenta.Usuario;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository {
    void guardar(Usuario u);
    Optional<Usuario> buscar(UUID id);
    Optional<Usuario> buscarPorEmail(String email);
}
