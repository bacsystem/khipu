package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.EquipoRepository;
import pe.factura.domain.cuenta.Equipo;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** #21: los usuarios de una cuenta. */
@RequiredArgsConstructor
public class JdbcEquipoRepository implements EquipoRepository {
    private static final String SELECT = "SELECT id, cuenta_id, email, password_hash, rol, activo, correo_verificado_at FROM usuario WHERE cuenta_id = ? ORDER BY created_at, email";
    private final JdbcTemplate jdbc;

    @Override public Equipo deLaCuenta(UUID cuentaId) {
        return new Equipo(cuentaId, leer(SELECT, cuentaId));
    }

    /**
     * {@code FOR UPDATE} sobre las filas de la cuenta: un segundo cambio espera a que termine el primero y lee lo que dejó. La cuenta también se bloquea, para que una
     * invitación concurrente (que no cambia filas de usuario) también espere.
     */
    @Override public Equipo deLaCuentaParaCambiar(UUID cuentaId) {
        jdbc.query("SELECT id FROM cuenta WHERE id = ? FOR UPDATE", rs -> {}, cuentaId);
        return new Equipo(cuentaId, leer(SELECT + " FOR UPDATE", cuentaId));
    }

    private List<Usuario> leer(String sql, UUID cuentaId) {
        return jdbc.query(sql, (rs, i) -> {
            Timestamp verificado = rs.getTimestamp("correo_verificado_at");
            return new Usuario(rs.getObject("id", UUID.class), rs.getObject("cuenta_id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
                    Rol.valueOf(rs.getString("rol")), rs.getBoolean("activo"), verificado == null ? null : verificado.toInstant());
        }, cuentaId);
    }
}
