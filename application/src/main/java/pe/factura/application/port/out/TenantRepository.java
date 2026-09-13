package pe.factura.application.port.out;

import pe.factura.domain.tenant.Tenant;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {
    void guardar(Tenant t);
    Optional<Tenant> buscar(UUID id);
    Optional<Tenant> buscarPorRuc(String ruc);
}
