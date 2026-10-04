package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.BajaDeCuentaRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Baja lógica de cuentas (#201). Dar de baja y reponer son condicionales (solo si el estado era el contrario), así de dos pedidos a la vez solo uno
 * cambia algo. Solo toca la columna {@code baja_en} de la cuenta: ni sus empresas ni sus comprobantes, y el RUC sigue ocupado.
 */
@RequiredArgsConstructor
public class JdbcBajaDeCuentaRepository implements BajaDeCuentaRepository {
    private final JdbcTemplate jdbc;

    @Override public boolean darDeBaja(UUID cuentaId, Instant cuando) {
        return jdbc.update("UPDATE cuenta SET baja_en = ? WHERE id = ? AND baja_en IS NULL", Timestamp.from(cuando), cuentaId) == 1;
    }

    @Override public boolean reponer(UUID cuentaId) {
        return jdbc.update("UPDATE cuenta SET baja_en = NULL WHERE id = ? AND baja_en IS NOT NULL", cuentaId) == 1;
    }
}
