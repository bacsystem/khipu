package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.IntentosDeAccesoRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * La compuerta (no está bloqueada) y el conteo (suma, empieza de cero si venció la ventana, bloquea al llegar al tope) van en un
 * solo {@code INSERT … ON CONFLICT … WHERE}: no hay hueco entre leer y contar, y dos primeras peticiones simultáneas no crean dos
 * filas. Con la clave bloqueada el {@code WHERE} descarta la actualización y no se escribe nada (#261).
 */
@RequiredArgsConstructor
public class JdbcIntentosDeAccesoRepository implements IntentosDeAccesoRepository {
    private final JdbcTemplate jdbc;

    @Override public boolean reservar(String clave, int maxIntentos, Instant ahora, Duration ventana, Duration bloqueo) {
        Timestamp t = Timestamp.from(ahora);
        Timestamp hasta = Timestamp.from(ahora.plus(bloqueo));
        long ventanaSeg = ventana.toSeconds();
        // «nueva»: la fila anterior ya no cuenta (su bloqueo venció, o su ventana se cerró). Se escribe dos veces porque Postgres no
        // deja nombrar una expresión dentro del SET.
        return jdbc.update("""
                INSERT INTO intento_de_acceso (clave, intentos, ventana_desde, bloqueado_hasta)
                VALUES (?, 1, ?::timestamptz, CASE WHEN 1 >= ? THEN ?::timestamptz END)
                ON CONFLICT (clave) DO UPDATE SET
                    intentos = CASE
                        WHEN intento_de_acceso.bloqueado_hasta IS NOT NULL
                          OR intento_de_acceso.ventana_desde + make_interval(secs => ?) <= EXCLUDED.ventana_desde THEN 1
                        ELSE intento_de_acceso.intentos + 1 END,
                    ventana_desde = CASE
                        WHEN intento_de_acceso.bloqueado_hasta IS NOT NULL
                          OR intento_de_acceso.ventana_desde + make_interval(secs => ?) <= EXCLUDED.ventana_desde THEN EXCLUDED.ventana_desde
                        ELSE intento_de_acceso.ventana_desde END,
                    bloqueado_hasta = CASE
                        WHEN (CASE
                                WHEN intento_de_acceso.bloqueado_hasta IS NOT NULL
                                  OR intento_de_acceso.ventana_desde + make_interval(secs => ?) <= EXCLUDED.ventana_desde THEN 1
                                ELSE intento_de_acceso.intentos + 1 END) >= ? THEN ?::timestamptz
                        ELSE NULL END
                WHERE intento_de_acceso.bloqueado_hasta IS NULL OR intento_de_acceso.bloqueado_hasta <= EXCLUDED.ventana_desde
                """, clave, t, maxIntentos, hasta, ventanaSeg, ventanaSeg, ventanaSeg, maxIntentos, hasta) == 1;
    }

    @Override public void devolver(String clave) {
        jdbc.update("UPDATE intento_de_acceso SET intentos = intentos - 1, bloqueado_hasta = NULL WHERE clave = ? AND intentos > 0", clave);
    }

    @Override public void reiniciar(String clave) {
        jdbc.update("DELETE FROM intento_de_acceso WHERE clave = ?", clave);
    }

    @Override public int purgar(Instant antesDe) {
        Timestamp t = Timestamp.from(antesDe);
        return jdbc.update("""
                DELETE FROM intento_de_acceso
                WHERE ventana_desde < ?::timestamptz AND (bloqueado_hasta IS NULL OR bloqueado_hasta < ?::timestamptz)
                """, t, t);
    }
}
