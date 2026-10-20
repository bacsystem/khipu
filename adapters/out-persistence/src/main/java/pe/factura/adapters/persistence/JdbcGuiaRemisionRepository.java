package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.GuiaRemisionRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/** Guías de remisión (#39): la cabecera en {@code guia_remision} y los bienes, vehículos, conductores y documentos relacionados en sus tablas. */
@RequiredArgsConstructor
public class JdbcGuiaRemisionRepository implements GuiaRemisionRepository {
    private final JdbcTemplate jdbc;

    private static final String RESUELTAS = "('ACEPTADO','ACEPTADO_CON_OBS','RECHAZADO')";

    @Override public void guardar(GuiaRemision g) {
        GuiaRemision.Envio e = g.envio();
        // Lo de la guía no cambia después de firmada: solo el estado y el envío. Una respuesta de SUNAT que llega tarde (dos envíos o dos consultas en paralelo)
        // no pisa una guía que otra transacción ya resolvió.
        String guardia = g.estado().esFinal() ? "" : " AND estado NOT IN " + RESUELTAS;
        int filas = jdbc.update("""
            UPDATE guia_remision SET estado = ?, hash = ?, xml_key = ?, cdr_key = ?, ticket = ?, fecha_recepcion = ?, intentos = ?, consultas_ticket = ?, reintento_automatico = ?,
              ultimo_error = ?, cdr_codigo = ?, cdr_descripcion = ?, cdr_observaciones = ?::jsonb, enlace_consulta = ?, updated_at = now()
            WHERE id = ? AND tenant_id = ?""" + guardia,
                g.estado().name(), e.hash(), e.xmlKey(), e.cdrKey(), e.ticket(), e.fechaRecepcion() == null ? null : Timestamp.valueOf(e.fechaRecepcion()), e.intentos(), e.consultasTicket(),
                e.reintentoAutomatico(), e.ultimoError(), e.cdr() == null ? null : e.cdr().codigo(), e.cdr() == null ? null : e.cdr().descripcion(),
                e.cdr() == null ? null : JdbcComprobanteRepository.aJson(e.cdr().observaciones()), e.enlaceConsulta(), g.id(), g.tenantId());
        if (filas > 0) return;
        if (existe(g)) throw new DomainException("ESTADO_CONFLICTO", "La guía " + g.nombreArchivo() + " ya la resolvió otra transacción");
        insertar(g, e);
    }

    private boolean existe(GuiaRemision g) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM guia_remision WHERE id = ? AND tenant_id = ?)", Boolean.class, g.id(), g.tenantId()));
    }

    private void insertar(GuiaRemision g, GuiaRemision.Envio e) {
        DatosDeGuia.DeRemitente r = g.remitente().orElse(null);
        DatosDeGuia.DeTransportista tr = g.transportista().orElse(null);
        TransportistaContratado tc = r == null ? null : r.transportista();
        Traslado t = g.traslado();
        PuntoDeTraslado p = t.partida(), l = t.llegada();
        jdbc.update("""
            INSERT INTO guia_remision (id, tenant_id, tipo, serie, numero, fecha_emision, hora_emision, estado, destinatario_tipo_doc, destinatario_num_doc, destinatario_nombre,
              motivo, descripcion_motivo, modalidad, transportista_ruc, transportista_razon_social, transportista_mtc, remitente_tipo_doc, remitente_num_doc, remitente_nombre, registro_mtc,
              fecha_inicio_traslado, peso_bruto, unidad_peso, partida_ubigeo, partida_direccion, partida_establecimiento, partida_ruc,
              llegada_ubigeo, llegada_direccion, llegada_establecimiento, llegada_ruc, indicadores, observaciones, nombre_archivo, hash, xml_key, cdr_key, ticket, fecha_recepcion,
              intentos, consultas_ticket, reintento_automatico, ultimo_error, cdr_codigo, cdr_descripcion, cdr_observaciones, enlace_consulta)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)""",
                g.id(), g.tenantId(), g.tipo().codigo(), g.serie(), g.numero(), Date.valueOf(g.fechaEmision()), Time.valueOf(g.horaEmision()), g.estado().name(),
                g.destinatario().tipoDoc(), g.destinatario().numDoc(), g.destinatario().nombre(),
                r == null ? null : r.motivo(), r == null ? null : r.descripcionMotivo(), r == null ? null : r.modalidad().codigo(),
                tc == null ? null : tc.ruc(), tc == null ? null : tc.razonSocial(), tc == null ? null : tc.registroMtc(),
                tr == null ? null : tr.remitente().tipoDoc(), tr == null ? null : tr.remitente().numDoc(), tr == null ? null : tr.remitente().nombre(), tr == null ? null : tr.registroMtc(),
                Date.valueOf(t.fechaInicio()), t.pesoBruto(), t.unidadPeso(), p.ubigeo(), p.direccion(), p.codigoEstablecimiento(), p.rucEstablecimiento(),
                l.ubigeo(), l.direccion(), l.codigoEstablecimiento(), l.rucEstablecimiento(),
                t.indicadores().isEmpty() ? null : t.indicadores().stream().sorted().map(IndicadorDeTraslado::name).collect(Collectors.joining(",")),
                g.observaciones(), e.nombreArchivo(), e.hash(), e.xmlKey(), e.cdrKey(), e.ticket(), e.fechaRecepcion() == null ? null : Timestamp.valueOf(e.fechaRecepcion()),
                e.intentos(), e.consultasTicket(), e.reintentoAutomatico(), e.ultimoError(), e.cdr() == null ? null : e.cdr().codigo(), e.cdr() == null ? null : e.cdr().descripcion(),
                e.cdr() == null ? null : JdbcComprobanteRepository.aJson(e.cdr().observaciones()), e.enlaceConsulta());
        int n = 0;
        for (ItemDeGuia i : g.items())
            jdbc.update("INSERT INTO guia_remision_item (guia_id, orden, codigo, descripcion, unidad, cantidad, codigo_sunat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    g.id(), n++, i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.codigoSunat());
        n = 0;
        for (Vehiculo v : t.vehiculos()) jdbc.update("INSERT INTO guia_remision_vehiculo (guia_id, orden, placa) VALUES (?, ?, ?)", g.id(), n++, v.placa());
        n = 0;
        for (Conductor c : t.conductores())
            jdbc.update("INSERT INTO guia_remision_conductor (guia_id, orden, tipo_doc, num_doc, nombres, apellidos, licencia) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    g.id(), n++, c.tipoDoc(), c.numDoc(), c.nombres(), c.apellidos(), c.licencia());
        n = 0;
        for (DocumentoDeTraslado d : g.documentos())
            jdbc.update("INSERT INTO guia_remision_documento (guia_id, orden, tipo, numero, ruc_emisor) VALUES (?, ?, ?, ?, ?)", g.id(), n++, d.tipo(), d.numero(), d.rucEmisor());
    }

    @Override public Optional<GuiaRemision> buscar(UUID tenantId, UUID id) {
        return cargar("SELECT * FROM guia_remision WHERE tenant_id = ? AND id = ?", tenantId, id).stream().findFirst();
    }

    @Override public boolean reservarEnvio(UUID tenantId, UUID id, java.time.Instant ahora, boolean forzar, java.time.Instant viejaAntesDe) {
        return jdbc.update("""
            UPDATE guia_remision SET estado = 'ENVIANDO', enviando_desde = ?, updated_at = now()
            WHERE id = ? AND tenant_id = ? AND (estado IN ('FIRMADO', 'ERROR_ENVIO') OR (? AND estado = 'ENVIANDO' AND enviando_desde < ?))""",
                Timestamp.from(ahora), id, tenantId, forzar, Timestamp.from(viejaAntesDe)) == 1;
    }

    @Override public List<GuiaRemision> listar(UUID tenantId, Filtro filtro, int pagina, int porPagina) {
        List<Object> args = new ArrayList<>();
        String donde = donde(tenantId, filtro == null ? Filtro.NINGUNO : filtro, args);
        args.add(porPagina);
        args.add((long) (Math.max(1, pagina) - 1) * porPagina);
        return cargar("SELECT * FROM guia_remision " + donde + " ORDER BY fecha_emision DESC, created_at DESC, numero DESC LIMIT ? OFFSET ?", args.toArray());
    }

    @Override public long contar(UUID tenantId, Filtro filtro) {
        List<Object> args = new ArrayList<>();
        String donde = donde(tenantId, filtro == null ? Filtro.NINGUNO : filtro, args);
        return jdbc.queryForObject("SELECT count(*) FROM guia_remision " + donde, Long.class, args.toArray());
    }

    private static String donde(UUID tenantId, Filtro f, List<Object> args) {
        StringBuilder sb = new StringBuilder("WHERE tenant_id = ?");
        args.add(tenantId);
        if (f.estado() != null) { sb.append(" AND estado = ?"); args.add(f.estado().name()); }
        if (f.tipo() != null) { sb.append(" AND tipo = ?"); args.add(f.tipo().codigo()); }
        if (f.serie() != null) { sb.append(" AND serie = ?"); args.add(f.serie()); }
        if (f.desde() != null) { sb.append(" AND fecha_emision >= ?"); args.add(Date.valueOf(f.desde())); }
        if (f.hasta() != null) { sb.append(" AND fecha_emision <= ?"); args.add(Date.valueOf(f.hasta())); }
        return sb.toString();
    }

    /** La cabecera leída, a la espera de sus filas hijas. */
    private record GuiaCruda(UUID id, UUID tenantId, String serie, long numero, java.time.LocalDate fecha, java.time.LocalTime hora, EstadoGuia estado, ParteDeGuia destinatario,
                             DatosDeGuia datos, java.time.LocalDate inicio, java.math.BigDecimal peso, String unidad, PuntoDeTraslado partida, PuntoDeTraslado llegada,
                             Set<IndicadorDeTraslado> indicadores, String observaciones, GuiaRemision.Envio envio) {}

    private List<GuiaRemision> cargar(String sql, Object... args) {
        List<GuiaCruda> crudas = jdbc.query(sql, (rs, i) -> cruda(rs), args);
        if (crudas.isEmpty()) return List.of();
        UUID[] ids = crudas.stream().map(GuiaCruda::id).toArray(UUID[]::new);
        Map<UUID, List<ItemDeGuia>> items = hijos("SELECT * FROM guia_remision_item WHERE guia_id = ANY(?) ORDER BY guia_id, orden", ids,
                rs -> new ItemDeGuia(rs.getString("codigo"), rs.getString("descripcion"), rs.getString("unidad"), normal(rs.getBigDecimal("cantidad")), rs.getString("codigo_sunat")));
        Map<UUID, List<Vehiculo>> vehiculos = hijos("SELECT * FROM guia_remision_vehiculo WHERE guia_id = ANY(?) ORDER BY guia_id, orden", ids, rs -> new Vehiculo(rs.getString("placa")));
        Map<UUID, List<Conductor>> conductores = hijos("SELECT * FROM guia_remision_conductor WHERE guia_id = ANY(?) ORDER BY guia_id, orden", ids,
                rs -> new Conductor(rs.getString("tipo_doc"), rs.getString("num_doc"), rs.getString("nombres"), rs.getString("apellidos"), rs.getString("licencia")));
        Map<UUID, List<DocumentoDeTraslado>> documentos = hijos("SELECT * FROM guia_remision_documento WHERE guia_id = ANY(?) ORDER BY guia_id, orden", ids,
                rs -> new DocumentoDeTraslado(rs.getString("tipo"), rs.getString("numero"), rs.getString("ruc_emisor")));
        List<GuiaRemision> guias = new ArrayList<>(crudas.size());
        for (GuiaCruda c : crudas) {
            Traslado t = new Traslado(c.inicio(), c.peso(), c.unidad(), null, c.partida(), c.llegada(), vehiculos.getOrDefault(c.id(), List.of()),
                    conductores.getOrDefault(c.id(), List.of()), c.indicadores());
            guias.add(GuiaRemision.rehidratar(c.id(), c.tenantId(), c.serie(), c.numero(), c.fecha(), c.hora(), c.destinatario(), c.datos(), t,
                    items.getOrDefault(c.id(), List.of()), documentos.getOrDefault(c.id(), List.of()), c.observaciones(), c.estado(), c.envio()));
        }
        return guias;
    }

    /** Sin los ceros que agrega la escala de la columna (12.5000000000 → 12.5), y sin pasar a notación exponencial (10 sigue siendo 10, no 1E+1). */
    static java.math.BigDecimal normal(java.math.BigDecimal n) {
        java.math.BigDecimal s = n.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    private interface Fila<T> { T de(ResultSet rs) throws SQLException; }

    private <T> Map<UUID, List<T>> hijos(String sql, UUID[] ids, Fila<T> fila) {
        Map<UUID, List<T>> r = new HashMap<>();
        jdbc.query(sql, ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", ids)),
                rs -> { r.computeIfAbsent(rs.getObject("guia_id", UUID.class), k -> new ArrayList<>()).add(fila.de(rs)); });
        return r;
    }

    private static GuiaCruda cruda(ResultSet rs) throws SQLException {
        String tipo = rs.getString("tipo");
        DatosDeGuia datos = "09".equals(tipo)
                ? new DatosDeGuia.DeRemitente(rs.getString("motivo"), rs.getString("descripcion_motivo"), ModalidadTraslado.porCodigo(rs.getString("modalidad")),
                    rs.getString("transportista_ruc") == null ? null : new TransportistaContratado(rs.getString("transportista_ruc"), rs.getString("transportista_razon_social"), rs.getString("transportista_mtc")))
                : new DatosDeGuia.DeTransportista(new ParteDeGuia(rs.getString("remitente_tipo_doc"), rs.getString("remitente_num_doc"), rs.getString("remitente_nombre")), rs.getString("registro_mtc"));
        String ind = rs.getString("indicadores");
        Set<IndicadorDeTraslado> indicadores = ind == null || ind.isBlank() ? Set.of()
                : Arrays.stream(ind.split(",")).map(IndicadorDeTraslado::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(IndicadorDeTraslado.class)));
        Timestamp recepcion = rs.getTimestamp("fecha_recepcion");
        Cdr cdr = rs.getString("cdr_codigo") == null ? null : new Cdr(rs.getString("cdr_codigo"), rs.getString("cdr_descripcion"), JdbcComprobanteRepository.deJson(rs.getString("cdr_observaciones")));
        GuiaRemision.Envio envio = new GuiaRemision.Envio(rs.getString("nombre_archivo"), rs.getString("hash"), rs.getString("xml_key"), rs.getString("cdr_key"), cdr,
                rs.getString("ticket"), recepcion == null ? null : recepcion.toLocalDateTime(), rs.getInt("intentos"), rs.getInt("consultas_ticket"), rs.getString("ultimo_error"),
                rs.getBoolean("reintento_automatico"), rs.getString("enlace_consulta"));
        return new GuiaCruda(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("serie"), rs.getLong("numero"),
                rs.getDate("fecha_emision").toLocalDate(), rs.getTime("hora_emision").toLocalTime(), EstadoGuia.valueOf(rs.getString("estado")),
                new ParteDeGuia(rs.getString("destinatario_tipo_doc"), rs.getString("destinatario_num_doc"), rs.getString("destinatario_nombre")), datos,
                rs.getDate("fecha_inicio_traslado").toLocalDate(), normal(rs.getBigDecimal("peso_bruto")), rs.getString("unidad_peso"),
                new PuntoDeTraslado(rs.getString("partida_ubigeo"), rs.getString("partida_direccion"), rs.getString("partida_establecimiento"), rs.getString("partida_ruc")),
                new PuntoDeTraslado(rs.getString("llegada_ubigeo"), rs.getString("llegada_direccion"), rs.getString("llegada_establecimiento"), rs.getString("llegada_ruc")),
                indicadores, rs.getString("observaciones"), envio);
    }
}
