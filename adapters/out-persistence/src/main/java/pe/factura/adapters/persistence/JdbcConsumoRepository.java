package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.ConsumoRepository;
import pe.factura.domain.documento.EstadoDocumento;

import java.sql.Date;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * El consumo mensual (#192). La lista de estados que cuentan sale de {@link EstadoDocumento#cuentaParaElConsumo()}, no de un texto copiado aquí: así la regla
 * comercial vive en un solo sitio. El mes es el calendario, por la fecha de emisión (un {@code DATE}, ya en hora de Lima), de la medianoche del día 1 inclusive a la
 * del día 1 siguiente exclusive. Cuenta filas de {@code documento}: un comprobante reintentado es una sola fila, y la comunicación de baja es otra tabla.
 */
@RequiredArgsConstructor
public class JdbcConsumoRepository implements ConsumoRepository {
    private final JdbcTemplate jdbc;

    /** {@code 'ACEPTADO', 'ACEPTADO_CON_OBS'}: nombres de un enum, nunca texto de fuera, así que no hay inyección posible al armarlo. */
    private static final String ESTADOS_QUE_CUENTAN = Arrays.stream(EstadoDocumento.values()).filter(EstadoDocumento::cuentaParaElConsumo)
            .map(e -> "'" + e.name() + "'").collect(Collectors.joining(", "));

    @Override public long documentosDeEmpresa(UUID tenantId, YearMonth mes) {
        return jdbc.queryForObject("SELECT count(*) FROM documento WHERE tenant_id = ? AND estado IN (" + ESTADOS_QUE_CUENTAN + ") AND fecha_emision >= ? AND fecha_emision < ?",
                Long.class, tenantId, desde(mes), hasta(mes));
    }

    @Override public List<ConsumoDeEmpresa> documentosPorEmpresaDeCuenta(UUID cuentaId, YearMonth mes) {
        return jdbc.query("""
                SELECT t.id, t.ruc, t.razon_social, count(d.id) AS documentos
                FROM tenant t
                LEFT JOIN documento d ON d.tenant_id = t.id AND d.estado IN (%s) AND d.fecha_emision >= ? AND d.fecha_emision < ?
                WHERE t.cuenta_id = ?
                GROUP BY t.id, t.ruc, t.razon_social
                ORDER BY t.ruc
                """.formatted(ESTADOS_QUE_CUENTAN),
                (rs, i) -> new ConsumoDeEmpresa(rs.getObject("id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"), rs.getLong("documentos")),
                desde(mes), hasta(mes), cuentaId);
    }

    private static Date desde(YearMonth mes) { return Date.valueOf(mes.atDay(1)); }

    private static Date hasta(YearMonth mes) { return Date.valueOf(mes.plusMonths(1).atDay(1)); }
}
