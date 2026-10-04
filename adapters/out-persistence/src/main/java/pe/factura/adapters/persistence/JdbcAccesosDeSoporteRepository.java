package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.AccesosDeSoporteRepository;

import java.util.List;
import java.util.UUID;

/**
 * Las sesiones de soporte (#184) que la bitácora (#178) tiene de una cuenta. Solo lee: la bitácora se escribe en la misma transacción que la acción. Usa el
 * índice de {@code (cuenta_id, ocurrido_en DESC)}, el mismo que el detalle de la cuenta en el backoffice (#181).
 */
@RequiredArgsConstructor
public class JdbcAccesosDeSoporteRepository implements AccesosDeSoporteRepository {
    private final JdbcTemplate jdbc;

    @Override public List<Registro> deLaCuenta(UUID cuentaId, int limite) {
        return jdbc.query("""
                SELECT ocurrido_en, detalle FROM auditoria_admin
                WHERE cuenta_id = ? AND accion = 'IMPERSONAR_USUARIO'
                ORDER BY ocurrido_en DESC, id LIMIT ?
                """, (rs, i) -> new Registro(rs.getTimestamp("ocurrido_en").toInstant(), rs.getString("detalle")), cuentaId, limite);
    }
}
