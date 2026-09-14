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

    @Override public void guardar(Cuenta c) {
        jdbc.update("INSERT INTO cuenta (id, nombre, email) VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, email = EXCLUDED.email",
                c.id(), c.nombre(), c.email());
    }
    @Override public Optional<Cuenta> buscar(UUID id) {
        return jdbc.query("SELECT id, nombre, email FROM cuenta WHERE id = ?", (rs, i) -> new Cuenta(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("email")), id).stream().findFirst();
    }
    @Override public Optional<Cuenta> buscarPorEmail(String email) {
        return jdbc.query("SELECT id, nombre, email FROM cuenta WHERE email = ?", (rs, i) -> new Cuenta(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("email")), email).stream().findFirst();
    }
}
