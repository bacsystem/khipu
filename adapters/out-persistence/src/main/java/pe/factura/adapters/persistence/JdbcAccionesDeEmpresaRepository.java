package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.AccionesDeEmpresaRepository;
import pe.factura.domain.tenant.Entorno;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Acciones del administrador sobre una empresa (#187). Los dos cambios son condicionales (solo si el estado era el esperado) y tocan una sola columna: ni el
 * certificado ni las credenciales SOL cifrados pasan por aquí, a diferencia de {@code JdbcTenantRepository.guardar}, que reescribe la fila entera.
 */
@RequiredArgsConstructor
public class JdbcAccionesDeEmpresaRepository implements AccionesDeEmpresaRepository {
    private final JdbcTemplate jdbc;

    @Override public Optional<Entorno> entornoDe(UUID tenantId) {
        return jdbc.query("SELECT entorno FROM tenant WHERE id = ?", (rs, i) -> Entorno.valueOf(rs.getString("entorno")), tenantId).stream().findFirst();
    }

    @Override public boolean cambiarEntorno(UUID tenantId, Entorno desde, Entorno hacia) {
        return jdbc.update("UPDATE tenant SET entorno = ?, updated_at = now() WHERE id = ? AND entorno = ?", hacia.name(), tenantId, desde.name()) == 1;
    }

    @Override public long enviosPendientes(UUID tenantId) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox WHERE tenant_id = ?", Long.class, tenantId);
    }

    @Override public Optional<ApiKeyDeEmpresa> apiKey(UUID tenantId, UUID apiKeyId) {
        return jdbc.query("SELECT prefijo, activa FROM api_key WHERE id = ? AND tenant_id = ?",
                (rs, i) -> new ApiKeyDeEmpresa(rs.getString("prefijo"), rs.getBoolean("activa")), apiKeyId, tenantId).stream().findFirst();
    }

    @Override public boolean revocarApiKey(UUID tenantId, UUID apiKeyId, Instant cuando) {
        return jdbc.update("UPDATE api_key SET activa = false, revoked_at = ? WHERE id = ? AND tenant_id = ? AND activa", Timestamp.from(cuando), apiKeyId, tenantId) == 1;
    }
}
