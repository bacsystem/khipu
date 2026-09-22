package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcOutboxRepository implements OutboxRepository {
    private final JdbcTemplate jdbc;

    /** Idempotente: ux_outbox_agregado_accion garantiza una sola fila por (agregado_id, accion). */
    @Override public void programar(UUID tenantId, String accion, UUID agregadoId, Instant cuando) {
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, ?, ?, ?) ON CONFLICT (agregado_id, accion) DO NOTHING",
                tenantId, agregadoId, accion, Timestamp.from(cuando));
    }
    /** Debe ejecutarse dentro de una transacción (UnitOfWork) para que FOR UPDATE SKIP LOCKED tenga efecto. */
    @Override public List<OutboxItem> tomarVencidas(int limite, Duration lock) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("tomarVencidas debe ejecutarse dentro de una transacción (UnitOfWork)");
        List<OutboxItem> items = jdbc.query("""
            SELECT o.id, o.tenant_id, o.agregado_id, o.accion, o.intentos FROM outbox o
            LEFT JOIN documento d ON d.id = o.agregado_id
            WHERE o.siguiente_intento <= now() AND (o.locked_until IS NULL OR o.locked_until < now())
            ORDER BY d.fecha_emision NULLS LAST, o.siguiente_intento FOR UPDATE OF o SKIP LOCKED LIMIT ?
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
