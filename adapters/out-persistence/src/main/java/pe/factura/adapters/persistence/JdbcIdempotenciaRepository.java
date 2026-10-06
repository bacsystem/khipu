package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.IdempotenciaRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * La reserva es un {@code INSERT ... ON CONFLICT DO NOTHING}: si otra transacción insertó la misma clave y no confirmó, Postgres
 * espera a que termine; si confirma no se inserta nada y se lee lo suyo, y si se revierte la inserción sigue adelante. Leer antes
 * de insertar dejaría pasar a los dos pedidos.
 */
@RequiredArgsConstructor
public class JdbcIdempotenciaRepository implements IdempotenciaRepository {
    private static final RowMapper<Registro> MAPPER = (rs, i) -> new Registro(rs.getString("huella"), rs.getObject("recurso_id", UUID.class),
            rs.getBytes("respuesta_cifrada"), rs.getTimestamp("creado_at").toInstant());
    private final JdbcTemplate jdbc;

    @Override public Optional<Registro> reservar(String alcance, String clave, String huella) {
        int insertadas = jdbc.update("INSERT INTO idempotencia (alcance, clave, huella) VALUES (?, ?, ?) ON CONFLICT (alcance, clave) DO NOTHING",
                alcance, clave, huella);
        if (insertadas == 1) return Optional.empty();
        return buscar(alcance, clave);
    }

    @Override public Optional<Registro> buscar(String alcance, String clave) {
        return jdbc.query("SELECT huella, recurso_id, respuesta_cifrada, creado_at FROM idempotencia WHERE alcance = ? AND clave = ?", MAPPER, alcance, clave)
                .stream().findFirst();
    }

    @Override public void completar(String alcance, String clave, UUID recursoId, byte[] respuestaCifrada) {
        jdbc.update("UPDATE idempotencia SET recurso_id = ?, respuesta_cifrada = ? WHERE alcance = ? AND clave = ?", recursoId, respuestaCifrada, alcance, clave);
    }

    @Override public int borrarAnterioresA(Instant limite) {
        return jdbc.update("DELETE FROM idempotencia WHERE creado_at < ?", Timestamp.from(limite));
    }

    @Override public int olvidarRespuestasAnterioresA(Instant limite) {
        return jdbc.update("UPDATE idempotencia SET respuesta_cifrada = NULL WHERE respuesta_cifrada IS NOT NULL AND creado_at < ?", Timestamp.from(limite));
    }
}
