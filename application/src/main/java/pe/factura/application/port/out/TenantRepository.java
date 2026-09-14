package pe.factura.application.port.out;

import pe.factura.domain.tenant.Tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {
    void guardar(Tenant t);
    Optional<Tenant> buscar(UUID id);
    Optional<Tenant> buscarPorRuc(String ruc);
    /** Empresas que pertenecen a una cuenta (portal). */
    List<Tenant> listarPorCuenta(UUID cuentaId);
    void asignarCuenta(UUID tenantId, UUID cuentaId);
    Optional<UUID> cuentaDe(UUID tenantId);
}
