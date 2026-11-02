package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.UsuarioRepository;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcUsuarioRepository implements UsuarioRepository {
    private static final String COLS = "id, cuenta_id, email, password_hash, rol, activo, correo_verificado_at";
    private static final RowMapper<Usuario> MAPPER = (rs, i) -> {
        Timestamp verificado = rs.getTimestamp("correo_verificado_at");
        return new Usuario(rs.getObject("id", UUID.class), rs.getObject("cuenta_id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
                Rol.valueOf(rs.getString("rol")), rs.getBoolean("activo"), verificado == null ? null : verificado.toInstant());
    };
    private final JdbcTemplate jdbc;

    @Override public void guardar(Usuario u) {
        jdbc.update("""
            INSERT INTO usuario (id, cuenta_id, email, password_hash, rol, activo, correo_verificado_at) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, password_hash = EXCLUDED.password_hash, rol = EXCLUDED.rol, activo = EXCLUDED.activo,
                correo_verificado_at = EXCLUDED.correo_verificado_at
            """, u.id(), u.cuentaId(), u.email(), u.passwordHash(), u.rol().name(), u.activo(),
                u.correoVerificadoEn() == null ? null : Timestamp.from(u.correoVerificadoEn()));
    }
    @Override public Optional<Usuario> buscar(UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM usuario WHERE id = ?", MAPPER, id).stream().findFirst();
    }
    @Override public Optional<Usuario> buscarPorEmail(String email) {
        return jdbc.query("SELECT " + COLS + " FROM usuario WHERE email = ?", MAPPER, email).stream().findFirst();
    }
    /** Solo esa columna, y solo si estaba vacía: no pisa un cambio de contraseña concurrente ni mueve la primera fecha. */
    @Override public void marcarCorreoVerificado(UUID id, java.time.Instant cuando) {
        jdbc.update("UPDATE usuario SET correo_verificado_at = ? WHERE id = ? AND correo_verificado_at IS NULL", Timestamp.from(cuando), id);
    }
    /** H2: solo esas columnas; el rol y si está activo quedan como estén en la base, aunque otro los haya cambiado después de leer al usuario. */
    @Override public void cambiarPassword(UUID id, String passwordHash, java.time.Instant correoVerificadoEn) {
        jdbc.update("UPDATE usuario SET password_hash = ?, correo_verificado_at = COALESCE(correo_verificado_at, ?) WHERE id = ?",
                passwordHash, Timestamp.from(correoVerificadoEn), id);
    }
    @Override public void cambiarRol(UUID id, Rol rol) {
        jdbc.update("UPDATE usuario SET rol = ? WHERE id = ?", rol.name(), id);
    }
    @Override public void cambiarActivo(UUID id, boolean activo) {
        jdbc.update("UPDATE usuario SET activo = ? WHERE id = ?", activo, id);
    }
}
