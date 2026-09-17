package pe.factura.application.port.out;

import pe.factura.domain.tenant.ApiKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository {
    void guardar(ApiKey k);
    Optional<ApiKey> buscarPorHash(String hash);
    Optional<ApiKey> buscar(UUID id);
    List<ApiKey> listarPorTenant(UUID tenantId);   // más reciente primero
}
