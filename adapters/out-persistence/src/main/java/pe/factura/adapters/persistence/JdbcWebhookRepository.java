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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los endpoints de webhooks y sus entregas (#19). Tomar las entregas usa {@code FOR UPDATE SKIP LOCKED} más un bloqueo por tiempo, como el outbox de envíos a SUNAT: dos
 * instancias del backend no mandan la misma entrega. Solo se toman las de endpoints activos, así que un endpoint apagado o pausado acumula sus entregas pendientes y las
 * manda al reactivarse.
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

    @Override public void crear(EndpointWebhook e, byte[] secretoCifrado) {
        jdbc.update("INSERT INTO webhook_endpoint (id, tenant_id, url, descripcion, eventos, secreto_enc, activo, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                e.id(), e.tenantId(), e.url(), e.descripcion(), String.join(",", e.eventos()), secretoCifrado, e.activo(), Timestamp.from(e.creadoEn()));
    }

    @Override public Optional<EndpointWebhook> buscar(UUID tenantId, UUID id) {
        return jdbc.query("SELECT * FROM webhook_endpoint WHERE id = ? AND tenant_id = ?", ENDPOINT, id, tenantId).stream().findFirst();
    }

    @Override public List<EndpointWebhook> listar(UUID tenantId) {
        return jdbc.query("SELECT * FROM webhook_endpoint WHERE tenant_id = ? ORDER BY created_at, id", ENDPOINT, tenantId);
    }

    @Override public void actualizar(EndpointWebhook e) {
        jdbc.update("UPDATE webhook_endpoint SET url = ?, descripcion = ?, eventos = ?, activo = ?, pausado_motivo = ?, fallos_consecutivos = ?, updated_at = now() WHERE id = ? AND tenant_id = ?",
                e.url(), e.descripcion(), String.join(",", e.eventos()), e.activo(), e.pausadoMotivo(), e.fallosConsecutivos(), e.id(), e.tenantId());
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

    @Override public List<Pendiente> tomarVencidas(int limite, Duration bloqueo) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("tomarVencidas debe ejecutarse dentro de una transacción (UnitOfWork)");
        List<Pendiente> tomadas = jdbc.query("""
                SELECT %s, e.url, e.secreto_enc FROM webhook_entrega w JOIN webhook_endpoint e ON e.id = w.endpoint_id
                WHERE w.estado = 'PENDIENTE' AND w.siguiente_intento <= now() AND (w.locked_until IS NULL OR w.locked_until < now()) AND e.activo
                ORDER BY w.siguiente_intento FOR UPDATE OF w SKIP LOCKED LIMIT ?
                """.formatted(COLUMNAS_ENTREGA.replaceAll("(\\w+)", "w.$1")),
                (rs, i) -> new Pendiente(ENTREGA.mapRow(rs, i), rs.getString("url"), rs.getBytes("secreto_enc")), limite);
        for (Pendiente p : tomadas)
            jdbc.update("UPDATE webhook_entrega SET locked_until = ? WHERE id = ?", Timestamp.from(Instant.now().plus(bloqueo)), p.entrega().id());
        return tomadas;
    }

    @Override public void registrarExito(UUID entregaId, UUID endpointId, int http, Instant ahora) {
        Timestamp t = Timestamp.from(ahora);
        jdbc.update("UPDATE webhook_entrega SET estado = 'EXITOSA', intentos = intentos + 1, ultimo_http = ?, ultimo_error = NULL, ultimo_intento_en = ?, entregada_en = ?, locked_until = NULL WHERE id = ?",
                http, t, t, entregaId);
        jdbc.update("UPDATE webhook_endpoint SET fallos_consecutivos = 0 WHERE id = ? AND fallos_consecutivos <> 0", endpointId);
    }

    @Override public int registrarFallo(UUID entregaId, UUID endpointId, Integer http, String error, Instant ahora, Instant siguiente) {
        jdbc.update("UPDATE webhook_entrega SET estado = ?, intentos = intentos + 1, ultimo_http = ?, ultimo_error = ?, ultimo_intento_en = ?, siguiente_intento = coalesce(?, siguiente_intento), locked_until = NULL WHERE id = ?",
                siguiente == null ? Estado.FALLIDA.name() : Estado.PENDIENTE.name(), http, recortar(error), Timestamp.from(ahora), siguiente == null ? null : Timestamp.from(siguiente), entregaId);
        return jdbc.queryForObject("UPDATE webhook_endpoint SET fallos_consecutivos = fallos_consecutivos + 1 WHERE id = ? RETURNING fallos_consecutivos", Integer.class, endpointId);
    }

    @Override public void pausar(UUID endpointId, String motivo) {
        jdbc.update("UPDATE webhook_endpoint SET activo = FALSE, pausado_motivo = ?, updated_at = now() WHERE id = ?", recortar(motivo), endpointId);
    }

    @Override public long limpiar(Instant antesDe) {
        return jdbc.update("DELETE FROM webhook_entrega WHERE estado <> 'PENDIENTE' AND created_at < ?", Timestamp.from(antesDe));
    }

    private static String recortar(String s) { return s == null || s.length() <= 300 ? s : s.substring(0, 300); }

    private static Instant instante(Timestamp t) { return t == null ? null : t.toInstant(); }
}
