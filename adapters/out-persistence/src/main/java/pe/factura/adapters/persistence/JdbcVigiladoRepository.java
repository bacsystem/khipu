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
 * Lo que lee el vigilante de la operación (#341): comprobantes con el envío fallando o fuera de plazo (por el índice parcial de los estados con problema), los latidos que
 * anotan los procesos externos y el registro de las alertas activas.
 */
@RequiredArgsConstructor
public class JdbcVigiladoRepository implements VigiladoRepository {
    private final JdbcTemplate jdbc;

    /** Se mide desde que se creó el comprobante y no desde su último cambio: cada reintento fallido vuelve a guardarlo y movería {@code updated_at}, así que la cuenta parpadearía. */
    @Override public long enErrorDeEnvioDesde(Instant antesDe) {
        return jdbc.queryForObject("SELECT count(*) FROM documento WHERE estado = 'ERROR_ENVIO' AND created_at < ?", Long.class, Timestamp.from(antesDe));
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

    @Override public boolean reclamarAlerta(String clave, EstadoAlerta previa, EstadoAlerta nueva) {
        if (previa == null)
            return jdbc.update("INSERT INTO alerta_operador (clave, activa, desde, ultimo_envio) VALUES (?, ?, ?, ?) ON CONFLICT (clave) DO NOTHING",
                    clave, nueva.activa(), Timestamp.from(nueva.desde()), Timestamp.from(nueva.ultimoEnvio())) == 1;
        return jdbc.update("UPDATE alerta_operador SET activa = ?, desde = ?, ultimo_envio = ? WHERE clave = ? AND activa = ? AND ultimo_envio = ?",
                nueva.activa(), Timestamp.from(nueva.desde()), Timestamp.from(nueva.ultimoEnvio()), clave, previa.activa(), Timestamp.from(previa.ultimoEnvio())) == 1;
    }

    @Override public void restaurarAlerta(String clave, EstadoAlerta reclamada, EstadoAlerta previa) {
        if (previa == null) {
            jdbc.update("DELETE FROM alerta_operador WHERE clave = ? AND ultimo_envio = ?", clave, Timestamp.from(reclamada.ultimoEnvio()));
            return;
        }
        jdbc.update("UPDATE alerta_operador SET activa = ?, desde = ?, ultimo_envio = ? WHERE clave = ? AND ultimo_envio = ?",
                previa.activa(), Timestamp.from(previa.desde()), Timestamp.from(previa.ultimoEnvio()), clave, Timestamp.from(reclamada.ultimoEnvio()));
    }
}
