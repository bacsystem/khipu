package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.ResumenDeComprobantesRepository;
import pe.factura.domain.documento.EstadoDocumento;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * El resumen de los comprobantes de una empresa (#15). Tres consultas, acotadas por el índice {@code (tenant_id, fecha_emision)}: cuántos hay por estado, cuánto suman los que
 * cuentan como facturados, por moneda, y cuántas boletas siguen FIRMADAS sin enviar desde el 5.º día (#356, {@link BoletasPorInformar}). Los estados que cuentan salen del enum
 * ({@link EstadoDocumento#cuentaComoFacturado()}), no de un texto copiado acá.
 */
@RequiredArgsConstructor
public class JdbcResumenDeComprobantesRepository implements ResumenDeComprobantesRepository {
    private final JdbcTemplate jdbc;

    /** Nombres de un enum, nunca texto de fuera, así que no hay inyección posible al armarlo. */
    static final String ESTADOS_FACTURADOS = Arrays.stream(EstadoDocumento.values()).filter(EstadoDocumento::cuentaComoFacturado)
            .map(e -> "'" + e.name() + "'").collect(Collectors.joining(", "));

    /** Notas de crédito: restan de lo facturado. */
    private static final String NOTA_DE_CREDITO = "07";
    /** Liquidación de compra (#327): es una compra de la empresa, no una venta; no cuenta como facturado. */
    private static final String LIQUIDACION_DE_COMPRA = "04";
    /** Retención y percepción (#40): lo que el agente retiene o percibe es un impuesto, no una venta; tampoco cuentan como facturado. */
    private static final String RETENCION = "20", PERCEPCION = "40";

    @Override public Agregados resumir(UUID tenantId, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        StringBuilder fechas = new StringBuilder();
        if (desde != null) { fechas.append(" AND d.fecha_emision >= ?"); params.add(Date.valueOf(desde)); }
        if (hasta != null) { fechas.append(" AND d.fecha_emision <= ?"); params.add(Date.valueOf(hasta)); }
        Object[] args = params.toArray();

        Map<EstadoDocumento, Long> porEstado = new EnumMap<>(EstadoDocumento.class);
        jdbc.query("SELECT d.estado, count(*) AS n FROM documento d WHERE d.tenant_id = ?" + fechas + " GROUP BY d.estado",
                rs -> { porEstado.put(EstadoDocumento.valueOf(rs.getString("estado")), rs.getLong("n")); }, args);

        List<Facturado> facturado = jdbc.query("""
                SELECT c.moneda, sum(CASE WHEN d.tipo = '%s' THEN -c.total ELSE c.total END) AS total
                FROM documento d JOIN comprobante c ON c.documento_id = d.id
                WHERE d.tenant_id = ? AND d.estado IN (%s) AND d.tipo NOT IN ('%s', '%s', '%s')%s
                GROUP BY c.moneda ORDER BY c.moneda
                """.formatted(NOTA_DE_CREDITO, ESTADOS_FACTURADOS, LIQUIDACION_DE_COMPRA, RETENCION, PERCEPCION, fechas), (rs, i) -> new Facturado(rs.getString("moneda"), rs.getBigDecimal("total")), args);

        // #356: la boleta FIRMADA sin enviar desde el 5.º día. El «?» del corte va antes que los de la empresa y las fechas, que están después en el texto.
        List<Object> conCorte = new ArrayList<>();
        conCorte.add(BoletasPorInformar.corte(hoy));
        conCorte.addAll(params);
        long boletasSinEnviar = jdbc.queryForObject("""
                SELECT count(*) FROM documento d LEFT JOIN comprobante c ON c.documento_id = d.id
                WHERE %s AND d.tenant_id = ?%s
                """.formatted(BoletasPorInformar.sinEnviar("c"), fechas), Long.class, conCorte.toArray());

        return new Agregados(porEstado, facturado, boletasSinEnviar);
    }
}
