package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.domain.tenant.ApiKey;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcApiKeyRepository implements ApiKeyRepository {
    private static final String COLUMNAS = "id, tenant_id, key_hash, prefijo, activa, created_at, revoked_at";
    private static final RowMapper<ApiKey> MAPPER = (rs, i) -> new ApiKey(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("key_hash"), rs.getString("prefijo"),
            rs.getBoolean("activa"), instante(rs.getTimestamp("created_at")), instante(rs.getTimestamp("revoked_at")));
    private final JdbcTemplate jdbc;

    @Override public void guardar(ApiKey k) {
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo, activa, created_at, revoked_at) VALUES (?, ?, ?, ?, ?, COALESCE(?, now()), ?) " +
                        "ON CONFLICT (id) DO UPDATE SET activa = EXCLUDED.activa, revoked_at = EXCLUDED.revoked_at",
                k.id(), k.tenantId(), k.hash(), k.prefijo(), k.activa(), marca(k.creadaEn()), marca(k.revocadaEn()));
    }
    @Override public Optional<ApiKey> buscarPorHash(String hash) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM api_key WHERE key_hash = ?", MAPPER, hash).stream().findFirst();
    }
    @Override public Optional<ApiKey> buscar(UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM api_key WHERE id = ?", MAPPER, id).stream().findFirst();
    }
    @Override public List<ApiKey> listarPorTenant(UUID tenantId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM api_key WHERE tenant_id = ? ORDER BY created_at DESC, id", MAPPER, tenantId);
    }

    private static Instant instante(Timestamp t) { return t == null ? null : t.toInstant(); }
    private static Timestamp marca(Instant i) { return i == null ? null : Timestamp.from(i); }
}
