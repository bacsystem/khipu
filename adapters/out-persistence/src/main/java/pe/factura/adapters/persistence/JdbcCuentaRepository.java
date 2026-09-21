package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.domain.cuenta.Cuenta;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcCuentaRepository implements CuentaRepository {
    private final JdbcTemplate jdbc;

    private static final String SELECT = "SELECT id, nombre, email, telefono FROM cuenta";

    @Override public void guardar(Cuenta c) {
        jdbc.update("""
            INSERT INTO cuenta (id, nombre, email, telefono) VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, email = EXCLUDED.email, telefono = EXCLUDED.telefono
            """, c.id(), c.nombre(), c.email(), c.telefono());
    }
    @Override public Optional<Cuenta> buscar(UUID id) {
        return jdbc.query(SELECT + " WHERE id = ?", this::mapear, id).stream().findFirst();
    }
    @Override public Optional<Cuenta> buscarPorEmail(String email) {
        return jdbc.query(SELECT + " WHERE email = ?", this::mapear, email).stream().findFirst();
    }
    private Cuenta mapear(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Cuenta(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("email"), rs.getString("telefono"));
    }
}
