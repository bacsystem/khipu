package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@RequiredArgsConstructor
public class JdbcComprobanteRepository implements ComprobanteRepository {
    private final JdbcTemplate jdbc;

    /** Estados desde los que un envío en curso puede escribir ERROR_ENVIO o ENVIADO sin pisar un estado terminal. */
    private static final String ESTADOS_DE_ENVIO = "('FIRMADO','ERROR_ENVIO','ENVIADO')";

    @Override public void guardar(Comprobante c) {
        // Un envío tardío (p. ej. el worker y una llamada manual en paralelo) no debe sobreescribir un
        // ACEPTADO/RECHAZADO ya persistido: al guardar ERROR_ENVIO o ENVIADO la actualización es condicional.
        boolean condicional = c.estado() == EstadoDocumento.ERROR_ENVIO || c.estado() == EstadoDocumento.ENVIADO;
        int filas = jdbc.update("""
            UPDATE documento SET estado = ?, hash = ?, ticket = NULL, intentos = ?, ultimo_error = ?, cdr_codigo = ?, cdr_descripcion = ?,
              cdr_observaciones = ?::jsonb, xml_key = ?, cdr_key = ?, updated_at = now() WHERE id = ? AND tenant_id = ?
            """ + (condicional ? " AND estado IN " + ESTADOS_DE_ENVIO : ""),
                c.estado().name(), c.hash(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : c.cdr().codigo(), c.cdr() == null ? null : c.cdr().descripcion(),
                c.cdr() == null ? null : aJson(c.cdr().observaciones()), c.xmlKey(), c.cdrKey(), c.id(), c.tenantId());
        if (filas > 0) return;
        if (condicional) throw new DomainException("ESTADO_CONFLICTO", "El comprobante cambió de estado en otra transacción");
        jdbc.update("""
            INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, hash, nombre_archivo, intentos, ultimo_error, xml_key, cdr_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.tenantId(), c.tipo().codigo(), c.serie(), c.numero(), Date.valueOf(c.fechaEmision()), c.estado().name(),
                c.hash(), c.nombreArchivo(), c.intentos(), c.ultimoError(), c.xmlKey(), c.cdrKey());
        Totales t = c.totales();
        jdbc.update("""
            INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, receptor_direccion,
              total_gravado, total_exonerado, total_inafecto, total_igv, total, forma_pago, monto_pendiente,
              descuento_global_tipo, descuento_global_valor, descuento_global_afecta_base) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.tipoOperacion(), c.moneda(), c.receptor().tipoDoc(), c.receptor().numDoc(), c.receptor().razonSocial(),
                c.receptor().direccion(), t.gravado(), t.exonerado(), t.inafecto(), t.igv(), t.total(),
                c.formaPago().tipo().name(), c.formaPago().montoPendiente(),
                tipo(c.descuentoGlobal()), valor(c.descuentoGlobal()), afectaBase(c.descuentoGlobal()));
        int nCuota = 1;
        for (FormaPago.Cuota q : c.formaPago().cuotas()) {
            jdbc.update("INSERT INTO comprobante_cuota (comprobante_id, orden, monto, vencimiento) VALUES (?, ?, ?, ?)",
                    c.id(), nCuota++, q.monto(), Date.valueOf(q.vencimiento()));
        }
        int orden = 1;
        for (Item i : c.items()) {
            jdbc.update("INSERT INTO comprobante_item (comprobante_id, orden, codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv, descuento_tipo, descuento_valor, descuento_afecta_base) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    c.id(), orden++, i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), i.afectacion().codigo(),
                    tipo(i.descuento()), valor(i.descuento()), afectaBase(i.descuento()));
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

    @Override public long contar(UUID tenantId, EstadoDocumento estado) {
        String sql = "SELECT count(*) FROM documento d WHERE d.tenant_id = ?" + (estado == null ? "" : " AND d.estado = ?");
        Object[] args = estado == null ? new Object[]{tenantId} : new Object[]{tenantId, estado.name()};
        Long total = jdbc.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }

    private static final String SELECT = """
        SELECT d.id, d.tenant_id, d.tipo, d.serie, d.numero, d.fecha_emision, d.estado, d.hash, d.nombre_archivo, d.intentos, d.ultimo_error,
               d.cdr_codigo, d.cdr_descripcion, d.cdr_observaciones::text AS cdr_obs, d.xml_key, d.cdr_key,
               c.tipo_operacion, c.moneda, c.receptor_tipo_doc, c.receptor_num_doc, c.receptor_nombre, c.receptor_direccion,
               c.forma_pago, c.monto_pendiente, c.descuento_global_tipo, c.descuento_global_valor, c.descuento_global_afecta_base
        FROM documento d JOIN comprobante c ON c.documento_id = d.id
        """;

    private Comprobante mapear(ResultSet rs, int i) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<Item> items = jdbc.query("SELECT codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv, descuento_tipo, descuento_valor, descuento_afecta_base FROM comprobante_item WHERE comprobante_id = ? ORDER BY orden",
                (r, k) -> new Item(r.getString("codigo"), r.getString("descripcion"), r.getString("unidad"), r.getBigDecimal("cantidad"),
                        r.getBigDecimal("precio_unitario"), TipoAfectacionIgv.porCodigo(r.getString("tipo_afectacion_igv")),
                        descuento(r.getString("descuento_tipo"), r.getBigDecimal("descuento_valor"), r.getObject("descuento_afecta_base", Boolean.class))), id);
        Cdr cdr = rs.getString("cdr_codigo") == null ? null : new Cdr(rs.getString("cdr_codigo"), rs.getString("cdr_descripcion"), deJson(rs.getString("cdr_obs")));
        return Comprobante.rehidratar(id, rs.getObject("tenant_id", UUID.class), TipoDocumento.porCodigo(rs.getString("tipo")), rs.getString("serie"),
                rs.getLong("numero"), rs.getDate("fecha_emision").toLocalDate(), rs.getString("moneda"), rs.getString("tipo_operacion"),
                new Receptor(rs.getString("receptor_tipo_doc"), rs.getString("receptor_num_doc"), rs.getString("receptor_nombre"), rs.getString("receptor_direccion")),
                items, formaPago(rs, id), descuento(rs.getString("descuento_global_tipo"), rs.getBigDecimal("descuento_global_valor"), rs.getObject("descuento_global_afecta_base", Boolean.class)),
                EstadoDocumento.valueOf(rs.getString("estado")), rs.getString("hash"), rs.getString("nombre_archivo"),
                rs.getString("xml_key"), rs.getString("cdr_key"), cdr, rs.getInt("intentos"), rs.getString("ultimo_error"));
    }

    private static String tipo(Descuento d) { return d == null ? null : d.tipo().name(); }
    private static java.math.BigDecimal valor(Descuento d) { return d == null ? null : d.valor(); }
    private static Boolean afectaBase(Descuento d) { return d == null ? null : d.afectaBaseIgv(); }

    private static Descuento descuento(String tipo, java.math.BigDecimal valor, Boolean afectaBase) {
        return tipo == null ? null : new Descuento(Descuento.Tipo.valueOf(tipo), sinCeros(valor), Boolean.TRUE.equals(afectaBase));
    }

    /** NUMERIC devuelve la escala de la columna (12.50000); los objetos de valor comparan escala, así que se normaliza. */
    private static java.math.BigDecimal sinCeros(java.math.BigDecimal v) {
        java.math.BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    private FormaPago formaPago(ResultSet rs, UUID id) throws SQLException {
        FormaPago.Tipo tipo = FormaPago.Tipo.valueOf(rs.getString("forma_pago"));
        if (tipo == FormaPago.Tipo.CONTADO) return FormaPago.contado();
        List<FormaPago.Cuota> cuotas = jdbc.query("SELECT monto, vencimiento FROM comprobante_cuota WHERE comprobante_id = ? ORDER BY orden",
                (r, k) -> new FormaPago.Cuota(r.getBigDecimal("monto"), r.getDate("vencimiento").toLocalDate()), id);
        return FormaPago.credito(rs.getBigDecimal("monto_pendiente"), cuotas);
    }

    /** JSON mínimo para una lista de strings (sin dependencia de Jackson en este módulo). */
    static String aJson(List<String> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int k = 0; k < l.size(); k++) {
            if (k > 0) sb.append(',');
            sb.append('"');
            escapar(l.get(k), sb);
            sb.append('"');
        }
        return sb.append(']').toString();
    }

    private static void escapar(String s, StringBuilder sb) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
    }

    /** Parser mínimo (máquina de estados) de un arreglo JSON de strings, sin dependencia de Jackson. */
    static List<String> deJson(String json) {
        if (json == null || json.length() <= 2) return List.of();
        List<String> out = new ArrayList<>();
        int i = 1;
        int n = json.length() - 1; // excluye ']'
        while (i < n) {
            while (i < n && json.charAt(i) != '"') i++;
            if (i >= n) break;
            i++; // salta comilla de apertura
            StringBuilder sb = new StringBuilder();
            while (i < n && json.charAt(i) != '"') {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < n) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case '\\' -> { sb.append('\\'); i += 2; }
                        case '"' -> { sb.append('"'); i += 2; }
                        case 'n' -> { sb.append('\n'); i += 2; }
                        case 'r' -> { sb.append('\r'); i += 2; }
                        case 't' -> { sb.append('\t'); i += 2; }
                        case 'b' -> { sb.append('\b'); i += 2; }
                        case 'f' -> { sb.append('\f'); i += 2; }
                        case 'u' -> {
                            String hex = json.substring(i + 2, i + 6);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 6;
                        }
                        default -> { sb.append(next); i += 2; }
                    }
                } else {
                    sb.append(c);
                    i++;
                }
            }
            i++; // salta comilla de cierre
            out.add(sb.toString());
        }
        return out;
    }
}
