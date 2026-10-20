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
 * El resumen de los comprobantes de una empresa (#15) y su emisión por día (#24). El resumen son tres consultas, acotadas por el índice {@code (tenant_id, fecha_emision)}: cuántos hay por estado, cuánto suman los que
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
    // Solo las ventas ({@link TiposDeVenta}, de TipoDocumento#esVenta): ni la liquidación de compra (04, #327), que es una compra, ni la retención o la percepción
    // (20, 40, #40), un impuesto que el agente retiene o percibe, cuentan en «emitidos» ni en «facturado».

    @Override public Agregados resumir(UUID tenantId, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        StringBuilder fechas = new StringBuilder();
        if (desde != null) { fechas.append(" AND d.fecha_emision >= ?"); params.add(Date.valueOf(desde)); }
        if (hasta != null) { fechas.append(" AND d.fecha_emision <= ?"); params.add(Date.valueOf(hasta)); }
        Object[] args = params.toArray();

        Map<EstadoDocumento, Long> porEstado = new EnumMap<>(EstadoDocumento.class);
        jdbc.query("SELECT d.estado, count(*) AS n FROM documento d WHERE d.tenant_id = ? AND d.tipo IN (" + TiposDeVenta.SQL + ")" + fechas + " GROUP BY d.estado",
                rs -> { porEstado.put(EstadoDocumento.valueOf(rs.getString("estado")), rs.getLong("n")); }, args);

        List<Facturado> facturado = jdbc.query("""
                SELECT c.moneda, sum(CASE WHEN d.tipo = '%s' THEN -c.total ELSE c.total END) AS total
                FROM documento d JOIN comprobante c ON c.documento_id = d.id
                WHERE d.tenant_id = ? AND d.estado IN (%s) AND d.tipo IN (%s)%s
                GROUP BY c.moneda ORDER BY c.moneda
                """.formatted(NOTA_DE_CREDITO, ESTADOS_FACTURADOS, TiposDeVenta.SQL, fechas), (rs, i) -> new Facturado(rs.getString("moneda"), rs.getBigDecimal("total")), args);

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

    /**
     * #24: un solo recorrido por el índice {@code (tenant_id, fecha_emision)}, agrupado por día, estado y moneda. {@code fecha_emision} es la fecha del comprobante (un DATE,
     * la de Lima en que se emitió), así que agrupar por ella no depende del huso horario de la sesión. El LEFT JOIN deja también los documentos sin fila de totales.
     */
    @Override public List<Celda> porDia(UUID tenantId, LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                SELECT d.fecha_emision, d.estado, c.moneda, count(*) AS n, sum(CASE WHEN d.tipo = '%s' THEN -c.total ELSE c.total END) AS neto
                FROM documento d LEFT JOIN comprobante c ON c.documento_id = d.id
                WHERE d.tenant_id = ? AND d.fecha_emision >= ? AND d.fecha_emision <= ?
                GROUP BY d.fecha_emision, d.estado, c.moneda
                ORDER BY d.fecha_emision, d.estado, c.moneda
                """.formatted(NOTA_DE_CREDITO),
                // Como LocalDate y no como java.sql.Date: ese pasa por medianoche en el huso de la JVM y podría correr el día.
                (rs, i) -> new Celda(rs.getObject("fecha_emision", LocalDate.class), EstadoDocumento.valueOf(rs.getString("estado")), rs.getString("moneda"), rs.getLong("n"),
                        rs.getBigDecimal("neto")),
                tenantId, Date.valueOf(desde), Date.valueOf(hasta));
    }
}
