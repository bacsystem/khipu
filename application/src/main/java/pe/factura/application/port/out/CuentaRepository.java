package pe.factura.application.port.out;

import pe.factura.domain.cuenta.Cuenta;
import java.util.Optional;
import java.util.UUID;

public interface CuentaRepository {
    void guardar(Cuenta c);
    Optional<Cuenta> buscar(UUID id);
    Optional<Cuenta> buscarPorEmail(String email);
}
