package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class JdbcOutboxRepository implements OutboxRepository {
    private final JdbcTemplate jdbc;
    public JdbcOutboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void programar(UUID tenantId, String accion, UUID agregadoId, Instant cuando) {
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, ?, ?, ?)", tenantId, agregadoId, accion, Timestamp.from(cuando));
    }
    /** Debe ejecutarse dentro de una transacción (UnitOfWork) para que FOR UPDATE SKIP LOCKED tenga efecto. */
    @Override public List<OutboxItem> tomarVencidas(int limite, Duration lock) {
        List<OutboxItem> items = jdbc.query("""
            SELECT id, tenant_id, agregado_id, accion, intentos FROM outbox
            WHERE siguiente_intento <= now() AND (locked_until IS NULL OR locked_until < now())
            ORDER BY siguiente_intento FOR UPDATE SKIP LOCKED LIMIT ?
            """, (rs, i) -> new OutboxItem(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("agregado_id", UUID.class), rs.getString("accion"), rs.getInt("intentos")), limite);
        for (OutboxItem it : items)
            jdbc.update("UPDATE outbox SET locked_until = ? WHERE id = ?", Timestamp.from(Instant.now().plus(lock)), it.id());
        return items;
    }
    @Override public void reprogramar(UUID id, Instant cuando, String error) {
        jdbc.update("UPDATE outbox SET intentos = intentos + 1, siguiente_intento = ?, locked_until = NULL, ultimo_error = ? WHERE id = ?", Timestamp.from(cuando), error, id);
    }
    @Override public void completar(UUID id) { jdbc.update("DELETE FROM outbox WHERE id = ?", id); }
}
