package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SegundoFactorRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las reglas que protegen el segundo factor (anti-reuso, tope de fallos, códigos de un solo uso) viven en la condición de cada
 * UPDATE y no en una lectura previa: así se cumplen también entre peticiones simultáneas.
 */
@RequiredArgsConstructor
public class JdbcSegundoFactorRepository implements SegundoFactorRepository {
    private final JdbcTemplate jdbc;

    @Override public Optional<Estado> buscar(UUID administradorId) {
        return jdbc.query("""
                SELECT secreto_cifrado, confirmado, ultimo_paso, fallos, bloqueado_hasta
                FROM administrador_segundo_factor WHERE administrador_id = ?
                """, (rs, i) -> {
            Timestamp bloqueo = rs.getTimestamp("bloqueado_hasta");
            return new Estado(rs.getBytes("secreto_cifrado"), rs.getBoolean("confirmado"), rs.getLong("ultimo_paso"), rs.getInt("fallos"),
                    bloqueo == null ? null : bloqueo.toInstant());
        }, administradorId).stream().findFirst();
    }

    @Override public void guardarPendiente(UUID administradorId, byte[] secretoCifrado) {
        jdbc.update("""
                INSERT INTO administrador_segundo_factor (administrador_id, secreto_cifrado) VALUES (?, ?)
                ON CONFLICT (administrador_id) DO UPDATE SET secreto_cifrado = EXCLUDED.secreto_cifrado, confirmado = false,
                    ultimo_paso = 0, fallos = 0, bloqueado_hasta = NULL, actualizado_at = now()
                """, administradorId, secretoCifrado);
        jdbc.update("DELETE FROM administrador_codigo_recuperacion WHERE administrador_id = ?", administradorId);
    }

    @Override public void confirmar(UUID administradorId, long paso, List<String> hashesRecuperacion) {
        jdbc.update("""
                UPDATE administrador_segundo_factor SET confirmado = true, ultimo_paso = ?, fallos = 0, bloqueado_hasta = NULL, actualizado_at = now()
                WHERE administrador_id = ?
                """, paso, administradorId);
        for (String hash : hashesRecuperacion)
            jdbc.update("INSERT INTO administrador_codigo_recuperacion (administrador_id, hash) VALUES (?, ?)", administradorId, hash);
    }

    @Override public boolean registrarAcceso(UUID administradorId, long paso) {
        return jdbc.update("""
                UPDATE administrador_segundo_factor SET ultimo_paso = ?, fallos = 0, bloqueado_hasta = NULL, actualizado_at = now()
                WHERE administrador_id = ? AND ultimo_paso < ?
                """, paso, administradorId, paso) == 1;
    }

    /** La compuerta (no está bloqueada) y el conteo (suma, y bloquea al llegar al tope) en una sola sentencia: no hay hueco entre leer y contar. */
    @Override public boolean reservarIntento(UUID administradorId, int maxIntentos, Instant ahora, Instant bloquearHasta) {
        return jdbc.update("""
                UPDATE administrador_segundo_factor SET
                    fallos = CASE WHEN fallos + 1 >= ? THEN 0 ELSE fallos + 1 END,
                    bloqueado_hasta = CASE WHEN fallos + 1 >= ? THEN ?::timestamptz ELSE bloqueado_hasta END,
                    actualizado_at = now()
                WHERE administrador_id = ? AND (bloqueado_hasta IS NULL OR bloqueado_hasta <= ?::timestamptz)
                """, maxIntentos, maxIntentos, Timestamp.from(bloquearHasta), administradorId, Timestamp.from(ahora)) == 1;
    }

    @Override public boolean consumirCodigoRecuperacion(UUID administradorId, String hash) {
        boolean consumido = jdbc.update("""
                UPDATE administrador_codigo_recuperacion SET usado_at = now()
                WHERE administrador_id = ? AND hash = ? AND usado_at IS NULL
                """, administradorId, hash) == 1;
        if (consumido) jdbc.update("UPDATE administrador_segundo_factor SET fallos = 0, bloqueado_hasta = NULL, actualizado_at = now() WHERE administrador_id = ?", administradorId);
        return consumido;
    }
}
