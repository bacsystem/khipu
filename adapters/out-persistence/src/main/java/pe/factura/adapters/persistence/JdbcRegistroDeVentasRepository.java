package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.RegistroDeVentasRepository;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.Monedas;
import pe.factura.domain.sire.FilaRvie;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * El registro de ventas del SIRE (#322) sobre las tablas de comprobantes. Todo va acotado por el índice {@code (tenant_id, fecha_emision)}; el recorrido es por
 * posición (fecha, tipo, serie, número) y no por OFFSET, así que un comprobante que cambia de estado a mitad de la descarga no hace que otro se salte ni se repita.
 * Los estados salen del dominio ({@link FilaRvie#INCLUIDOS}, {@link FilaRvie#PENDIENTES}): nombres de un enum, sin texto de fuera.
 */
public class JdbcRegistroDeVentasRepository implements RegistroDeVentasRepository {
    static final String INCLUIDOS = lista(FilaRvie.INCLUIDOS);
    static final String PENDIENTES = lista(FilaRvie.PENDIENTES);
    private static final String ORDEN = " ORDER BY d.fecha_emision, d.tipo, d.serie, d.numero";

    private final JdbcTemplate jdbc;
    private final JdbcComprobanteRepository comprobantes;

    public JdbcRegistroDeVentasRepository(JdbcTemplate jdbc, JdbcComprobanteRepository comprobantes) { this.jdbc = jdbc; this.comprobantes = comprobantes; }

    @Override public Conteo contar(UUID tenantId, LocalDate desde, LocalDate hasta, int ejemplos) {
        String sinTipoCambio = "d.estado IN (" + INCLUIDOS + ") AND c.moneda <> '" + Monedas.SOLES + "' AND c.tipo_cambio IS NULL";
        Object[] args = {tenantId, Date.valueOf(desde), Date.valueOf(hasta)};
        long[] n = jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE d.estado IN (%s)) AS incluidos, count(*) FILTER (WHERE d.estado IN (%s)) AS pendientes,
                       count(*) FILTER (WHERE %s) AS sin_tipo_cambio
                FROM documento d JOIN comprobante c ON c.documento_id = d.id
                WHERE d.tenant_id = ? AND d.fecha_emision BETWEEN ? AND ?
                """.formatted(INCLUIDOS, PENDIENTES, sinTipoCambio),
                (rs, i) -> new long[]{rs.getLong("incluidos"), rs.getLong("pendientes"), rs.getLong("sin_tipo_cambio")}, args);
        List<String> muestra = n[2] == 0 || ejemplos <= 0 ? List.of() : jdbc.queryForList(
                "SELECT d.serie || '-' || d.numero FROM documento d JOIN comprobante c ON c.documento_id = d.id WHERE d.tenant_id = ? AND d.fecha_emision BETWEEN ? AND ? AND "
                        + sinTipoCambio + ORDEN + " LIMIT " + ejemplos, String.class, args);
        return new Conteo(n[0], n[1], n[2], muestra);
    }

    @Override public List<Comprobante> siguientes(UUID tenantId, LocalDate desde, LocalDate hasta, Posicion despuesDe, int limite) {
        List<Object> args = new ArrayList<>(List.of(tenantId, Date.valueOf(desde), Date.valueOf(hasta)));
        StringBuilder sql = new StringBuilder("SELECT d.id FROM documento d WHERE d.tenant_id = ? AND d.fecha_emision BETWEEN ? AND ? AND d.estado IN (" + INCLUIDOS + ")");
        if (despuesDe != null) {
            sql.append(" AND (d.fecha_emision, d.tipo, d.serie, d.numero) > (?, ?, ?, ?)");
            args.addAll(List.of(Date.valueOf(despuesDe.fechaEmision()), despuesDe.tipo(), despuesDe.serie(), despuesDe.numero()));
        }
        sql.append(ORDEN).append(" LIMIT ?");
        args.add(limite);
        List<UUID> ids = jdbc.queryForList(sql.toString(), UUID.class, args.toArray());
        return comprobantes.porIds(tenantId, ids, ORDEN);
    }

    @Override public Map<Referencia, LocalDate> fechasDeEmision(UUID tenantId, Collection<Referencia> referencias) {
        Map<Referencia, LocalDate> fechas = new HashMap<>();
        if (referencias.isEmpty()) return fechas;
        List<Referencia> refs = List.copyOf(referencias);
        jdbc.query("""
                SELECT d.tipo, d.serie, d.numero, d.fecha_emision FROM documento d
                JOIN unnest(?::varchar[], ?::varchar[], ?::bigint[]) AS r(tipo, serie, numero) ON d.tipo = r.tipo AND d.serie = r.serie AND d.numero = r.numero
                WHERE d.tenant_id = ?
                """, ps -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("varchar", refs.stream().map(Referencia::tipo).toArray()));
                    ps.setArray(2, ps.getConnection().createArrayOf("varchar", refs.stream().map(Referencia::serie).toArray()));
                    ps.setArray(3, ps.getConnection().createArrayOf("bigint", refs.stream().map(Referencia::numero).toArray()));
                    ps.setObject(4, tenantId);
                }, rs -> { fechas.put(new Referencia(rs.getString("tipo"), rs.getString("serie"), rs.getLong("numero")), rs.getDate("fecha_emision").toLocalDate()); });
        return fechas;
    }

    private static String lista(Set<EstadoDocumento> estados) {
        return estados.stream().map(e -> "'" + e.name() + "'").sorted().collect(Collectors.joining(", "));
    }
}
