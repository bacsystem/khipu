package pe.factura.application.port.out;

import pe.factura.domain.plataforma.Administrador;

import java.util.Optional;
import java.util.UUID;

public interface AdministradorRepository {
    void guardar(Administrador a);
    Optional<Administrador> buscar(UUID id);
    Optional<Administrador> buscarPorEmail(String email);
}
