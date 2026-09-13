package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.domain.tenant.ApiKey;
import java.util.Optional;
import java.util.UUID;

public class JdbcApiKeyRepository implements ApiKeyRepository {
    private final JdbcTemplate jdbc;
    public JdbcApiKeyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void guardar(ApiKey k) {
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo, activa) VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET activa = EXCLUDED.activa, revoked_at = CASE WHEN EXCLUDED.activa THEN NULL ELSE now() END",
                k.id(), k.tenantId(), k.hash(), k.prefijo(), k.activa());
    }
    @Override public Optional<ApiKey> buscarPorHash(String hash) {
        return jdbc.query("SELECT id, tenant_id, key_hash, prefijo, activa FROM api_key WHERE key_hash = ?",
                (rs, i) -> new ApiKey(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("key_hash"), rs.getString("prefijo"), rs.getBoolean("activa")), hash)
                .stream().findFirst();
    }
}
