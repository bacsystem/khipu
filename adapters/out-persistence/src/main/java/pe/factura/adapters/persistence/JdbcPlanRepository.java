package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.PlanRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CambioDeLimites;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Los planes (#189, #190). Un límite en {@code NULL} es «sin límite»; la base no admite cero, así que {@link Limite} nunca falla al leer. El cambio de límites
 * programado vive en {@code plan_cambio_programado} (uno por plan) y se lee junto al plan. Las escrituras no abren transacción: van dentro de la del servicio.
 */
@RequiredArgsConstructor
public class JdbcPlanRepository implements PlanRepository {
    private final JdbcTemplate jdbc;

    private static final String SELECT = """
            SELECT p.id, p.nombre, p.precio_mensual, p.documentos_al_mes, p.rucs, p.usuarios, p.api_keys, p.retencion_anios, p.estado, p.por_defecto,
                   c.aplica_desde, c.documentos_al_mes AS c_documentos, c.rucs AS c_rucs, c.usuarios AS c_usuarios, c.api_keys AS c_api_keys,
                   c.retencion_anios AS c_retencion
            FROM plan p LEFT JOIN plan_cambio_programado c ON c.plan_id = p.id""";

    @Override public Optional<Plan> buscar(UUID id) {
        return jdbc.query(SELECT + " WHERE p.id = ?", this::mapear, id).stream().findFirst();
    }

    @Override public Optional<Plan> buscarParaEditar(UUID id) {
        return jdbc.query(SELECT + " WHERE p.id = ? FOR UPDATE OF p", this::mapear, id).stream().findFirst();
    }

    @Override public List<Plan> listar() {
        return jdbc.query(SELECT + " ORDER BY p.precio_mensual, p.nombre", this::mapear);
    }

    @Override public Plan porDefecto() {
        return jdbc.query(SELECT + " WHERE p.por_defecto", this::mapear).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay plan por defecto: la migración de planes debía dejarlo"));
    }

    @Override public void guardar(Plan p) {
        Limites l = p.limites();
        try {
            jdbc.update("""
                    INSERT INTO plan (id, nombre, precio_mensual, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios, estado, por_defecto)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, precio_mensual = EXCLUDED.precio_mensual,
                        documentos_al_mes = EXCLUDED.documentos_al_mes, rucs = EXCLUDED.rucs, usuarios = EXCLUDED.usuarios, api_keys = EXCLUDED.api_keys,
                        retencion_anios = EXCLUDED.retencion_anios, estado = EXCLUDED.estado, por_defecto = EXCLUDED.por_defecto
                    """, p.id(), p.nombre(), p.precioMensual(), l.documentosAlMes().maximo(), l.rucs(), l.usuarios().maximo(), l.apiKeys().maximo(),
                    l.retencionAnios(), p.estado().name(), p.porDefecto());
        } catch (DuplicateKeyException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT).contains("ux_plan_nombre"))
                throw new DomainException("NOMBRE_DUPLICADO", "Ya existe un plan llamado «" + p.nombre() + "»");
            throw e;
        }
        guardarProgramado(p);
    }

    private void guardarProgramado(Plan p) {
        CambioDeLimites c = p.programado();
        if (c == null) {
            jdbc.update("DELETE FROM plan_cambio_programado WHERE plan_id = ?", p.id());
            return;
        }
        Limites l = c.limites();
        jdbc.update("""
                INSERT INTO plan_cambio_programado (plan_id, aplica_desde, documentos_al_mes, rucs, usuarios, api_keys, retencion_anios)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (plan_id) DO UPDATE SET aplica_desde = EXCLUDED.aplica_desde, documentos_al_mes = EXCLUDED.documentos_al_mes, rucs = EXCLUDED.rucs,
                    usuarios = EXCLUDED.usuarios, api_keys = EXCLUDED.api_keys, retencion_anios = EXCLUDED.retencion_anios
                """, p.id(), Timestamp.from(c.aplicaDesde()), l.documentosAlMes().maximo(), l.rucs(), l.usuarios().maximo(), l.apiKeys().maximo(), l.retencionAnios());
    }

    @Override public boolean eliminar(UUID id) {
        return jdbc.update("""
                DELETE FROM plan WHERE id = ? AND NOT por_defecto AND NOT EXISTS (SELECT 1 FROM suscripcion s WHERE s.plan_id = plan.id)
                AND NOT EXISTS (SELECT 1 FROM suscripcion_cambio_programado c WHERE c.plan_id = plan.id)
                """, id) == 1;
    }

    @Override public Map<UUID, Long> cuentasPorPlan() {
        Map<UUID, Long> r = new HashMap<>();
        jdbc.query("SELECT plan_id, count(*) AS n FROM suscripcion WHERE termina_en IS NULL GROUP BY plan_id",
                rs -> { r.put(rs.getObject("plan_id", UUID.class), rs.getLong("n")); });
        return r;
    }

    @Override public long suscripcionesDelPlan(UUID id) {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM suscripcion WHERE plan_id = ?) + (SELECT count(*) FROM suscripcion_cambio_programado WHERE plan_id = ?)", Long.class, id, id);
    }

    private Plan mapear(ResultSet rs, int i) throws SQLException {
        Limites vigentes = new Limites(limite(rs, "documentos_al_mes"), rs.getInt("rucs"), limite(rs, "usuarios"), limite(rs, "api_keys"), rs.getInt("retencion_anios"));
        Timestamp aplicaDesde = rs.getTimestamp("aplica_desde");
        CambioDeLimites programado = aplicaDesde == null ? null
                : new CambioDeLimites(new Limites(limite(rs, "c_documentos"), rs.getInt("c_rucs"), limite(rs, "c_usuarios"), limite(rs, "c_api_keys"), rs.getInt("c_retencion")),
                        aplicaDesde.toInstant());
        return new Plan(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getBigDecimal("precio_mensual"), vigentes,
                EstadoPlan.valueOf(rs.getString("estado")), rs.getBoolean("por_defecto"), programado);
    }

    private static Limite limite(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? Limite.sinLimite() : Limite.de(valor);
    }
}
