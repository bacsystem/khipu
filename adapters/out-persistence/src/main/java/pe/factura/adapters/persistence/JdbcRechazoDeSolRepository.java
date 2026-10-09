package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.RechazoDeSolRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Ver {@link RechazoDeSolRepository}. El outbox (`JdbcOutboxRepository.tomarVencidas`) es el que deja de tomar los envíos de una empresa marcada. */
@RequiredArgsConstructor
public class JdbcRechazoDeSolRepository implements RechazoDeSolRepository {
    private final JdbcTemplate jdbc;

    @Override public void marcar(UUID tenantId, String motivo, Instant en) {
        String corto = motivo == null ? null : motivo.length() > 500 ? motivo.substring(0, 500) : motivo;
        // Cada rechazo, también el del envío de prueba, vuelve a contar la hora de pausa (sol_sondeo_en); la fecha del primero se conserva.
        jdbc.update("UPDATE tenant SET sol_rechazadas_en = COALESCE(sol_rechazadas_en, ?), sol_sondeo_en = ?, sol_rechazo_motivo = ? WHERE id = ?",
                Timestamp.from(en), Timestamp.from(en), corto, tenantId);
    }

    @Override public Optional<Rechazo> buscar(UUID tenantId) {
        return jdbc.query("SELECT sol_rechazadas_en, sol_rechazo_motivo FROM tenant WHERE id = ? AND sol_rechazadas_en IS NOT NULL",
                (rs, i) -> new Rechazo(rs.getTimestamp(1).toInstant(), rs.getString(2)), tenantId).stream().findFirst();
    }

    @Override public boolean levantar(UUID tenantId, Instant ahora) {
        boolean estaba = jdbc.update("UPDATE tenant SET sol_rechazadas_en = NULL, sol_rechazo_motivo = NULL, sol_sondeo_en = NULL WHERE id = ? AND sol_rechazadas_en IS NOT NULL", tenantId) == 1;
        // Los envíos que esperaban su reintento (hasta horas, por el backoff) se reintentan ya: el motivo por el que fallaban se corrigió.
        if (estaba) jdbc.update("UPDATE outbox SET siguiente_intento = ? WHERE tenant_id = ? AND siguiente_intento > ?", Timestamp.from(ahora), tenantId, Timestamp.from(ahora));
        return estaba;
    }
}
