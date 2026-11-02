package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.RechazoDeCredencialesGreRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Las credenciales de la GRE que SUNAT rechazó, por empresa (#39, H3): tabla {@code gre_rechazo_credenciales} de V69. */
@RequiredArgsConstructor
public class JdbcRechazoDeCredencialesGreRepository implements RechazoDeCredencialesGreRepository {
    private final JdbcTemplate jdbc;

    @Override public void marcar(UUID tenantId, String huella, String motivo, Instant en) {
        jdbc.update("""
            INSERT INTO gre_rechazo_credenciales (tenant_id, huella, motivo, rechazadas_en) VALUES (?, ?, ?, ?)
            ON CONFLICT (tenant_id) DO UPDATE SET huella = EXCLUDED.huella, motivo = EXCLUDED.motivo, rechazadas_en = EXCLUDED.rechazadas_en""",
                tenantId, huella, motivo, Timestamp.from(en));
    }

    @Override public Optional<Rechazo> buscar(UUID tenantId) {
        return jdbc.query("SELECT huella, motivo, rechazadas_en FROM gre_rechazo_credenciales WHERE tenant_id = ?",
                (rs, i) -> new Rechazo(rs.getString("huella"), rs.getString("motivo"), rs.getTimestamp("rechazadas_en").toInstant()), tenantId).stream().findFirst();
    }

    @Override public void levantar(UUID tenantId) { jdbc.update("DELETE FROM gre_rechazo_credenciales WHERE tenant_id = ?", tenantId); }
}
