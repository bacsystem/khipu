package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.AceptacionDeTerminosRepository;
import pe.factura.domain.cuenta.AceptacionDeTerminos;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcAceptacionDeTerminosRepository implements AceptacionDeTerminosRepository {
    private final JdbcTemplate jdbc;

    /** {@code DO NOTHING}: dos clics a la vez, o aceptar otra vez, no mueven la fecha de la primera aceptación. */
    @Override public void registrar(AceptacionDeTerminos a) {
        jdbc.update("INSERT INTO aceptacion_terminos (usuario_id, version, aceptada_at) VALUES (?, ?, ?) ON CONFLICT (usuario_id, version) DO NOTHING",
                a.usuarioId(), a.version(), Timestamp.from(a.aceptadaEn()));
    }

    @Override public Optional<AceptacionDeTerminos> buscar(UUID usuarioId, String version) {
        return jdbc.query("SELECT usuario_id, version, aceptada_at FROM aceptacion_terminos WHERE usuario_id = ? AND version = ?",
                (rs, i) -> new AceptacionDeTerminos(rs.getObject("usuario_id", UUID.class), rs.getString("version"), rs.getTimestamp("aceptada_at").toInstant()),
                usuarioId, version).stream().findFirst();
    }
}
