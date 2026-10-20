package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.out.ComprobanteRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.webhook.EventoWebhook;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
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
        // Descartar (#196) también es condicional y más estricto: solo se descarta lo que sigue en error de envío en la base, no lo que un envío en paralelo ya resolvió.
        String guardia = c.estado() == EstadoDocumento.DESCARTADO ? " AND estado = 'ERROR_ENVIO'"
                : c.estado() == EstadoDocumento.ERROR_ENVIO || c.estado() == EstadoDocumento.ENVIADO ? " AND estado IN " + ESTADOS_DE_ENVIO : "";
        boolean condicional = !guardia.isEmpty();
        int filas = jdbc.update("""
            UPDATE documento SET estado = ?, hash = ?, ticket = NULL, intentos = ?, ultimo_error = ?, cdr_codigo = ?, cdr_descripcion = ?,
              cdr_observaciones = ?::jsonb, xml_key = ?, cdr_key = ?, updated_at = now() WHERE id = ? AND tenant_id = ?
            """ + guardia,
                c.estado().name(), c.hash(), c.intentos(), c.ultimoError(),
                c.cdr() == null ? null : c.cdr().codigo(), c.cdr() == null ? null : c.cdr().descripcion(),
                c.cdr() == null ? null : aJson(c.cdr().observaciones()), c.xmlKey(), c.cdrKey(), c.id(), c.tenantId());
        if (filas > 0) { registrarEventos(c); return; }
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
        LiquidacionCompra lc = c.liquidacion();
        jdbc.update("""
            INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, receptor_direccion,
              total_gravado, total_exonerado, total_inafecto, total_igv, total, forma_pago, monto_pendiente,
              descuento_global_tipo, descuento_global_valor, descuento_global_afecta_base,
              detraccion_codigo, detraccion_porcentaje, detraccion_monto, detraccion_cuenta, detraccion_medio_pago,
              retencion_porcentaje, retencion_monto, percepcion_regimen, percepcion_porcentaje, percepcion_base, percepcion_monto, orden_compra,
              fecha_vencimiento, redondeo, nota_tipo_afectado, nota_serie_afectada, nota_numero_afectado, nota_motivo, nota_descripcion, observaciones, tasa_igv, leyendas,
              receptor_pais, incoterm, pais_uso, tipo_cambio, vendedor_ubigeo, vendedor_condicion, lugar_direccion, lugar_ubigeo, lugar_condicion, retencion_renta_tasa,
              receptor_email)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                c.observaciones(), c.tasaIgv(), c.leyendas().isEmpty() ? null : String.join(",", c.leyendas()),
                c.receptor().pais(), c.exportacion() == null ? null : c.exportacion().incoterm(), c.exportacion() == null ? null : c.exportacion().paisUso(), c.tipoCambio(),
                lc == null ? null : lc.ubigeoVendedor(), lc == null ? null : lc.condicionVendedor(), lc == null ? null : lc.lugarOperacion().direccion(),
                lc == null ? null : lc.lugarOperacion().ubigeo(), lc == null ? null : lc.lugarOperacion().condicion(), lc == null ? null : lc.tasaRetencionRenta(),
                c.correoCliente());
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
            jdbc.update("INSERT INTO comprobante_item (comprobante_id, orden, codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv, descuento_tipo, descuento_valor, descuento_afecta_base, isc_sistema, isc_tasa, isc_monto_unitario, icbper, codigo_sunat, gtin_tipo, gtin, isc_base_pvp, hidrobiologico, transporte, otros_tributos_tasa, placa) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)",
                    c.id(), orden++, i.codigo(), i.descripcion(), i.unidad(), i.cantidad(), i.precioUnitario(), i.afectacion().codigo(),
                    tipo(i.descuento()), valor(i.descuento()), afectaBase(i.descuento()),
                    i.isc() == null ? null : i.isc().sistema(), i.isc() == null ? null : i.isc().tasa(), i.isc() == null ? null : i.isc().montoUnitario(), i.icbper(),
                    i.tieneCodigoSunat() ? i.codigoSunat().codigo() : null, i.gtin() == null ? null : i.gtin().tipo(), i.gtin() == null ? null : i.gtin().codigo(),
                    i.isc() == null ? null : i.isc().basePvp(), DatosSectorialesJson.aJson(i.hidrobiologico()), DatosSectorialesJson.aJson(i.transporte()),
                    i.tieneOtrosTributos() ? i.otrosTributos().tasa() : null, i.placa());
        }
        registrarEventos(c);
    }

    /** Historial de intentos (#4): cada cambio de estado ocurrido en memoria se guarda con la hora del servidor, en orden. */
    private void registrarEventos(Comprobante c) {
        for (EventoDocumento e : c.eventosPendientes()) {
            jdbc.update("INSERT INTO evento_documento (documento_id, estado_anterior, estado_nuevo, detalle, ocurrido_en) VALUES (?, ?, ?, ?, clock_timestamp())",
                    c.id(), e.estadoAnterior() == null ? null : e.estadoAnterior().name(), e.estadoNuevo().name(), e.detalle());
            encolarWebhooks(c, e);
            anotarEnvioAlCliente(c, e);
        }
        c.eventosGuardados();
    }

    /**
     * #325: en la misma transacción que el paso a aceptado, el envío del comprobante a su cliente, si trae correo y la empresa tiene activado el envío automático (outbox
     * transaccional: si el estado no se guarda, tampoco el envío). Uno por comprobante: la clave primaria y el {@code ON CONFLICT DO NOTHING} hacen que un segundo paso a aceptado,
     * o una segunda réplica, no anote otro.
     */
    private void anotarEnvioAlCliente(Comprobante c, EventoDocumento e) {
        if (c.correoCliente() == null || !EnvioAlCliente.correspondeA(e)) return;
        jdbc.update("""
                INSERT INTO envio_al_cliente (documento_id, tenant_id, destinatario)
                SELECT ?, t.id, ? FROM tenant t WHERE t.id = ? AND t.envio_automatico_al_cliente
                ON CONFLICT (documento_id) DO NOTHING
                """, c.id(), c.correoCliente(), c.tenantId());
    }

    /**
     * Webhooks (#19): en la misma transacción que el cambio de estado, una entrega por cada endpoint de la empresa suscrito al evento, activo o pausado por khipu (el pausado
     * guarda en cola lo nuevo y lo manda al reactivarse; el que apagó una persona no recibe nada) (outbox transaccional: si el estado
     * no se guarda, tampoco el aviso). Un reintento que deja el mismo estado (ERROR_ENVIO tras ERROR_ENVIO) no es un cambio y no avisa. Sin endpoints es una consulta indexada por empresa.
     */
    private void encolarWebhooks(Comprobante c, EventoDocumento e) {
        if (e.estadoAnterior() == e.estadoNuevo()) return;
        EventoWebhook.deEstado(e.estadoNuevo()).ifPresent(evento -> jdbc.update("""
                INSERT INTO webhook_entrega (id, endpoint_id, tenant_id, evento, documento_id, payload)
                SELECT gen_random_uuid(), w.id, w.tenant_id, ?, ?, ? FROM webhook_endpoint w
                WHERE w.tenant_id = ? AND (w.activo OR w.pausado_motivo IS NOT NULL) AND position(',' || ? || ',' IN ',' || w.eventos || ',') > 0
                """, evento, c.id(), PayloadDeWebhook.de(evento, c, e, Instant.now()), c.tenantId(), evento));
    }

    @Override public List<EventoDocumento> eventosDe(UUID tenantId, UUID comprobanteId) {
        return jdbc.query("""
            SELECT e.estado_anterior, e.estado_nuevo, e.detalle, e.ocurrido_en FROM evento_documento e JOIN documento d ON d.id = e.documento_id
            WHERE d.id = ? AND d.tenant_id = ? ORDER BY e.ocurrido_en, e.id
            """, (r, k) -> new EventoDocumento(r.getString("estado_anterior") == null ? null : EstadoDocumento.valueOf(r.getString("estado_anterior")),
                        EstadoDocumento.valueOf(r.getString("estado_nuevo")), r.getString("detalle"), r.getTimestamp("ocurrido_en").toInstant()), comprobanteId, tenantId);
    }

    @Override public Optional<Comprobante> buscar(UUID tenantId, UUID id) {
        return jdbc.query(SELECT + " WHERE d.id = ? AND d.tenant_id = ?", this::mapear, id, tenantId).stream().findFirst();
    }
    @Override public Optional<Comprobante> bloquear(UUID tenantId, UUID id) {
        return jdbc.query(SELECT + " WHERE d.id = ? AND d.tenant_id = ? FOR UPDATE OF d", this::mapear, id, tenantId).stream().findFirst();
    }
    @Override public List<Comprobante> bloquearPorInformarDeLaFecha(UUID tenantId, java.time.LocalDate fechaEmision, UUID excluido, int limite) {
        if (limite < 1) return List.of();
        // Primero las filas libres, bloqueadas y sin esperar a las que otro ya tiene (SKIP LOCKED); luego se cargan completas (con sus hijos, de a lote) por id.
        List<UUID> ids = jdbc.queryForList("SELECT d.id " + DESDE + " WHERE d.tenant_id = ? AND d.id <> ? AND " + BoletasPorInformar.paraAgruparEnResumen("c")
                + " ORDER BY d.serie, d.numero LIMIT ? FOR UPDATE OF d SKIP LOCKED", UUID.class, tenantId, excluido, java.sql.Date.valueOf(fechaEmision), limite);
        if (ids.isEmpty()) return List.of();
        String marcas = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = new Object[ids.size() + 1];
        args[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) args[i + 1] = ids.get(i);
        return consultar(" WHERE d.tenant_id = ? AND d.id IN (" + marcas + ")", " ORDER BY d.serie, d.numero", args);
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
        return consultar(" WHERE d.tenant_id = ? AND c.nota_serie_afectada = ? AND c.nota_numero_afectado = ?", " ORDER BY d.created_at", tenantId, serie, numero);
    }
    /**
     * Fuera queda lo que va en el resumen diario ({@code Nota.vaEnResumenDiario}): la boleta y la nota B, salvo la NC 10 y la ND 03 (#331). Nunca vence, y sin este
     * filtro una boleta firmada que nadie envía entraría en cada barrido para siempre.
     */
    @Override public List<Comprobante> pendientesQueVencenEmitidosHasta(java.time.LocalDate fechaEmisionMaxima) {
        return consultar(" WHERE d.estado IN ('FIRMADO', 'ERROR_ENVIO') AND d.fecha_emision <= ?"
                + " AND d.tipo <> '03' AND (c.nota_tipo_afectado IS DISTINCT FROM '03' OR (d.tipo = '07' AND c.nota_motivo = '10') OR (d.tipo = '08' AND c.nota_motivo = '03'))"
                , " ORDER BY d.fecha_emision", Date.valueOf(fechaEmisionMaxima));
    }
    /**
     * Lo que SUNAT consulta por getStatusCdr: la factura y sus notas (#357). Fuera quedan la boleta y las notas B ({@code Comprobante.esDeBoleta}: tipo 03 o nota que
     * modifica una boleta), cuya constancia no se pide por serie y número, y la liquidación de compra (04, #327), que el servicio tampoco cubre.
     */
    @Override public List<Comprobante> pendientesDeCdr() {
        return consultar(" WHERE d.xml_key IS NOT NULL AND d.cdr_key IS NULL AND d.estado IN ('ENVIADO', 'ERROR_ENVIO', 'ACEPTADO', 'ACEPTADO_CON_OBS', 'RECHAZADO')"
                + " AND d.tipo NOT IN ('03', '04') AND c.nota_tipo_afectado IS DISTINCT FROM '03'", " ORDER BY d.fecha_emision");
    }
    @Override public List<Comprobante> firmadosEmitidosEntre(LocalDate desde, LocalDate hasta) {
        return consultar(" WHERE d.xml_key IS NOT NULL AND d.fecha_emision BETWEEN ? AND ?", " ORDER BY d.fecha_emision, d.created_at", Date.valueOf(desde), Date.valueOf(hasta));
    }
    @Override public List<Comprobante> listar(UUID tenantId, ConsultarComprobanteUseCase.Filtro filtro, int pagina, int porPagina) {
        List<Object> args = new ArrayList<>();
        String where = where(tenantId, filtro, args);
        args.add(porPagina); args.add((pagina - 1) * porPagina);
        // #321: con desempate por id, dos comprobantes creados en el mismo instante no cambian de lugar entre una página y la siguiente.
        return consultar(where, " ORDER BY d.created_at DESC, d.id DESC LIMIT ? OFFSET ?", args.toArray());
    }

    /**
     * #321: por llave ({@code (created_at, id) < (?, ?)}) en vez de {@code OFFSET}: que entren o salgan filas antes de la llave (un comprobante nuevo, otro que cambió de estado y
     * dejó de cumplir el filtro) no corre las páginas, así que lo que no cambia mientras se recorre sale exactamente una vez.
     */
    @Override public ConsultarComprobanteUseCase.PaginaPorLlave listarPorLlave(UUID tenantId, ConsultarComprobanteUseCase.Filtro filtro, Set<EstadoDocumento> excluidos,
                                                                         ConsultarComprobanteUseCase.Llave despues, int limite) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(where(tenantId, filtro, args)).append(sinEstados(excluidos, args));
        if (despues != null) {
            where.append(" AND (d.created_at, d.id) < (?, ?)");
            args.add(java.sql.Timestamp.from(despues.creadoEn()));
            args.add(despues.id());
        }
        args.add(limite);
        List<Comprobante> filas = consultar(where.toString(), " ORDER BY d.created_at DESC, d.id DESC LIMIT ?", args.toArray());
        if (filas.isEmpty()) return new ConsultarComprobanteUseCase.PaginaPorLlave(filas, null);
        UUID ultimo = filas.get(filas.size() - 1).id();
        Instant creado = jdbc.queryForObject("SELECT created_at FROM documento WHERE id = ?", java.sql.Timestamp.class, ultimo).toInstant();
        return new ConsultarComprobanteUseCase.PaginaPorLlave(filas, new ConsultarComprobanteUseCase.Llave(creado, ultimo));
    }

    @Override public long contar(UUID tenantId, ConsultarComprobanteUseCase.Filtro filtro, Set<EstadoDocumento> excluidos) {
        List<Object> args = new ArrayList<>();
        String where = where(tenantId, filtro, args) + sinEstados(excluidos, args);
        Long total = jdbc.queryForObject("SELECT count(*) FROM documento d JOIN comprobante c ON c.documento_id = d.id" + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private static String sinEstados(Set<EstadoDocumento> excluidos, List<Object> args) {
        if (excluidos == null || excluidos.isEmpty()) return "";
        excluidos.forEach(e -> args.add(e.name()));
        return " AND d.estado NOT IN (" + String.join(", ", java.util.Collections.nCopies(excluidos.size(), "?")) + ")";
    }

    @Override public long contar(UUID tenantId, ConsultarComprobanteUseCase.Filtro filtro) {
        List<Object> args = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT count(*) FROM documento d JOIN comprobante c ON c.documento_id = d.id" + where(tenantId, filtro, args), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    /**
     * Filtros del listado (#2, #3, #326): estado, rango de fecha de emisión (inclusive), serie, tipo, número y cliente; el WHERE y sus argumentos se comparten con el conteo.
     * El cliente se busca por documento (empieza por lo escrito) cuando son dígitos y por nombre (contiene, sin distinguir mayúsculas) en cualquier caso: un DNI de 8 dígitos
     * puede ser también parte de un nombre, así que las dos formas van con OR.
     */
    private static String where(UUID tenantId, ConsultarComprobanteUseCase.Filtro f, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE d.tenant_id = ?");
        args.add(tenantId);
        if (f.estado() != null) { w.append(" AND d.estado = ?"); args.add(f.estado().name()); }
        if (f.desde() != null) { w.append(" AND d.fecha_emision >= ?"); args.add(Date.valueOf(f.desde())); }
        if (f.hasta() != null) { w.append(" AND d.fecha_emision <= ?"); args.add(Date.valueOf(f.hasta())); }
        if (f.serie() != null) { w.append(" AND d.serie = ?"); args.add(f.serie()); }
        if (f.tipo() != null) { w.append(" AND d.tipo = ?"); args.add(f.tipo()); }
        if (f.numero() != null) { w.append(" AND d.numero = ?"); args.add(f.numero()); }
        if (f.cliente() != null) {
            // Como la búsqueda del backoffice: sin distinguir mayúsculas ni tildes con `translate` (del núcleo de Postgres, sin la extensión `unaccent`) aplicado a la columna y al texto,
            // lo que además no depende del LC_CTYPE de la base (con «C», `lower('PACÍFICO')` deja la Í), y con los comodines de LIKE tomados literalmente.
            String literal = JdbcCuentasAdminRepository.escaparComodines(java.text.Normalizer.normalize(f.cliente(), java.text.Normalizer.Form.NFC));
            w.append(" AND (" + JdbcCuentasAdminRepository.SIN_TILDES.formatted("c.receptor_nombre") + " ILIKE " + JdbcCuentasAdminRepository.SIN_TILDES.formatted("?") + " ESCAPE '\\'");
            args.add("%" + literal + "%");
            if (f.cliente().chars().allMatch(Character::isDigit)) { w.append(" OR c.receptor_num_doc LIKE ? ESCAPE '\\'"); args.add(literal + "%"); }
            w.append(")");
        }
        return w.toString();
    }

    private static final String SELECT = """
        SELECT d.id, d.tenant_id, d.tipo, d.serie, d.numero, d.fecha_emision, d.hora_emision, d.estado, d.hash, d.nombre_archivo, d.intentos, d.ultimo_error,
               d.cdr_codigo, d.cdr_descripcion, d.cdr_observaciones::text AS cdr_obs, d.xml_key, d.cdr_key,
               c.tipo_operacion, c.moneda, c.receptor_tipo_doc, c.receptor_num_doc, c.receptor_nombre, c.receptor_direccion,
               c.forma_pago, c.monto_pendiente, c.descuento_global_tipo, c.descuento_global_valor, c.descuento_global_afecta_base,
               c.detraccion_codigo, c.detraccion_porcentaje, c.detraccion_monto, c.detraccion_cuenta, c.detraccion_medio_pago,
               c.retencion_porcentaje, c.retencion_monto, c.percepcion_regimen, c.percepcion_porcentaje, c.percepcion_base, c.percepcion_monto, c.orden_compra, c.fecha_vencimiento, c.redondeo,
               c.nota_tipo_afectado, c.nota_serie_afectada, c.nota_numero_afectado, c.nota_motivo, c.nota_descripcion, c.observaciones, c.tasa_igv, c.leyendas,
               c.receptor_pais, c.incoterm, c.pais_uso, c.tipo_cambio,
               c.vendedor_ubigeo, c.vendedor_condicion, c.lugar_direccion, c.lugar_ubigeo, c.lugar_condicion, c.retencion_renta_tasa, c.receptor_email
        FROM documento d JOIN comprobante c ON c.documento_id = d.id
        """;

    /**
     * Las consultas de varias filas: primero los ids (una consulta), luego los hijos de todos juntos (una consulta por tabla y tanda) y por último las filas. Antes eran cinco
     * consultas por comprobante. {@code filtro} es el WHERE; {@code orden}, el ORDER BY (con LIMIT y OFFSET si los hay): la consulta de ids solo lo repite cuando lleva LIMIT,
     * porque ahí decide qué filas entran; sin LIMIT ordenar los ids sería trabajo perdido.
     */
    private List<Comprobante> consultar(String filtro, String orden, Object... args) {
        boolean acotada = orden.contains("LIMIT");
        List<IdFila> ids = jdbc.query("SELECT d.id, c.forma_pago " + DESDE + filtro + (acotada ? orden : ""),
                (rs, i) -> new IdFila(rs.getObject("id", UUID.class), "CREDITO".equals(rs.getString("forma_pago"))), args);
        Hijos hijos = new Hijos();
        hijos.cargar(ids.stream().map(IdFila::id).toList());
        hijos.cargarCuotas(ids.stream().filter(IdFila::alCredito).map(IdFila::id).toList());
        return jdbc.query(SELECT + filtro + orden, (rs, i) -> mapear(rs, hijos), args);
    }

    private record IdFila(UUID id, boolean alCredito) {}

    /**
     * Cargos, ítems, anticipos y documentos relacionados de un conjunto de comprobantes, y por separado las cuotas de los que son al crédito, leídos con {@code = ANY(?)} (un solo
     * texto de SQL para cualquier tamaño) de a {@value #LOTE_DE_HIJOS}. Un comprobante que no se cargó de antemano (nació entre la consulta de ids y la de filas, o es la fila única de
     * {@code buscar}) se lee por demanda.
     */
    private final class Hijos {
        private final Set<UUID> cargados = new HashSet<>();
        private final Set<UUID> conCuotas = new HashSet<>();
        private final Map<UUID, Map<Integer, List<Cargo>>> cargos = new HashMap<>();
        private final Map<UUID, List<Item>> items = new HashMap<>();
        private final Map<UUID, List<Anticipo>> anticipos = new HashMap<>();
        private final Map<UUID, List<GuiaRelacionada>> guias = new HashMap<>();
        private final Map<UUID, List<DocumentoRelacionado>> otros = new HashMap<>();
        private final Map<UUID, List<FormaPago.Cuota>> cuotas = new HashMap<>();

        Map<Integer, List<Cargo>> cargos(UUID id) { asegurar(id); return cargos.getOrDefault(id, Collections.emptyMap()); }
        List<Item> items(UUID id) { asegurar(id); return items.getOrDefault(id, List.of()); }
        List<Anticipo> anticipos(UUID id) { asegurar(id); return anticipos.getOrDefault(id, List.of()); }
        List<GuiaRelacionada> guias(UUID id) { asegurar(id); return guias.getOrDefault(id, List.of()); }
        List<DocumentoRelacionado> otros(UUID id) { asegurar(id); return otros.getOrDefault(id, List.of()); }
        /** Solo se pide para un comprobante al crédito: uno al contado no cuesta la consulta. */
        List<FormaPago.Cuota> cuotas(UUID id) { if (!conCuotas.contains(id)) cargarCuotas(List.of(id)); return cuotas.getOrDefault(id, List.of()); }

        private void asegurar(UUID id) { if (!cargados.contains(id)) cargar(List.of(id)); }

        void cargar(Collection<UUID> ids) {
            List<UUID> pendientes = ids.stream().filter(i -> !cargados.contains(i)).distinct().toList();
            for (int desde = 0; desde < pendientes.size(); desde += LOTE_DE_HIJOS)
                cargarLote(pendientes.subList(desde, Math.min(pendientes.size(), desde + LOTE_DE_HIJOS)));
        }

        void cargarCuotas(Collection<UUID> ids) {
            List<UUID> pendientes = ids.stream().filter(i -> !conCuotas.contains(i)).distinct().toList();
            for (int desde = 0; desde < pendientes.size(); desde += LOTE_DE_HIJOS) {
                List<UUID> lote = pendientes.subList(desde, Math.min(pendientes.size(), desde + LOTE_DE_HIJOS));
                consultarHijos("SELECT comprobante_id, monto, vencimiento FROM comprobante_cuota WHERE comprobante_id = ANY(?) ORDER BY comprobante_id, orden", lote,
                        r -> cuotas.computeIfAbsent(r.getObject("comprobante_id", UUID.class), k -> new ArrayList<>())
                                .add(new FormaPago.Cuota(r.getBigDecimal("monto"), r.getDate("vencimiento").toLocalDate())));
                conCuotas.addAll(lote);
            }
        }

        private void cargarLote(List<UUID> lote) {
            consultarHijos("SELECT comprobante_id, item_orden, codigo, tipo, valor FROM comprobante_cargo WHERE comprobante_id = ANY(?) ORDER BY comprobante_id, orden", lote,
                    r -> cargos.computeIfAbsent(r.getObject("comprobante_id", UUID.class), k -> new HashMap<>()).computeIfAbsent(r.getObject("item_orden", Integer.class), k -> new ArrayList<>())
                            .add(new Cargo(r.getString("codigo"), Cargo.Tipo.valueOf(r.getString("tipo")), sinCeros(r.getBigDecimal("valor")))));
            consultarHijos(ITEMS_SQL, lote, r -> {
                UUID cid = r.getObject("comprobante_id", UUID.class);
                Map<Integer, List<Cargo>> cargosDe = cargos.getOrDefault(cid, Collections.emptyMap());
                items.computeIfAbsent(cid, k -> new ArrayList<>()).add(new Item(r.getString("codigo"), r.getString("descripcion"), r.getString("unidad"), r.getBigDecimal("cantidad"),
                        r.getBigDecimal("precio_unitario"), TipoAfectacionIgv.porCodigo(r.getString("tipo_afectacion_igv")),
                        descuento(r.getString("descuento_tipo"), r.getBigDecimal("descuento_valor"), r.getObject("descuento_afecta_base", Boolean.class)),
                        r.getString("isc_sistema") == null ? null : new Isc(r.getString("isc_sistema"), r.getBigDecimal("isc_tasa") == null ? null : sinCeros(r.getBigDecimal("isc_tasa")),
                                r.getBigDecimal("isc_monto_unitario") == null ? null : sinCeros(r.getBigDecimal("isc_monto_unitario")),
                                r.getBigDecimal("isc_base_pvp") == null ? null : sinCeros(r.getBigDecimal("isc_base_pvp"))),
                        r.getBoolean("icbper"), cargosDe.getOrDefault(r.getInt("orden"), List.of()),
                        CodigoProductoSunat.de(r.getString("codigo_sunat")), r.getString("gtin_tipo") == null ? null : new Gtin(r.getString("gtin_tipo"), r.getString("gtin")),
                        DatosSectorialesJson.deJson(r.getString("hidrobiologico"), Hidrobiologico.class), DatosSectorialesJson.deJson(r.getString("transporte"), TransporteCarga.class),
                        r.getBigDecimal("otros_tributos_tasa") == null ? null : new OtrosTributos(sinCeros(r.getBigDecimal("otros_tributos_tasa"))), r.getString("placa")));
            });
            consultarHijos("SELECT comprobante_id, serie, numero, monto, afectacion, fecha_pago FROM comprobante_anticipo WHERE comprobante_id = ANY(?) ORDER BY comprobante_id, orden", lote,
                    r -> anticipos.computeIfAbsent(r.getObject("comprobante_id", UUID.class), k -> new ArrayList<>())
                            .add(new Anticipo(r.getString("serie"), r.getLong("numero"), r.getBigDecimal("monto"), Anticipo.Afectacion.valueOf(r.getString("afectacion")),
                                    r.getDate("fecha_pago") == null ? null : r.getDate("fecha_pago").toLocalDate())));
            consultarHijos("SELECT comprobante_id, clase, tipo, numero FROM comprobante_documento_relacionado WHERE comprobante_id = ANY(?) ORDER BY comprobante_id, orden", lote, r -> {
                UUID cid = r.getObject("comprobante_id", UUID.class);
                switch (r.getString("clase")) {
                    case "GUIA" -> guias.computeIfAbsent(cid, k -> new ArrayList<>()).add(new GuiaRelacionada(r.getString("tipo"), r.getString("numero")));
                    case "OTRO" -> otros.computeIfAbsent(cid, k -> new ArrayList<>()).add(new DocumentoRelacionado(r.getString("tipo"), r.getString("numero")));
                    default -> throw new IllegalStateException("Clase de documento relacionado desconocida en comprobante " + cid + ": " + r.getString("clase"));
                }
            });
            cargados.addAll(lote);
        }

        private void consultarHijos(String sql, List<UUID> lote, RowCallbackHandler fila) {
            jdbc.query(sql, ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", lote.toArray())), fila);
        }
    }

    private static final int LOTE_DE_HIJOS = 500;
    private static final String DESDE = "FROM documento d JOIN comprobante c ON c.documento_id = d.id";
    private static final String ITEMS_SQL = "SELECT comprobante_id, orden, codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv, descuento_tipo, descuento_valor, descuento_afecta_base, isc_sistema, isc_tasa, isc_monto_unitario, isc_base_pvp, icbper, codigo_sunat, gtin_tipo, gtin, hidrobiologico::text AS hidrobiologico, transporte::text AS transporte, otros_tributos_tasa, placa FROM comprobante_item WHERE comprobante_id = ANY(?) ORDER BY comprobante_id, orden";

    /** Una sola fila (buscar, bloquear): sus hijos se leen aquí mismo, por demanda. */
    private Comprobante mapear(ResultSet rs, int i) throws SQLException { return mapear(rs, new Hijos()); }

    private Comprobante mapear(ResultSet rs, Hijos hijos) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        // Cargos por nivel: clave null = globales, n = línea n (orden del ítem).
        Map<Integer, List<Cargo>> cargos = hijos.cargos(id);
        List<Item> items = hijos.items(id);
        List<Anticipo> anticipos = hijos.anticipos(id);
        List<GuiaRelacionada> guias = hijos.guias(id);
        List<DocumentoRelacionado> otros = hijos.otros(id);
        Referencias referencias = new Referencias(rs.getString("orden_compra"), guias, otros);
        Cdr cdr = rs.getString("cdr_codigo") == null ? null : new Cdr(rs.getString("cdr_codigo"), rs.getString("cdr_descripcion"), deJson(rs.getString("cdr_obs")));
        Comprobante c = Comprobante.persistido(id, rs.getObject("tenant_id", UUID.class), TipoDocumento.porCodigo(rs.getString("tipo")), rs.getString("serie"), rs.getLong("numero"), rs.getDate("fecha_emision").toLocalDate(), EstadoDocumento.valueOf(rs.getString("estado")), new Receptor(rs.getString("receptor_tipo_doc"), rs.getString("receptor_num_doc"), rs.getString("receptor_nombre"), rs.getString("receptor_direccion"), rs.getString("receptor_pais")), items)
                .horaEmision(rs.getTime("hora_emision") == null ? null : rs.getTime("hora_emision").toLocalTime())
                .fechaVencimiento(rs.getDate("fecha_vencimiento") == null ? null : rs.getDate("fecha_vencimiento").toLocalDate())
                .moneda(rs.getString("moneda"))
                .tipoOperacion(rs.getString("tipo_operacion"))
                .formaPago(formaPago(rs, () -> hijos.cuotas(id)))
                .descuentoGlobal(descuento(rs.getString("descuento_global_tipo"), rs.getBigDecimal("descuento_global_valor"), rs.getObject("descuento_global_afecta_base", Boolean.class)))
                .cargos(cargos.getOrDefault(null, List.of()))
                .detraccion(detraccion(rs))
                .retencion(retencion(rs))
                .percepcion(percepcion(rs))
                .anticipos(anticipos)
                .referencias(referencias)
                .redondeo(rs.getBigDecimal("redondeo"))
                .nota(nota(rs))
                .liquidacion(liquidacion(rs))
                .tasaIgv(rs.getBigDecimal("tasa_igv"))
                .tipoCambio(rs.getBigDecimal("tipo_cambio"))
                .leyendas(rs.getString("leyendas") == null ? List.of() : List.of(rs.getString("leyendas").split(",")))
                .exportacion(rs.getString("incoterm") == null && rs.getString("pais_uso") == null ? null : new Exportacion(rs.getString("incoterm"), rs.getString("pais_uso")))
                .firma(rs.getString("hash"), rs.getString("nombre_archivo"), rs.getString("xml_key"))
                .cdr(cdr, rs.getString("cdr_key"))
                .envio(rs.getInt("intentos"), rs.getString("ultimo_error"))
                .correoCliente(rs.getString("receptor_email"))
                .rehidratar();
        c.anotar(rs.getString("observaciones"));
        return c;
    }

    private static Nota nota(ResultSet rs) throws SQLException {
        return rs.getString("nota_motivo") == null ? null
                : new Nota(TipoDocumento.porCodigo(rs.getString("nota_tipo_afectado")), rs.getString("nota_serie_afectada"), rs.getLong("nota_numero_afectado"),
                        rs.getString("nota_motivo"), rs.getString("nota_descripcion"));
    }

    /** #327: la columna de la tasa marca la liquidación de compra (la restricción comprobante_liquidacion_completa asegura que vienen los seis datos juntos). */
    private static LiquidacionCompra liquidacion(ResultSet rs) throws SQLException {
        return rs.getBigDecimal("retencion_renta_tasa") == null ? null
                : new LiquidacionCompra(rs.getString("vendedor_ubigeo"), rs.getString("vendedor_condicion"),
                        new LiquidacionCompra.Ubicacion(rs.getString("lugar_direccion"), rs.getString("lugar_ubigeo"), rs.getString("lugar_condicion")), sinCeros(rs.getBigDecimal("retencion_renta_tasa")));
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

    private static FormaPago formaPago(ResultSet rs, java.util.function.Supplier<List<FormaPago.Cuota>> cuotas) throws SQLException {
        FormaPago.Tipo tipo = FormaPago.Tipo.valueOf(rs.getString("forma_pago"));
        if (tipo == FormaPago.Tipo.CONTADO) return FormaPago.contado();
        return FormaPago.credito(rs.getBigDecimal("monto_pendiente"), cuotas.get());
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
