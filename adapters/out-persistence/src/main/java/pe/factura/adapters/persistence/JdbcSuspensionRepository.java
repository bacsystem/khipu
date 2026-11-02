package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SuspensionRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Suspensión de cuentas (#182). Las dos lecturas corren en cada petición autenticada: son búsquedas por clave primaria. Suspender y
 * reactivar son condicionales (solo si el estado era el contrario), así de dos pedidos a la vez solo uno cambia algo.
 */
@RequiredArgsConstructor
public class JdbcSuspensionRepository implements SuspensionRepository {
    private final JdbcTemplate jdbc;

    @Override public boolean cuentaSuspendida(UUID cuentaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM cuenta WHERE id = ? AND suspendida_en IS NOT NULL)", Boolean.class, cuentaId));
    }

    @Override public boolean empresaSuspendida(UUID tenantId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM tenant t JOIN cuenta c ON c.id = t.cuenta_id WHERE t.id = ? AND c.suspendida_en IS NOT NULL)
                """, Boolean.class, tenantId));
    }

    @Override public boolean suspender(UUID cuentaId, Instant cuando) {
        return jdbc.update("UPDATE cuenta SET suspendida_en = ? WHERE id = ? AND suspendida_en IS NULL", Timestamp.from(cuando), cuentaId) == 1;
    }

    @Override public boolean reactivar(UUID cuentaId) {
        return jdbc.update("UPDATE cuenta SET suspendida_en = NULL WHERE id = ? AND suspendida_en IS NOT NULL", cuentaId) == 1;
    }
}
