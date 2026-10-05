package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

    @Override public Optional<Registro> buscar(String alcance, String clave) {
        return jdbc.query("SELECT huella, recurso_id FROM idempotencia WHERE alcance = ? AND clave = ?",
                (rs, i) -> new Registro(rs.getString("huella"), rs.getObject("recurso_id", UUID.class)), alcance, clave).stream().findFirst();
    }

    @Override public Optional<Registro> reservar(String alcance, String clave, String huella) {
        int insertadas = jdbc.update("INSERT INTO idempotencia (alcance, clave, huella) VALUES (?, ?, ?) ON CONFLICT (alcance, clave) DO NOTHING",
                alcance, clave, huella);
        if (insertadas == 1) return Optional.empty();
        return buscar(alcance, clave);
    }

    @Override public void completar(String alcance, String clave, UUID recursoId) {
        jdbc.update("UPDATE idempotencia SET recurso_id = ? WHERE alcance = ? AND clave = ?", recursoId, alcance, clave);
    }

    @Override public int borrarAnterioresA(Instant limite) {
        return jdbc.update("DELETE FROM idempotencia WHERE creado_at < ?", Timestamp.from(limite));
    }
}
