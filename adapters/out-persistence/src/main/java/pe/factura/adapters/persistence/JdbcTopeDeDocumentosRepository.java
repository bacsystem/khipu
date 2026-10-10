package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.factura.application.port.out.TopeDeDocumentosRepository;
import pe.factura.domain.documento.EstadoDocumento;

import java.sql.Date;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lo que ocupa el tope del plan (#18). Los estados salen de {@link EstadoDocumento#ocupaElTope()}, no de un texto copiado aquí. El candado es consultivo (por cuenta,
 * hasta el fin de la transacción) y no un {@code FOR UPDATE} sobre la fila de la cuenta: así no frena a quien edita la cuenta ni da de alta una empresa en ella.
 */
@RequiredArgsConstructor
public class JdbcTopeDeDocumentosRepository implements TopeDeDocumentosRepository {
    private final JdbcTemplate jdbc;

    /** Nombres de un enum, nunca texto de fuera: no hay inyección posible al armarlo. */
    static final String ESTADOS_QUE_OCUPAN = Arrays.stream(EstadoDocumento.values()).filter(EstadoDocumento::ocupaElTope)
            .map(e -> "'" + e.name() + "'").collect(Collectors.joining(", "));

    @Override public long ocupadosBloqueando(UUID cuentaId, YearMonth mes) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("ocupadosBloqueando debe ejecutarse dentro de la transacción que emite (UnitOfWork)");
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, "tope-del-plan:" + cuentaId);
        return jdbc.queryForObject("""
                SELECT count(*) FROM documento d JOIN tenant t ON t.id = d.tenant_id
                WHERE t.cuenta_id = ? AND d.estado IN (%s) AND d.fecha_emision >= ? AND d.fecha_emision < ?
                """.formatted(ESTADOS_QUE_OCUPAN), Long.class, cuentaId, Date.valueOf(mes.atDay(1)), Date.valueOf(mes.plusMonths(1).atDay(1)));
    }
}
