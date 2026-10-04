package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.in.FiltroDeConsumo;
import pe.factura.application.port.in.OrdenDeConsumo;
import pe.factura.application.port.in.VisibilidadDeBajas;
import pe.factura.application.port.out.ConsumoPorCuentaRepository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El consumo de todas las cuentas contra su plan de hoy (#193). Los documentos se cuentan con los mismos estados y el mismo mes que el contador de #192
 * ({@link JdbcConsumoRepository#ESTADOS_QUE_CUENTAN}: la regla vive en el dominio, no en un texto copiado aquí); las cuentas de baja se quitan con la misma regla de
 * los listados ({@link BajasEnListado}). El tope es el que manda hoy: el del plan, o el de su cambio programado si ya llegó a su fecha. Un solo recorrido, con el orden
 * y la paginación en la base: el porcentaje de la ordenación es la misma división entera (hacia abajo) que muestra {@code UsoDeLimite}.
 */
@RequiredArgsConstructor
public class JdbcConsumoPorCuentaRepository implements ConsumoPorCuentaRepository {
    private final JdbcTemplate jdbc;

    private static final String BASE = """
            SELECT * FROM (
                SELECT c.id AS cuenta_id, c.nombre, c.email, p.id AS plan_id, p.nombre AS plan_nombre, COALESCE(u.n, 0) AS documentos,
                       CASE WHEN cp.plan_id IS NOT NULL AND cp.aplica_desde <= ? THEN cp.documentos_al_mes ELSE p.documentos_al_mes END AS limite,
                       s.vence_en, s.dias_de_gracia
                FROM cuenta c
                JOIN suscripcion s ON s.cuenta_id = c.id AND s.termina_en IS NULL
                JOIN plan p ON p.id = s.plan_id
                LEFT JOIN plan_cambio_programado cp ON cp.plan_id = p.id
                LEFT JOIN (
                    SELECT t.cuenta_id, count(*) AS n FROM tenant t JOIN documento d ON d.tenant_id = t.id
                    WHERE t.cuenta_id IS NOT NULL AND d.estado IN (%s) AND d.fecha_emision >= ? AND d.fecha_emision < ?
                    GROUP BY t.cuenta_id) u ON u.cuenta_id = c.id
                WHERE %s
            ) x WHERE %s""";

    @Override public List<Registro> listar(Consulta q, int pagina, int porPagina) {
        List<Object> params = parametros(q);
        params.add(porPagina);
        params.add((long) (pagina - 1) * porPagina);
        return jdbc.query(sql(q) + " ORDER BY " + orden(q.orden()) + " LIMIT ? OFFSET ?", this::mapear, params.toArray());
    }

    @Override public List<Registro> todas(Consulta q) {
        return jdbc.query(sql(q) + " ORDER BY " + orden(q.orden()), this::mapear, parametros(q).toArray());
    }

    @Override public long contar(Consulta q) {
        return jdbc.queryForObject("SELECT count(*) FROM (" + sql(q) + ") y", Long.class, parametros(q).toArray());
    }

    private static String sql(Consulta q) {
        return BASE.formatted(JdbcConsumoRepository.ESTADOS_QUE_CUENTAN, BajasEnListado.deCuenta(VisibilidadDeBajas.OCULTAS, "c"), switch (q.filtro()) {
            case TODAS -> "TRUE";
            case CERCA_DEL_LIMITE -> "limite IS NOT NULL AND documentos * 100 >= limite::bigint * ?";
            case PLAN_VENCIDO -> "vence_en IS NOT NULL AND vence_en <= ?";
        });
    }

    /** En el orden de los {@code ?} de la consulta: el «hoy» del tope, el mes, y lo que pida el filtro. */
    private static List<Object> parametros(Consulta q) {
        List<Object> p = new ArrayList<>();
        p.add(Timestamp.from(q.ahora()));
        p.add(desde(q.mes()));
        p.add(hasta(q.mes()));
        switch (q.filtro()) {
            case CERCA_DEL_LIMITE -> p.add(q.umbralDeAlerta());
            case PLAN_VENCIDO -> p.add(Timestamp.from(q.ahora()));
            case TODAS -> { }
        }
        return p;
    }

    /** Siempre termina en el nombre y el id, para que dos cuentas empatadas no cambien de lugar entre una página y la siguiente. */
    private static String orden(OrdenDeConsumo o) {
        return switch (o) {
            case PORCENTAJE -> "(limite IS NULL), documentos * 100 / limite DESC, documentos DESC, nombre, cuenta_id";
            case DOCUMENTOS -> "documentos DESC, nombre, cuenta_id";
        };
    }

    private Registro mapear(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        int limite = rs.getInt("limite");
        Integer tope = rs.wasNull() ? null : limite;
        Timestamp vence = rs.getTimestamp("vence_en");
        return new Registro(rs.getObject("cuenta_id", UUID.class), rs.getString("nombre"), rs.getString("email"), rs.getObject("plan_id", UUID.class), rs.getString("plan_nombre"),
                rs.getLong("documentos"), tope, vence == null ? null : vence.toInstant(), rs.getInt("dias_de_gracia"));
    }

    private static Date desde(YearMonth mes) { return Date.valueOf(mes.atDay(1)); }

    private static Date hasta(YearMonth mes) { return Date.valueOf(mes.plusMonths(1).atDay(1)); }
}
