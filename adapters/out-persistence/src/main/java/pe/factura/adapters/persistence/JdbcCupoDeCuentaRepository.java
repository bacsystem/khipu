package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.factura.application.port.out.CupoDeCuentaRepository;
import pe.factura.domain.plan.RecursoDelPlan;
import pe.factura.domain.plan.UsoDeRecursos;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que ocupa el cupo del plan de una cuenta (#498). El candado es consultivo, uno por cuenta y recurso, como el del tope de documentos: no frena a quien edita la cuenta
 * ni a quien crea otro recurso. Una invitación vencida deja de ocupar su lugar aunque siga como {@code PENDIENTE} en la tabla.
 */
@RequiredArgsConstructor
public class JdbcCupoDeCuentaRepository implements CupoDeCuentaRepository {
    private final JdbcTemplate jdbc;

    @Override public void bloquear(UUID cuentaId, RecursoDelPlan recurso) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("bloquear debe ejecutarse dentro de la transacción que crea el recurso (UnitOfWork)");
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, "cupo-del-plan:" + recurso + ":" + cuentaId);
    }

    @Override public long ocupados(UUID cuentaId, RecursoDelPlan recurso, Instant ahora) {
        return switch (recurso) {
            case API_KEYS -> contar("SELECT count(*) FROM api_key a JOIN tenant t ON t.id = a.tenant_id WHERE t.cuenta_id = ? AND a.activa", cuentaId);
            case RUCS -> contar("SELECT count(*) FROM tenant WHERE cuenta_id = ?", cuentaId);
            case USUARIOS -> contar("""
                    SELECT (SELECT count(*) FROM usuario WHERE cuenta_id = ? AND activo)
                         + (SELECT count(*) FROM invitacion WHERE cuenta_id = ? AND estado = 'PENDIENTE' AND expira_en > ?)
                    """, cuentaId, cuentaId, Timestamp.from(ahora));
        };
    }

    @Override public UsoDeRecursos uso(UUID cuentaId, Instant ahora) {
        return new UsoDeRecursos(ocupados(cuentaId, RecursoDelPlan.API_KEYS, ahora), ocupados(cuentaId, RecursoDelPlan.USUARIOS, ahora), ocupados(cuentaId, RecursoDelPlan.RUCS, ahora));
    }

    private long contar(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
