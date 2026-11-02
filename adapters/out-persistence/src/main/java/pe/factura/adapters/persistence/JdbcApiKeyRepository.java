package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.application.port.out.PepperDeApiKeysRepository;
import pe.factura.domain.tenant.ApiKey;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcApiKeyRepository implements ApiKeyRepository, PepperDeApiKeysRepository {
    private static final String COLUMNAS = "id, tenant_id, key_hash, prefijo, activa, created_at, revoked_at";
    private static final RowMapper<ApiKey> MAPPER = (rs, i) -> new ApiKey(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("key_hash"), rs.getString("prefijo"),
            rs.getBoolean("activa"), instante(rs.getTimestamp("created_at")), instante(rs.getTimestamp("revoked_at")));
    private final JdbcTemplate jdbc;
    /** La huella del pepper vigente (S2): la llevan las keys nuevas y las re-hasheadas. Nula en tests que no la miran. */
    private final String huellaVigente;

    public JdbcApiKeyRepository(JdbcTemplate jdbc) { this(jdbc, null); }

    public JdbcApiKeyRepository(JdbcTemplate jdbc, String huellaVigente) {
        this.jdbc = jdbc;
        this.huellaVigente = huellaVigente;
    }

    @Override public void guardar(ApiKey k) {
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo, activa, created_at, revoked_at, pepper_huella) VALUES (?, ?, ?, ?, ?, COALESCE(?, now()), ?, ?) " +
                        "ON CONFLICT (id) DO UPDATE SET activa = EXCLUDED.activa, revoked_at = EXCLUDED.revoked_at",
                k.id(), k.tenantId(), k.hash(), k.prefijo(), k.activa(), marca(k.creadaEn()), marca(k.revocadaEn()), huellaVigente);
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

    @Override public boolean rehashear(UUID id, String hashAnterior, String hashNuevo) {
        return jdbc.update("UPDATE api_key SET key_hash = ?, pepper_huella = ? WHERE id = ? AND key_hash = ?", hashNuevo, huellaVigente, id, hashAnterior) == 1;
    }
    @Override public int completarHuellas(String huella) {
        return jdbc.update("UPDATE api_key SET pepper_huella = ? WHERE pepper_huella IS NULL", huella);
    }
    @Override public int activasConOtraHuella(String huella) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM api_key WHERE activa AND pepper_huella IS DISTINCT FROM ?", Integer.class, huella);
        return n == null ? 0 : n;
    }
}
