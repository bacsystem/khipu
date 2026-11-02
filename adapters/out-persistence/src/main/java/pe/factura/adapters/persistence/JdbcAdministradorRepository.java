package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.AdministradorRepository;
import pe.factura.domain.plataforma.Administrador;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcAdministradorRepository implements AdministradorRepository {
    private static final String COLS = "id, email, password_hash, activo";
    private static final RowMapper<Administrador> MAPPER = (rs, i) -> new Administrador(
            rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"), rs.getBoolean("activo"));
    private final JdbcTemplate jdbc;

    @Override public void guardar(Administrador a) {
        jdbc.update("""
            INSERT INTO administrador (id, email, password_hash, activo) VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email, password_hash = EXCLUDED.password_hash, activo = EXCLUDED.activo
            """, a.id(), a.email(), a.passwordHash(), a.activo());
    }
    @Override public Optional<Administrador> buscar(UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM administrador WHERE id = ?", MAPPER, id).stream().findFirst();
    }
    @Override public Optional<Administrador> buscarPorEmail(String email) {
        return jdbc.query("SELECT " + COLS + " FROM administrador WHERE email = ?", MAPPER, email).stream().findFirst();
    }
}
