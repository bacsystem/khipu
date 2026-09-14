package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.UsuarioRepository;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcUsuarioRepository implements UsuarioRepository {
    private static final String COLS = "id, cuenta_id, email, password_hash, rol, activo";
    private static final RowMapper<Usuario> MAPPER = (rs, i) -> new Usuario(rs.getObject("id", UUID.class), rs.getObject("cuenta_id", UUID.class),
            rs.getString("email"), rs.getString("password_hash"), Rol.valueOf(rs.getString("rol")), rs.getBoolean("activo"));
    private final JdbcTemplate jdbc;

    @Override public void guardar(Usuario u) {
        jdbc.update("""
            INSERT INTO usuario (id, cuenta_id, email, password_hash, rol, activo) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, password_hash = EXCLUDED.password_hash, rol = EXCLUDED.rol, activo = EXCLUDED.activo
            """, u.id(), u.cuentaId(), u.email(), u.passwordHash(), u.rol().name(), u.activo());
    }
    @Override public Optional<Usuario> buscar(UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM usuario WHERE id = ?", MAPPER, id).stream().findFirst();
    }
    @Override public Optional<Usuario> buscarPorEmail(String email) {
        return jdbc.query("SELECT " + COLS + " FROM usuario WHERE email = ?", MAPPER, email).stream().findFirst();
    }
}
