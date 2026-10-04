package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SuscripcionRepository;
import pe.factura.domain.plan.CambioDePlan;
import pe.factura.domain.plan.PlanesDeCuenta;
import pe.factura.domain.plan.Suscripcion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Suscripciones de las cuentas (#189). El cambio de plan es una única sentencia (una CTE que cierra la activa y, solo si la cerró, inserta la nueva): no hay
 * momento en que la cuenta quede sin plan ni con dos, y de dos cambios a la vez solo uno encuentra la suscripción aún abierta. El índice único parcial de la base
 * es la red de seguridad si alguna vez otro código escribe por su cuenta.
 */
@RequiredArgsConstructor
public class JdbcSuscripcionRepository implements SuscripcionRepository {
    private final JdbcTemplate jdbc;

    @Override public Optional<PlanesDeCuenta> deLaCuenta(UUID cuentaId) {
        List<Suscripcion> lista = jdbc.query("""
                SELECT id, cuenta_id, plan_id, inicia_en, vence_en, dias_de_gracia, termina_en
                FROM suscripcion WHERE cuenta_id = ? ORDER BY inicia_en, termina_en NULLS LAST, created_at
                """, this::mapear, cuentaId);
        return lista.isEmpty() ? Optional.empty() : Optional.of(new PlanesDeCuenta(cuentaId, lista, programado(cuentaId)));
    }

    private CambioDePlan programado(UUID cuentaId) {
        return jdbc.query("SELECT plan_id, aplica_desde, vence_en, dias_de_gracia FROM suscripcion_cambio_programado WHERE cuenta_id = ?",
                (rs, i) -> new CambioDePlan(rs.getObject("plan_id", UUID.class), rs.getTimestamp("aplica_desde").toInstant(), instante(rs, "vence_en"), rs.getInt("dias_de_gracia")),
                cuentaId).stream().findFirst().orElse(null);
    }

    @Override public boolean cambiar(Suscripcion actual, Suscripcion nueva) {
        if (!actual.cuentaId().equals(nueva.cuentaId())) return false;
        return jdbc.update("""
                WITH cerrada AS (
                    UPDATE suscripcion SET termina_en = ? WHERE id = ? AND cuenta_id = ? AND termina_en IS NULL RETURNING cuenta_id),
                sin_programado AS (
                    DELETE FROM suscripcion_cambio_programado WHERE cuenta_id IN (SELECT cuenta_id FROM cerrada))
                INSERT INTO suscripcion (id, cuenta_id, plan_id, inicia_en, vence_en, dias_de_gracia)
                SELECT ?, cerrada.cuenta_id, ?, ?, ?, ? FROM cerrada
                """, Timestamp.from(nueva.iniciaEn()), actual.id(), actual.cuentaId(),
                nueva.id(), nueva.planId(), Timestamp.from(nueva.iniciaEn()), nueva.venceEn() == null ? null : Timestamp.from(nueva.venceEn()), nueva.diasDeGracia()) == 1;
    }

    @Override public void programar(UUID cuentaId, CambioDePlan c) {
        jdbc.update("""
                INSERT INTO suscripcion_cambio_programado (cuenta_id, plan_id, aplica_desde, vence_en, dias_de_gracia) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (cuenta_id) DO UPDATE SET plan_id = EXCLUDED.plan_id, aplica_desde = EXCLUDED.aplica_desde, vence_en = EXCLUDED.vence_en,
                    dias_de_gracia = EXCLUDED.dias_de_gracia
                """, cuentaId, c.planId(), Timestamp.from(c.aplicaDesde()), c.venceEn() == null ? null : Timestamp.from(c.venceEn()), c.diasDeGracia());
    }

    @Override public void cancelarProgramado(UUID cuentaId) {
        jdbc.update("DELETE FROM suscripcion_cambio_programado WHERE cuenta_id = ?", cuentaId);
    }

    @Override public List<UUID> cuentasConCambioVencido(Instant ahora, int limite) {
        return jdbc.queryForList("SELECT cuenta_id FROM suscripcion_cambio_programado WHERE aplica_desde <= ? ORDER BY aplica_desde, cuenta_id LIMIT ?", UUID.class,
                Timestamp.from(ahora), limite);
    }

    private Suscripcion mapear(ResultSet rs, int i) throws SQLException {
        return new Suscripcion(rs.getObject("id", UUID.class), rs.getObject("cuenta_id", UUID.class), rs.getObject("plan_id", UUID.class),
                rs.getTimestamp("inicia_en").toInstant(), instante(rs, "vence_en"), rs.getInt("dias_de_gracia"), instante(rs, "termina_en"));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t == null ? null : t.toInstant();
    }
}
