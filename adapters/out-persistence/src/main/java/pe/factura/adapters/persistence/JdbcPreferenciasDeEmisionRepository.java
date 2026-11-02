package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.PreferenciasDeEmisionRepository;

import java.util.UUID;

/** Las preferencias de emisión de la empresa en sus columnas de {@code tenant}: se leen y se escriben solas, sin tocar el resto de la fila. */
@RequiredArgsConstructor
public class JdbcPreferenciasDeEmisionRepository implements PreferenciasDeEmisionRepository {
    private final JdbcTemplate jdbc;

    @Override public boolean opcionesAvanzadas(UUID tenantId) {
        return jdbc.query("SELECT opciones_avanzadas_emision FROM tenant WHERE id = ?", (rs, i) -> rs.getBoolean(1), tenantId).stream().findFirst().orElse(false);
    }

    @Override public boolean fijarOpcionesAvanzadas(UUID tenantId, boolean opcionesAvanzadas) {
        return jdbc.update("UPDATE tenant SET opciones_avanzadas_emision = ? WHERE id = ?", opcionesAvanzadas, tenantId) > 0;
    }
}
