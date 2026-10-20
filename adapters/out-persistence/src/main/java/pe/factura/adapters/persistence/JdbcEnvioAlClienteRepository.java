package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.factura.application.port.out.EnvioAlClienteRepository;
import pe.factura.domain.documento.EnvioAlCliente;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El envío automático de los comprobantes a sus clientes (#325) en la tabla {@code envio_al_cliente}. El alta la hace {@link JdbcComprobanteRepository} al guardar el paso a aceptado;
 * aquí se toman los pendientes con bloqueo y se anota el resultado, como las entregas de webhooks: cada escritura del resultado exige que el envío siga tomado con la marca con que
 * se tomó, así que una instancia lenta nunca pisa a la que lo volvió a tomar.
 */
@RequiredArgsConstructor
public class JdbcEnvioAlClienteRepository implements EnvioAlClienteRepository {
    private final JdbcTemplate jdbc;

    @Override public boolean automatico(UUID tenantId) {
        return jdbc.query("SELECT envio_automatico_al_cliente FROM tenant WHERE id = ?", (rs, i) -> rs.getBoolean(1), tenantId).stream().findFirst().orElse(false);
    }

    @Override public void fijarAutomatico(UUID tenantId, boolean automatico) {
        jdbc.update("UPDATE tenant SET envio_automatico_al_cliente = ? WHERE id = ?", automatico, tenantId);
    }

    @Override public List<Tomado> tomarVencidos(int limite, Duration bloqueo) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("tomarVencidos debe ejecutarse dentro de una transacción (UnitOfWork)");
        List<Tomado> tomados = jdbc.query("""
                SELECT tenant_id, documento_id, destinatario, intentos FROM envio_al_cliente
                WHERE estado = 'PENDIENTE' AND siguiente_intento <= now() AND (locked_until IS NULL OR locked_until < now())
                ORDER BY siguiente_intento, created_at FOR UPDATE SKIP LOCKED LIMIT ?
                """, (rs, i) -> new Tomado(rs.getObject("tenant_id", UUID.class), rs.getObject("documento_id", UUID.class), rs.getString("destinatario"), rs.getInt("intentos"), null), limite);
        // Postgres guarda microsegundos: la marca se redondea acá para que la comparación por igualdad de las escrituras del resultado acierte.
        Instant marca = Instant.now().plus(bloqueo).truncatedTo(ChronoUnit.MICROS);
        List<Tomado> conMarca = new ArrayList<>(tomados.size());
        for (Tomado t : tomados) {
            jdbc.update("UPDATE envio_al_cliente SET locked_until = ? WHERE documento_id = ?", Timestamp.from(marca), t.documentoId());
            conMarca.add(new Tomado(t.tenantId(), t.documentoId(), t.destinatario(), t.intentos(), marca));
        }
        return conMarca;
    }

    @Override public boolean registrarEnvio(Tomado t, Instant ahora) {
        Timestamp en = Timestamp.from(ahora);
        return jdbc.update("""
                UPDATE envio_al_cliente SET estado = 'ENVIADO', intentos = intentos + 1, ultimo_error = NULL, ultimo_intento_en = ?, enviado_en = ?, locked_until = NULL
                WHERE documento_id = ? AND estado = 'PENDIENTE' AND locked_until = ?
                """, en, en, t.documentoId(), Timestamp.from(t.bloqueo())) > 0;
    }

    @Override public boolean registrarFallo(Tomado t, String error, Instant ahora, Instant siguiente) {
        return jdbc.update("""
                UPDATE envio_al_cliente SET estado = ?, intentos = intentos + 1, ultimo_error = ?, ultimo_intento_en = ?, siguiente_intento = coalesce(?, siguiente_intento), locked_until = NULL
                WHERE documento_id = ? AND estado = 'PENDIENTE' AND locked_until = ?
                """, siguiente == null ? EnvioAlCliente.Estado.FALLIDO.name() : EnvioAlCliente.Estado.PENDIENTE.name(), recortar(error), Timestamp.from(ahora),
                siguiente == null ? null : Timestamp.from(siguiente), t.documentoId(), Timestamp.from(t.bloqueo())) > 0;
    }

    @Override public boolean omitir(Tomado t, String motivo, Instant ahora) {
        // No cuenta como intento: no se intentó mandar.
        return jdbc.update("""
                UPDATE envio_al_cliente SET estado = 'OMITIDO', ultimo_error = ?, ultimo_intento_en = ?, locked_until = NULL
                WHERE documento_id = ? AND estado = 'PENDIENTE' AND locked_until = ?
                """, recortar(motivo), Timestamp.from(ahora), t.documentoId(), Timestamp.from(t.bloqueo())) > 0;
    }

    @Override public Optional<EnvioAlCliente> deComprobante(UUID tenantId, UUID documentoId) {
        return jdbc.query("""
                SELECT documento_id, destinatario, estado, intentos, ultimo_error, siguiente_intento, enviado_en, created_at FROM envio_al_cliente
                WHERE documento_id = ? AND tenant_id = ?
                """, (rs, i) -> new EnvioAlCliente(rs.getObject("documento_id", UUID.class), rs.getString("destinatario"), EnvioAlCliente.Estado.valueOf(rs.getString("estado")),
                rs.getInt("intentos"), rs.getString("ultimo_error"), instante(rs.getTimestamp("siguiente_intento")), instante(rs.getTimestamp("enviado_en")),
                instante(rs.getTimestamp("created_at"))), documentoId, tenantId).stream().findFirst();
    }

    private static String recortar(String s) { return s == null || s.length() <= 300 ? s : s.substring(0, 300); }

    private static Instant instante(Timestamp t) { return t == null ? null : t.toInstant(); }
}
