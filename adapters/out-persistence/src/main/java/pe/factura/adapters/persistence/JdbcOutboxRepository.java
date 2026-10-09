package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
        List<OutboxItem> items = new ArrayList<>(jdbc.query("""
            SELECT o.id, o.tenant_id, o.agregado_id, o.accion, o.intentos FROM outbox o
            LEFT JOIN documento d ON d.id = o.agregado_id
            WHERE o.siguiente_intento <= now() AND (o.locked_until IS NULL OR o.locked_until < now())
              -- #107: con las credenciales SOL rechazadas, los envíos de la empresa esperan a que se corrijan (RechazoDeSolRepository).
              AND NOT EXISTS (SELECT 1 FROM tenant t WHERE t.id = o.tenant_id AND t.sol_rechazadas_en IS NOT NULL)
            ORDER BY d.fecha_emision NULLS LAST, o.siguiente_intento FOR UPDATE OF o SKIP LOCKED LIMIT ?
            """, FILA, limite));
        if (items.size() < limite) items.addAll(envioDePrueba(limite - items.size()));
        for (OutboxItem it : items)
            jdbc.update("UPDATE outbox SET locked_until = ? WHERE id = ?", Timestamp.from(Instant.now().plus(lock)), it.id());
        return items;
    }
    private static final RowMapper<OutboxItem> FILA = (rs, i) -> new OutboxItem(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
            rs.getObject("agregado_id", UUID.class), rs.getString("accion"), rs.getInt("intentos"));

    /** Cuánto se espera, desde el último rechazo de las credenciales SOL (o el último envío de prueba), para volver a probar con un envío (270-H1). */
    static final Duration PAUSA_POR_RECHAZO_DE_SOL = Duration.ofHours(1);

    /**
     * Un envío por empresa con las credenciales rechazadas hace más de {@link #PAUSA_POR_RECHAZO_DE_SOL}. Tres 401 seguidos también pueden ser del frontal
     * de SUNAT, no de las credenciales: sin esto, un rechazo pasajero dejaba los envíos de la empresa en pausa hasta que alguien volviera a guardarlas. Si
     * SUNAT vuelve a rechazar, `RechazoDeSolRepository.marcar` renueva la pausa; si acepta, levanta la marca y salen los demás. Marcar el sondeo y tomar el
     * envío van en la misma transacción: dos workers no prueban dos veces la misma empresa.
     */
    private List<OutboxItem> envioDePrueba(int cupo) {
        List<UUID> empresas = jdbc.queryForList("""
            UPDATE tenant t SET sol_sondeo_en = now()
            WHERE t.id IN (
              SELECT t2.id FROM tenant t2
              WHERE t2.sol_rechazadas_en IS NOT NULL AND COALESCE(t2.sol_sondeo_en, t2.sol_rechazadas_en) <= now() - make_interval(secs => ?)
                AND EXISTS (SELECT 1 FROM outbox o WHERE o.tenant_id = t2.id AND o.siguiente_intento <= now() AND (o.locked_until IS NULL OR o.locked_until < now()))
              ORDER BY t2.sol_rechazadas_en LIMIT ? FOR UPDATE OF t2 SKIP LOCKED)
            RETURNING t.id
            """, UUID.class, PAUSA_POR_RECHAZO_DE_SOL.toSeconds(), cupo);
        List<OutboxItem> prueba = new ArrayList<>();
        for (UUID empresa : empresas)
            prueba.addAll(jdbc.query("""
                SELECT o.id, o.tenant_id, o.agregado_id, o.accion, o.intentos FROM outbox o
                LEFT JOIN documento d ON d.id = o.agregado_id
                WHERE o.tenant_id = ? AND o.siguiente_intento <= now() AND (o.locked_until IS NULL OR o.locked_until < now())
                ORDER BY d.fecha_emision NULLS LAST, o.siguiente_intento FOR UPDATE OF o SKIP LOCKED LIMIT 1
                """, FILA, empresa));
        return prueba;
    }

    @Override public void reprogramar(UUID id, Instant cuando, String error) {
        jdbc.update("UPDATE outbox SET intentos = intentos + 1, siguiente_intento = ?, locked_until = NULL, ultimo_error = ? WHERE id = ?", Timestamp.from(cuando), error, id);
    }
    @Override public void completar(UUID id) { jdbc.update("DELETE FROM outbox WHERE id = ?", id); }
    @Override public void completarPorAgregado(UUID agregadoId, String accion) { jdbc.update("DELETE FROM outbox WHERE agregado_id = ? AND accion = ?", agregadoId, accion); }
}
