package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.WebhookRepository;
import pe.factura.domain.webhook.EndpointWebhook;
import pe.factura.domain.webhook.EntregaWebhook;
import pe.factura.domain.webhook.EntregaWebhook.Estado;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los endpoints de webhooks y sus entregas (#19). Tomar las entregas usa {@code FOR UPDATE SKIP LOCKED} más una marca de bloqueo por tiempo ({@code locked_until}), y la marca
 * es el testigo de las escrituras del resultado: una instancia que tardó más que el bloqueo no pisa a la que volvió a tomar la entrega. Solo se toman las de endpoints
 * activos; un endpoint pausado por khipu sigue recibiendo en cola los eventos nuevos (no se pierden) y los manda al reactivarse.
 */
@RequiredArgsConstructor
public class JdbcWebhookRepository implements WebhookRepository {
    private final JdbcTemplate jdbc;

    private static final RowMapper<EndpointWebhook> ENDPOINT = (rs, i) -> new EndpointWebhook(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
            rs.getString("url"), rs.getString("descripcion"), List.of(rs.getString("eventos").split(",")), rs.getBoolean("activo"), rs.getString("pausado_motivo"),
            rs.getInt("fallos_consecutivos"), rs.getTimestamp("created_at").toInstant());

    private static final String COLUMNAS_ENTREGA = "id, endpoint_id, tenant_id, evento, documento_id, payload, estado, intentos, siguiente_intento, ultimo_http, ultimo_error, ultimo_intento_en, entregada_en, created_at";

    private static final RowMapper<EntregaWebhook> ENTREGA = (rs, i) -> new EntregaWebhook(rs.getObject("id", UUID.class), rs.getObject("endpoint_id", UUID.class),
            rs.getObject("tenant_id", UUID.class), rs.getString("evento"), rs.getObject("documento_id", UUID.class), rs.getString("payload"), Estado.valueOf(rs.getString("estado")),
            rs.getInt("intentos"), instante(rs.getTimestamp("siguiente_intento")), (Integer) rs.getObject("ultimo_http"), rs.getString("ultimo_error"),
            instante(rs.getTimestamp("ultimo_intento_en")), instante(rs.getTimestamp("entregada_en")), instante(rs.getTimestamp("created_at")));

    @Override public boolean crearSiHayCupo(EndpointWebhook e, byte[] secretoCifrado, int maximo) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("crearSiHayCupo debe ejecutarse dentro de una transacción (UnitOfWork)");
        // Las altas de una misma empresa se serializan: sin el candado, dos pedidos a la vez leen «4» y los dos crean el quinto y el sexto.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "webhook_endpoint:" + e.tenantId());
        Integer existentes = jdbc.queryForObject("SELECT count(*) FROM webhook_endpoint WHERE tenant_id = ?", Integer.class, e.tenantId());
        if (existentes != null && existentes >= maximo) return false;
        jdbc.update("INSERT INTO webhook_endpoint (id, tenant_id, url, descripcion, eventos, secreto_enc, activo, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                e.id(), e.tenantId(), e.url(), e.descripcion(), String.join(",", e.eventos()), secretoCifrado, e.activo(), Timestamp.from(e.creadoEn()));
        return true;
    }

    @Override public Optional<EndpointWebhook> buscar(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM webhook_endpoint WHERE id = ? AND tenant_id = ?", ENDPOINT, id, tenantId).stream().findFirst();
    }

    @Override public List<EndpointWebhook> listar(UUID tenantId) {
        return jdbc.query("SELECT * FROM webhook_endpoint WHERE tenant_id = ? ORDER BY created_at, id", ENDPOINT, tenantId);
    }

    @Override public void actualizarDatos(UUID tenantId, UUID id, String url, String descripcion, List<String> eventos) {
        jdbc.update("UPDATE webhook_endpoint SET url = ?, descripcion = ?, eventos = ?, updated_at = now() WHERE id = ? AND tenant_id = ?",
                url, descripcion, String.join(",", eventos), id, tenantId);
    }

    @Override public void fijarActivo(UUID tenantId, UUID id, boolean activo) {
        if (!activo) {
            jdbc.update("UPDATE webhook_endpoint SET activo = FALSE, pausado_motivo = NULL, updated_at = now() WHERE id = ? AND tenant_id = ?", id, tenantId);
            return;
        }
        int filas = jdbc.update("UPDATE webhook_endpoint SET activo = TRUE, pausado_motivo = NULL, fallos_consecutivos = 0, updated_at = now() WHERE id = ? AND tenant_id = ?", id, tenantId);
        // Lo que quedó esperando (en cola durante la pausa, o con su reintento a horas de distancia) sale ya.
        if (filas > 0) jdbc.update("UPDATE webhook_entrega SET siguiente_intento = now(), locked_until = NULL WHERE endpoint_id = ? AND estado = 'PENDIENTE'", id);
    }

    @Override public void guardarSecreto(UUID tenantId, UUID id, byte[] secretoCifrado) {
        jdbc.update("UPDATE webhook_endpoint SET secreto_enc = ?, updated_at = now() WHERE id = ? AND tenant_id = ?", secretoCifrado, id, tenantId);
    }

    @Override public Optional<byte[]> secreto(UUID tenantId, UUID id) {
        return jdbc.query("SELECT secreto_enc FROM webhook_endpoint WHERE id = ? AND tenant_id = ?", (rs, i) -> rs.getBytes("secreto_enc"), id, tenantId).stream().findFirst();
    }

    @Override public boolean eliminar(UUID tenantId, UUID id) {
        return jdbc.update("DELETE FROM webhook_endpoint WHERE id = ? AND tenant_id = ?", id, tenantId) > 0;
    }

    @Override public UUID encolar(UUID tenantId, UUID endpointId, String evento, UUID documentoId, String payload) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO webhook_entrega (id, endpoint_id, tenant_id, evento, documento_id, payload) VALUES (?, ?, ?, ?, ?, ?)", id, endpointId, tenantId, evento, documentoId, payload);
        return id;
    }

    @Override public List<EntregaWebhook> entregas(UUID tenantId, UUID endpointId, Estado estado, int pagina, int porPagina) {
        List<Object> args = new ArrayList<>(Arrays.asList(tenantId, endpointId));
        String filtro = filtroEstado(estado, args);
        args.add(porPagina);
        args.add((pagina - 1) * porPagina);
        return jdbc.query("SELECT " + COLUMNAS_ENTREGA + " FROM webhook_entrega WHERE tenant_id = ? AND endpoint_id = ?" + filtro + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
                ENTREGA, args.toArray());
    }

    @Override public long contarEntregas(UUID tenantId, UUID endpointId, Estado estado) {
        List<Object> args = new ArrayList<>(Arrays.asList(tenantId, endpointId));
        String filtro = filtroEstado(estado, args);
        return jdbc.queryForObject("SELECT count(*) FROM webhook_entrega WHERE tenant_id = ? AND endpoint_id = ?" + filtro, Long.class, args.toArray());
    }

    private static String filtroEstado(Estado estado, List<Object> args) {
        if (estado == null) return "";
        args.add(estado.name());
        return " AND estado = ?";
    }

    @Override public Optional<EntregaWebhook> buscarEntrega(UUID tenantId, UUID id) {
        return jdbc.query("SELECT " + COLUMNAS_ENTREGA + " FROM webhook_entrega WHERE id = ? AND tenant_id = ?", ENTREGA, id, tenantId).stream().findFirst();
    }

    @Override public void reencolar(UUID tenantId, UUID id) {
        jdbc.update("UPDATE webhook_entrega SET estado = 'PENDIENTE', intentos = 0, siguiente_intento = now(), locked_until = NULL, ultimo_error = NULL WHERE id = ? AND tenant_id = ?", id, tenantId);
    }

    @Override public List<Pendiente> tomarVencidas(int limite, int maximoPorEndpoint, Duration bloqueo) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("tomarVencidas debe ejecutarse dentro de una transacción (UnitOfWork)");
        // El tope por endpoint se calcula en una subconsulta con ventana: PostgreSQL no permite FOR UPDATE en una consulta que la usa.
        List<Pendiente> tomadas = jdbc.query("""
                SELECT %s, e.url, e.secreto_enc FROM webhook_entrega w JOIN webhook_endpoint e ON e.id = w.endpoint_id
                WHERE w.id IN (
                    SELECT id FROM (
                        SELECT x.id, row_number() OVER (PARTITION BY x.endpoint_id ORDER BY x.siguiente_intento, x.created_at) AS n
                        FROM webhook_entrega x JOIN webhook_endpoint y ON y.id = x.endpoint_id
                        WHERE x.estado = 'PENDIENTE' AND x.siguiente_intento <= now() AND (x.locked_until IS NULL OR x.locked_until < now()) AND y.activo
                    ) t WHERE t.n <= ?)
                  AND w.estado = 'PENDIENTE' AND (w.locked_until IS NULL OR w.locked_until < now())
                ORDER BY w.siguiente_intento, w.created_at FOR UPDATE OF w SKIP LOCKED LIMIT ?
                """.formatted(COLUMNAS_ENTREGA.replaceAll("(\\w+)", "w.$1")),
                (rs, i) -> new Pendiente(ENTREGA.mapRow(rs, i), rs.getString("url"), rs.getBytes("secreto_enc"), null), maximoPorEndpoint, limite);
        // Postgres guarda microsegundos: la marca se redondea acá para que la comparación por igualdad de las escrituras del resultado acierte.
        Instant marca = Instant.now().plus(bloqueo).truncatedTo(ChronoUnit.MICROS);
        List<Pendiente> conMarca = new ArrayList<>(tomadas.size());
        for (Pendiente p : tomadas) {
            jdbc.update("UPDATE webhook_entrega SET locked_until = ? WHERE id = ?", Timestamp.from(marca), p.entrega().id());
            conMarca.add(new Pendiente(p.entrega(), p.url(), p.secretoCifrado(), marca));
        }
        return conMarca;
    }

    @Override public boolean estaActivo(UUID endpointId) {
        return jdbc.query("SELECT activo FROM webhook_endpoint WHERE id = ?", (rs, i) -> rs.getBoolean("activo"), endpointId).stream().findFirst().orElse(false);
    }

    @Override public boolean registrarExito(Pendiente p, int http, Instant ahora) {
        Timestamp t = Timestamp.from(ahora);
        int filas = jdbc.update("UPDATE webhook_entrega SET estado = 'EXITOSA', intentos = intentos + 1, ultimo_http = ?, ultimo_error = NULL, ultimo_intento_en = ?, entregada_en = ?, locked_until = NULL "
                + "WHERE id = ? AND estado = 'PENDIENTE' AND locked_until = ?", http, t, t, p.entrega().id(), Timestamp.from(p.bloqueo()));
        if (filas == 0) return false;
        jdbc.update("UPDATE webhook_endpoint SET fallos_consecutivos = 0 WHERE id = ? AND fallos_consecutivos <> 0", p.entrega().endpointId());
        return true;
    }

    @Override public int registrarFallo(Pendiente p, Integer http, String error, Instant ahora, Instant siguiente) {
        int filas = jdbc.update("UPDATE webhook_entrega SET estado = ?, intentos = intentos + 1, ultimo_http = ?, ultimo_error = ?, ultimo_intento_en = ?, siguiente_intento = coalesce(?, siguiente_intento), locked_until = NULL "
                        + "WHERE id = ? AND estado = 'PENDIENTE' AND locked_until = ?",
                siguiente == null ? Estado.FALLIDA.name() : Estado.PENDIENTE.name(), http, recortar(error), Timestamp.from(ahora), siguiente == null ? null : Timestamp.from(siguiente),
                p.entrega().id(), Timestamp.from(p.bloqueo()));
        if (filas == 0) return -1;
        return jdbc.queryForObject("UPDATE webhook_endpoint SET fallos_consecutivos = fallos_consecutivos + 1 WHERE id = ? RETURNING fallos_consecutivos", Integer.class, p.entrega().endpointId());
    }

    @Override public void liberar(Pendiente p) {
        jdbc.update("UPDATE webhook_entrega SET locked_until = NULL WHERE id = ? AND estado = 'PENDIENTE' AND locked_until = ?", p.entrega().id(), Timestamp.from(p.bloqueo()));
    }

    @Override public boolean pausar(UUID endpointId, String motivo) {
        return jdbc.update("UPDATE webhook_endpoint SET activo = FALSE, pausado_motivo = ?, updated_at = now() WHERE id = ? AND activo", recortar(motivo), endpointId) > 0;
    }

    @Override public long limpiar(Instant antesDe) {
        return jdbc.update("""
                DELETE FROM webhook_entrega w WHERE w.created_at < ?
                  AND (w.estado <> 'PENDIENTE' OR NOT EXISTS (SELECT 1 FROM webhook_endpoint e WHERE e.id = w.endpoint_id AND e.activo))
                """, Timestamp.from(antesDe));
    }

    private static String recortar(String s) { return s == null || s.length() <= 300 ? s : s.substring(0, 300); }

    private static Instant instante(Timestamp t) { return t == null ? null : t.toInstant(); }
}
