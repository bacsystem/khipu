package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
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
            INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, hora_emision, estado, hash, nombre_archivo, intentos, ultimo_error, xml_key, cdr_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.tenantId(), c.tipo().codigo(), c.serie(), c.numero(), Date.valueOf(c.fechaEmision()), c.horaEmision() == null ? null : java.sql.Time.valueOf(c.horaEmision()), c.estado().name(),
                c.hash(), c.nombreArchivo(), c.intentos(), c.ultimoError(), c.xmlKey(), c.cdrKey());
        Totales t = c.totales();
        Detraccion d = c.detraccion();
        RetencionIgv r = c.retencion();
        Percepcion pc = c.percepcion();
        Nota n = c.nota();
        jdbc.update("""
            INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, receptor_direccion,
              total_gravado, total_exonerado, total_inafecto, total_igv, total, forma_pago, monto_pendiente,
              descuento_global_tipo, descuento_global_valor, descuento_global_afecta_base,
              detraccion_codigo, detraccion_porcentaje, detraccion_monto, detraccion_cuenta, detraccion_medio_pago,
              retencion_porcentaje, retencion_monto, percepcion_regimen, percepcion_porcentaje, percepcion_base, percepcion_monto, orden_compra,
              fecha_vencimiento, redondeo, nota_tipo_afectado, nota_serie_afectada, nota_numero_afectado, nota_motivo, nota_descripcion, observaciones, tasa_igv, leyendas)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, c.id(), c.tipoOperacion(), c.moneda(), c.receptor().tipoDoc(), c.receptor().numDoc(), c.receptor().razonSocial(),
                c.receptor().direccion(), t.gravado(), t.exonerado(), t.inafecto(), t.igv(), t.total(),
                c.formaPago().tipo().name(), c.formaPago().montoPendiente(),
                tipo(c.descuentoGlobal()), valor(c.descuentoGlobal()), afectaBase(c.descuentoGlobal()),
                d == null ? null : d.codigoBienServicio(), d == null ? null : d.porcentaje(), d == null ? null : d.monto(),
                d == null ? null : d.cuentaBancoNacion(), d == null ? null : d.medioPago(),
                r == null ? null : r.porcentaje(), r == null ? null : r.monto(),
                pc == null ? null : pc.regimen(), pc == null ? null : pc.porcentaje(), pc == null ? null : pc.base(), pc == null ? null : pc.monto(),
                c.referencias().ordenCompra(), c.fechaVencimiento() == null ? null : Date.valueOf(c.fechaVencimiento()), t.tieneRedondeo() ? t.redondeo() : null,
                n == null ? null : n.tipoAfectado().codigo(), n == null ? null : n.serieAfectada(), n == null ? null : n.numeroAfectado(), n == null ? null : n.motivo(), n == null ? null : n.descripcion(),
                c.observaciones(), c.tasaIgv(), c.leyendas().isEmpty() ? null : String.join(",", c.leyendas()));
        int nDoc = 1;
        for (GuiaRelacionada g : c.referencias().guias()) {
            jdbc.update("INSERT INTO comprobante_documento_relacionado (comprobante_id, orden, clase, tipo, numero) VALUES (?, ?, 'GUIA', ?, ?)", c.id(), nDoc++, g.tipo(), g.numero());
        }
        for (DocumentoRelacionado dr : c.referencias().otros()) {
            jdbc.update("INSERT INTO comprobante_documento_relacionado (comprobante_id, orden, clase, tipo, numero) VALUES (?, ?, 'OTRO', ?, ?)", c.id(), nDoc++, dr.tipo(), dr.numero());
        }
        int nCuota = 1;
        for (FormaPago.Cuota q : c.formaPago().cuotas()) {
            jdbc.update("INSERT INTO comprobante_cuota (comprobante_id, orden, monto, vencimiento) VALUES (?, ?, ?, ?)",
                    c.id(), nCuota++, q.monto(), Date.valueOf(q.vencimiento()));
        }
        int nAnticipo = 1;
        for (Anticipo a : c.anticipos()) {
            jdbc.update("INSERT INTO comprobante_anticipo (comprobante_id, orden, serie, numero, monto, afectacion, fecha_pago) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    c.id(), nAnticipo++, a.serie(), a.numero(), a.monto(), a.afectacion().name(), a.fechaPago() == null ? null : Date.valueOf(a.fechaPago()));
        }
        int nCargo = 1;
        for (Cargo cg : c.cargos()) {
            jdbc.update("INSERT INTO comprobante_cargo (comprobante_id, item_orden, orden, codigo, tipo, valor) VALUES (?, NULL, ?, ?, ?, ?)",
                    c.id(), nCargo++, cg.codigo(), cg.tipo().name(), cg.valor());
        }
        int orden = 1;
        for (Item i : c.items()) {
            for (Cargo cg : i.cargos()) {
                jdbc.update("INSERT INTO comprobante_cargo (comprobante_id, item_orden, orden, codigo, tipo, valor) VALUES (?, ?, ?, ?, ?, ?)",
                        c.id(), orden, nCargo++, cg.codigo(), cg.tipo().name(), cg.valor());
            }
            jdbc.update("INSERT INTO comprobante_item (comprobante_id, orden, codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv, descuento_tipo, descuento_valor, descuento_afecta_base, isc_sistema, isc_tasa, isc_monto_unitario, icbper, codigo_sunat, gtin_tipo, gtin, isc_base_pvp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    c.id(), orden++, i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), i.afectacion().codigo(),
                    tipo(i.descuento()), valor(i.descuento()), afectaBase(i.descuento()),
                    i.isc() == null ? null : i.isc().sistema(), i.isc() == null ? null : i.isc().tasa(), i.isc() == null ? null : i.isc().montoUnitario(), i.icbper(),
                    i.tieneCodigoSunat() ? i.codigoSunat().codigo() : null, i.gtin() == null ? null : i.gtin().tipo(), i.gtin() == null ? null : i.gtin().codigo(),
                    i.isc() == null ? null : i.isc().basePvp());
        }
    }

    @Override public Optional<Comprobante> buscar(UUID tenantId, UUID id) {
        return jdbc.query(SELECT + " WHERE d.id = ? AND d.tenant_id = ?", this::mapear, id, tenantId).stream().findFirst();
    }
    @Override public Optional<Comprobante> bloquear(UUID tenantId, UUID id) {
        return jdbc.query(SELECT + " WHERE d.id = ? AND d.tenant_id = ? FOR UPDATE OF d", this::mapear, id, tenantId).stream().findFirst();
    }
    @Override public BigDecimal montoRegularizado(UUID tenantId, String serieAnticipo, long numeroAnticipo) {
        // Un final RECHAZADO o INVALIDO no regularizó nada: su anticipo vuelve a estar disponible.
        BigDecimal suma = jdbc.queryForObject("""
            SELECT coalesce(sum(a.monto), 0) FROM comprobante_anticipo a JOIN documento d ON d.id = a.comprobante_id
            WHERE d.tenant_id = ? AND a.serie = ? AND a.numero = ? AND d.estado NOT IN ('RECHAZADO', 'INVALIDO')
            """, BigDecimal.class, tenantId, serieAnticipo, numeroAnticipo);
        return suma == null ? BigDecimal.ZERO : suma;
    }
    @Override public Optional<Comprobante> buscarPorNumero(UUID tenantId, TipoDocumento tipo, String serie, long numero) {
        return jdbc.query(SELECT + " WHERE d.tenant_id = ? AND d.tipo = ? AND d.serie = ? AND d.numero = ?", this::mapear, tenantId, tipo.codigo(), serie, numero).stream().findFirst();
    }
    @Override public Optional<Comprobante> bloquearPorNumero(UUID tenantId, TipoDocumento tipo, String serie, long numero) {
        return jdbc.query(SELECT + " WHERE d.tenant_id = ? AND d.tipo = ? AND d.serie = ? AND d.numero = ? FOR UPDATE OF d", this::mapear, tenantId, tipo.codigo(), serie, numero).stream().findFirst();
    }
    @Override public List<Comprobante> notasDe(UUID tenantId, String serie, long numero) {
        return jdbc.query(SELECT + " WHERE d.tenant_id = ? AND c.nota_serie_afectada = ? AND c.nota_numero_afectado = ? ORDER BY d.created_at", this::mapear, tenantId, serie, numero);
    }
    @Override public List<Comprobante> pendientesDeEnvioEmitidosHasta(java.time.LocalDate fechaEmisionMaxima) {
        return jdbc.query(SELECT + " WHERE d.estado IN ('FIRMADO', 'ERROR_ENVIO') AND d.fecha_emision <= ? ORDER BY d.fecha_emision", this::mapear, Date.valueOf(fechaEmisionMaxima));
    }
    @Override public List<Comprobante> pendientesDeCdr() {
        return jdbc.query(SELECT + " WHERE d.xml_key IS NOT NULL AND d.cdr_key IS NULL AND d.estado IN ('ENVIADO', 'ERROR_ENVIO', 'ACEPTADO', 'ACEPTADO_CON_OBS', 'RECHAZADO') ORDER BY d.fecha_emision", this::mapear);
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
        SELECT d.id, d.tenant_id, d.tipo, d.serie, d.numero, d.fecha_emision, d.hora_emision, d.estado, d.hash, d.nombre_archivo, d.intentos, d.ultimo_error,
               d.cdr_codigo, d.cdr_descripcion, d.cdr_observaciones::text AS cdr_obs, d.xml_key, d.cdr_key,
               c.tipo_operacion, c.moneda, c.receptor_tipo_doc, c.receptor_num_doc, c.receptor_nombre, c.receptor_direccion,
               c.forma_pago, c.monto_pendiente, c.descuento_global_tipo, c.descuento_global_valor, c.descuento_global_afecta_base,
               c.detraccion_codigo, c.detraccion_porcentaje, c.detraccion_monto, c.detraccion_cuenta, c.detraccion_medio_pago,
               c.retencion_porcentaje, c.retencion_monto, c.percepcion_regimen, c.percepcion_porcentaje, c.percepcion_base, c.percepcion_monto, c.orden_compra, c.fecha_vencimiento, c.redondeo,
               c.nota_tipo_afectado, c.nota_serie_afectada, c.nota_numero_afectado, c.nota_motivo, c.nota_descripcion, c.observaciones, c.tasa_igv, c.leyendas
        FROM documento d JOIN comprobante c ON c.documento_id = d.id
        """;

    private Comprobante mapear(ResultSet rs, int i) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        // Cargos por nivel: clave null = globales, n = línea n (orden del ítem).
        Map<Integer, List<Cargo>> cargos = new HashMap<>();
        jdbc.query("SELECT item_orden, codigo, tipo, valor FROM comprobante_cargo WHERE comprobante_id = ? ORDER BY orden",
                (RowCallbackHandler) r -> { cargos.computeIfAbsent(r.getObject("item_orden", Integer.class), k -> new ArrayList<>())
                        .add(new Cargo(r.getString("codigo"), Cargo.Tipo.valueOf(r.getString("tipo")), sinCeros(r.getBigDecimal("valor")))); }, id);
        List<Item> items = jdbc.query("SELECT orden, codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv, descuento_tipo, descuento_valor, descuento_afecta_base, isc_sistema, isc_tasa, isc_monto_unitario, isc_base_pvp, icbper, codigo_sunat, gtin_tipo, gtin FROM comprobante_item WHERE comprobante_id = ? ORDER BY orden",
                (r, k) -> new Item(r.getString("codigo"), r.getString("descripcion"), r.getString("unidad"), r.getBigDecimal("cantidad"),
                        r.getBigDecimal("precio_unitario"), TipoAfectacionIgv.porCodigo(r.getString("tipo_afectacion_igv")),
                        descuento(r.getString("descuento_tipo"), r.getBigDecimal("descuento_valor"), r.getObject("descuento_afecta_base", Boolean.class)),
                        r.getString("isc_sistema") == null ? null : new Isc(r.getString("isc_sistema"), r.getBigDecimal("isc_tasa") == null ? null : sinCeros(r.getBigDecimal("isc_tasa")),
                                r.getBigDecimal("isc_monto_unitario") == null ? null : sinCeros(r.getBigDecimal("isc_monto_unitario")),
                                r.getBigDecimal("isc_base_pvp") == null ? null : sinCeros(r.getBigDecimal("isc_base_pvp"))),
                        r.getBoolean("icbper"), cargos.getOrDefault(r.getInt("orden"), List.of()),
                        CodigoProductoSunat.de(r.getString("codigo_sunat")), r.getString("gtin_tipo") == null ? null : new Gtin(r.getString("gtin_tipo"), r.getString("gtin"))), id);
        List<Anticipo> anticipos = jdbc.query("SELECT serie, numero, monto, afectacion, fecha_pago FROM comprobante_anticipo WHERE comprobante_id = ? ORDER BY orden",
                (r, k) -> new Anticipo(r.getString("serie"), r.getLong("numero"), r.getBigDecimal("monto"), Anticipo.Afectacion.valueOf(r.getString("afectacion")),
                        r.getDate("fecha_pago") == null ? null : r.getDate("fecha_pago").toLocalDate()), id);
        List<GuiaRelacionada> guias = new ArrayList<>();
        List<DocumentoRelacionado> otros = new ArrayList<>();
        jdbc.query("SELECT clase, tipo, numero FROM comprobante_documento_relacionado WHERE comprobante_id = ? ORDER BY orden", (RowCallbackHandler) r -> {
            switch (r.getString("clase")) {
                case "GUIA" -> guias.add(new GuiaRelacionada(r.getString("tipo"), r.getString("numero")));
                case "OTRO" -> otros.add(new DocumentoRelacionado(r.getString("tipo"), r.getString("numero")));
                default -> throw new IllegalStateException("Clase de documento relacionado desconocida en comprobante " + id + ": " + r.getString("clase"));
            }
        }, id);
        Referencias referencias = new Referencias(rs.getString("orden_compra"), guias, otros);
        Cdr cdr = rs.getString("cdr_codigo") == null ? null : new Cdr(rs.getString("cdr_codigo"), rs.getString("cdr_descripcion"), deJson(rs.getString("cdr_obs")));
        Comprobante c = Comprobante.persistido(id, rs.getObject("tenant_id", UUID.class), TipoDocumento.porCodigo(rs.getString("tipo")), rs.getString("serie"), rs.getLong("numero"), rs.getDate("fecha_emision").toLocalDate(), EstadoDocumento.valueOf(rs.getString("estado")), new Receptor(rs.getString("receptor_tipo_doc"), rs.getString("receptor_num_doc"), rs.getString("receptor_nombre"), rs.getString("receptor_direccion")), items)
                .horaEmision(rs.getTime("hora_emision") == null ? null : rs.getTime("hora_emision").toLocalTime())
                .fechaVencimiento(rs.getDate("fecha_vencimiento") == null ? null : rs.getDate("fecha_vencimiento").toLocalDate())
                .moneda(rs.getString("moneda"))
                .tipoOperacion(rs.getString("tipo_operacion"))
                .formaPago(formaPago(rs, id))
                .descuentoGlobal(descuento(rs.getString("descuento_global_tipo"), rs.getBigDecimal("descuento_global_valor"), rs.getObject("descuento_global_afecta_base", Boolean.class)))
                .cargos(cargos.getOrDefault(null, List.of()))
                .detraccion(detraccion(rs))
                .retencion(retencion(rs))
                .percepcion(percepcion(rs))
                .anticipos(anticipos)
                .referencias(referencias)
                .redondeo(rs.getBigDecimal("redondeo"))
                .nota(nota(rs))
                .tasaIgv(rs.getBigDecimal("tasa_igv"))
                .leyendas(rs.getString("leyendas") == null ? List.of() : List.of(rs.getString("leyendas").split(",")))
                .firma(rs.getString("hash"), rs.getString("nombre_archivo"), rs.getString("xml_key"))
                .cdr(cdr, rs.getString("cdr_key"))
                .envio(rs.getInt("intentos"), rs.getString("ultimo_error"))
                .rehidratar();
        c.anotar(rs.getString("observaciones"));
        return c;
    }

    private static Nota nota(ResultSet rs) throws SQLException {
        return rs.getString("nota_motivo") == null ? null
                : new Nota(TipoDocumento.porCodigo(rs.getString("nota_tipo_afectado")), rs.getString("nota_serie_afectada"), rs.getLong("nota_numero_afectado"),
                        rs.getString("nota_motivo"), rs.getString("nota_descripcion"));
    }

    private static String tipo(Descuento d) { return d == null ? null : d.tipo().name(); }
    private static java.math.BigDecimal valor(Descuento d) { return d == null ? null : d.valor(); }
    private static Boolean afectaBase(Descuento d) { return d == null ? null : d.afectaBaseIgv(); }

    private static Descuento descuento(String tipo, java.math.BigDecimal valor, Boolean afectaBase) {
        return tipo == null ? null : new Descuento(Descuento.Tipo.valueOf(tipo), sinCeros(valor), Boolean.TRUE.equals(afectaBase));
    }

    private static RetencionIgv retencion(ResultSet rs) throws SQLException {
        return rs.getBigDecimal("retencion_monto") == null ? null : new RetencionIgv(sinCeros(rs.getBigDecimal("retencion_porcentaje")), rs.getBigDecimal("retencion_monto"));
    }

    private static Percepcion percepcion(ResultSet rs) throws SQLException {
        return rs.getString("percepcion_regimen") == null ? null
                : new Percepcion(rs.getString("percepcion_regimen"), sinCeros(rs.getBigDecimal("percepcion_porcentaje")), rs.getBigDecimal("percepcion_base"), rs.getBigDecimal("percepcion_monto"));
    }

    /** NUMERIC devuelve la escala de la columna (12.50000); los objetos de valor comparan escala, así que se normaliza. */
    private static java.math.BigDecimal sinCeros(java.math.BigDecimal v) {
        java.math.BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    private static Detraccion detraccion(ResultSet rs) throws SQLException {
        if (rs.getString("detraccion_codigo") == null) return null;
        return new Detraccion(rs.getString("detraccion_codigo"), sinCeros(rs.getBigDecimal("detraccion_porcentaje")),
                rs.getBigDecimal("detraccion_monto"), rs.getString("detraccion_cuenta"), rs.getString("detraccion_medio_pago"));
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
