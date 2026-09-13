package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.domain.documento.*;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class JdbcComprobanteRepository implements ComprobanteRepository {
    private final JdbcTemplate jdbc;
    public JdbcComprobanteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void guardar(Comprobante c) {
        int filas = jdbc.update("""
            UPDATE documento SET estado = ?, hash = ?, ticket = NULL, intentos = ?, ultimo_error = ?, cdr_codigo = ?, cdr_descripcion = ?,
              cdr_observaciones = ?::jsonb, xml_key = ?, cdr_key = ?, updated_at = now() WHERE id = ? AND tenant_id = ?
            """, c.estado().name(), c.hash(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : c.cdr().codigo(), c.cdr() == null ? null : c.cdr().descripcion(),
                c.cdr() == null ? null : aJson(c.cdr().observaciones()), c.xmlKey(), c.cdrKey(), c.id(), c.tenantId());
        if (filas > 0) return;
        jdbc.update("""
            INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, hash, nombre_archivo, intentos, ultimo_error, xml_key, cdr_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.tenantId(), c.tipo().codigo(), c.serie(), c.numero(), Date.valueOf(c.fechaEmision()), c.estado().name(),
                c.hash(), c.nombreArchivo(), c.intentos(), c.ultimoError(), c.xmlKey(), c.cdrKey());
        Totales t = c.totales();
        jdbc.update("""
            INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, receptor_direccion,
              total_gravado, total_exonerado, total_inafecto, total_igv, total) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.tipoOperacion(), c.moneda(), c.receptor().tipoDoc(), c.receptor().numDoc(), c.receptor().razonSocial(),
                c.receptor().direccion(), t.gravado(), t.exonerado(), t.inafecto(), t.igv(), t.total());
        int orden = 1;
        for (Item i : c.items()) {
            jdbc.update("INSERT INTO comprobante_item (comprobante_id, orden, codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    c.id(), orden++, i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), i.afectacion().codigo());
        }
    }

    @Override public Optional<Comprobante> buscar(UUID tenantId, UUID id) {
        return jdbc.query(SELECT + " WHERE d.id = ? AND d.tenant_id = ?", this::mapear, id, tenantId).stream().findFirst();
    }
    @Override public boolean existe(UUID tenantId, TipoDocumento tipo, String serie, long numero) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM documento WHERE tenant_id = ? AND tipo = ? AND serie = ? AND numero = ?", Integer.class, tenantId, tipo.codigo(), serie, numero);
        return n != null && n > 0;
    }
    @Override public List<Comprobante> listar(UUID tenantId, EstadoDocumento estado, int pagina, int porPagina) {
        String sql = SELECT + " WHERE d.tenant_id = ?" + (estado == null ? "" : " AND d.estado = ?") + " ORDER BY d.created_at DESC LIMIT ? OFFSET ?";
        Object[] args = estado == null ? new Object[]{tenantId, porPagina, (pagina - 1) * porPagina} : new Object[]{tenantId, estado.name(), porPagina, (pagina - 1) * porPagina};
        return jdbc.query(sql, this::mapear, args);
    }

    private static final String SELECT = """
        SELECT d.id, d.tenant_id, d.tipo, d.serie, d.numero, d.fecha_emision, d.estado, d.hash, d.nombre_archivo, d.intentos, d.ultimo_error,
               d.cdr_codigo, d.cdr_descripcion, d.cdr_observaciones::text AS cdr_obs, d.xml_key, d.cdr_key,
               c.tipo_operacion, c.moneda, c.receptor_tipo_doc, c.receptor_num_doc, c.receptor_nombre, c.receptor_direccion
        FROM documento d JOIN comprobante c ON c.documento_id = d.id
        """;

    private Comprobante mapear(ResultSet rs, int i) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<Item> items = jdbc.query("SELECT codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv FROM comprobante_item WHERE comprobante_id = ? ORDER BY orden",
                (r, k) -> new Item(r.getString("codigo"), r.getString("descripcion"), r.getString("unidad"), r.getBigDecimal("cantidad"),
                        r.getBigDecimal("precio_unitario"), TipoAfectacionIgv.porCodigo(r.getString("tipo_afectacion_igv"))), id);
        Cdr cdr = rs.getString("cdr_codigo") == null ? null : new Cdr(rs.getString("cdr_codigo"), rs.getString("cdr_descripcion"), deJson(rs.getString("cdr_obs")));
        return Comprobante.rehidratar(id, rs.getObject("tenant_id", UUID.class), TipoDocumento.porCodigo(rs.getString("tipo")), rs.getString("serie"),
                rs.getLong("numero"), rs.getDate("fecha_emision").toLocalDate(), rs.getString("moneda"), rs.getString("tipo_operacion"),
                new Receptor(rs.getString("receptor_tipo_doc"), rs.getString("receptor_num_doc"), rs.getString("receptor_nombre"), rs.getString("receptor_direccion")),
                items, EstadoDocumento.valueOf(rs.getString("estado")), rs.getString("hash"), rs.getString("nombre_archivo"),
                rs.getString("xml_key"), rs.getString("cdr_key"), cdr, rs.getInt("intentos"), rs.getString("ultimo_error"));
    }

    /** JSON mínimo para una lista de strings (sin dependencia de Jackson en este módulo). */
    static String aJson(List<String> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int k = 0; k < l.size(); k++) { if (k > 0) sb.append(','); sb.append('"').append(l.get(k).replace("\\", "\\\\").replace("\"", "\\\"")).append('"'); }
        return sb.append(']').toString();
    }
    static List<String> deJson(String json) {
        if (json == null || json.length() <= 2) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : json.substring(1, json.length() - 1).split("\",\"")) out.add(s.replaceAll("^\"|\"$", "").replace("\\\"", "\"").replace("\\\\", "\\"));
        return out;
    }
}
