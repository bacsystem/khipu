package pe.factura.application.port.out;

import pe.factura.domain.tenant.ApiKey;

import java.util.Optional;

public interface ApiKeyRepository {
    void guardar(ApiKey k);
    Optional<ApiKey> buscarPorHash(String hash);
}
