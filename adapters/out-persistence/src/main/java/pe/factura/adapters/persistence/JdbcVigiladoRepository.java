package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.VigiladoRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que lee el vigilante de la operación (#341): comprobantes con el envío fallando o fuera de plazo (por el índice parcial de estado y {@code updated_at}), los latidos que
 * anotan los procesos externos y el registro de las alertas activas.
 */
@RequiredArgsConstructor
public class JdbcVigiladoRepository implements VigiladoRepository {
    private final JdbcTemplate jdbc;

    @Override public long enErrorDeEnvioDesde(Instant antesDe) {
        return jdbc.queryForObject("SELECT count(*) FROM documento WHERE estado = 'ERROR_ENVIO' AND updated_at < ?", Long.class, Timestamp.from(antesDe));
    }

    @Override public long fueraDePlazoDesde(Instant desde) {
        return jdbc.queryForObject("SELECT count(*) FROM documento WHERE estado = 'FUERA_DE_PLAZO' AND updated_at >= ?", Long.class, Timestamp.from(desde));
    }

    @Override public Optional<Instant> ultimoLatido(String clave) {
        return jdbc.query("SELECT ultimo FROM latido_operativo WHERE clave = ?", (rs, i) -> rs.getTimestamp("ultimo").toInstant(), clave).stream().findFirst();
    }

    @Override public Map<String, EstadoAlerta> alertas() {
        Map<String, EstadoAlerta> alertas = new HashMap<>();
        jdbc.query("SELECT clave, activa, desde, ultimo_envio FROM alerta_operador",
                rs -> { alertas.put(rs.getString("clave"), new EstadoAlerta(rs.getBoolean("activa"), rs.getTimestamp("desde").toInstant(), rs.getTimestamp("ultimo_envio").toInstant())); });
        return alertas;
    }

    @Override public void guardarAlerta(String clave, EstadoAlerta estado) {
        jdbc.update("""
                INSERT INTO alerta_operador (clave, activa, desde, ultimo_envio) VALUES (?, ?, ?, ?)
                ON CONFLICT (clave) DO UPDATE SET activa = EXCLUDED.activa, desde = EXCLUDED.desde, ultimo_envio = EXCLUDED.ultimo_envio
                """, clave, estado.activa(), Timestamp.from(estado.desde()), Timestamp.from(estado.ultimoEnvio()));
    }
}
