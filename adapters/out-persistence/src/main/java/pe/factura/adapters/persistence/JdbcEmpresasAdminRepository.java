package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.ApiKeyDeEmpresa;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.Cdr;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.ComprobanteReciente;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.DomicilioDeEmpresa;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.EmpresaDetalle;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.EstablecimientoDeEmpresa;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.EventoDeComprobante;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.Outbox;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.PdfDeEmpresa;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.SerieDeEmpresa;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase.TareaPendiente;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EmpresaResumen;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.Filtro;
import pe.factura.application.port.out.EmpresasAdminRepository;
import pe.factura.domain.tenant.Entorno;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Listado de empresas del backoffice (#185). Una sola consulta: la cuenta sale de un {@code LEFT JOIN} (las empresas dadas de alta por una
 * integración no tienen cuenta) y los números de cada fila de subconsultas correlacionadas que Postgres evalúa solo para las filas de la
 * página, por los índices de {@code serie} y de {@code documento (tenant_id, fecha_emision)}: no hay N+1 ni se recorren los documentos.
 */
@RequiredArgsConstructor
public class JdbcEmpresasAdminRepository implements EmpresasAdminRepository {
    /**
     * La única regla del estado del certificado: la usan la columna y el filtro, así lo que el filtro deja pasar es exactamente lo que la fila
     * dice ser. Pide dos veces «hoy». Vencido es antes de hoy (el último día todavía vale); por vencer, menos de {@code DIAS_POR_VENCER} días.
     */
    static final String ESTADO = """
            CASE WHEN t.cert_pkcs12_enc IS NULL THEN 'SIN_CERTIFICADO'
                 WHEN t.cert_vigencia_hasta IS NULL THEN 'SIN_FECHA'
                 WHEN t.cert_vigencia_hasta < ?::date THEN 'VENCIDO'
                 WHEN t.cert_vigencia_hasta - ?::date < %d THEN 'POR_VENCER'
                 ELSE 'VIGENTE' END""".formatted(ListarEmpresasAdminUseCase.DIAS_POR_VENCER);

    /** Sin certificado no hay vigencia ni días, aunque quedara una fecha suelta. Los días piden «hoy» una vez. */
    private static final String VIGENTE_HASTA = "CASE WHEN t.cert_pkcs12_enc IS NULL THEN NULL ELSE t.cert_vigencia_hasta END";
    private static final String DIAS_RESTANTES = "CASE WHEN t.cert_pkcs12_enc IS NULL THEN NULL ELSE t.cert_vigencia_hasta - ?::date END";
    /** Las credenciales SOL cuentan solo con usuario y clave: una a medias no sirve para enviar. */
    private static final String SOL_CARGADAS = "(t.sol_usuario_enc IS NOT NULL AND t.sol_clave_enc IS NOT NULL)";

    private static final String SELECT = """
            SELECT t.id, t.ruc, t.razon_social, t.cuenta_id, c.nombre AS cuenta_nombre, c.baja_en AS cuenta_baja_en, t.entorno,
                   %s AS estado,
                   %s AS vigente_hasta,
                   %s AS dias_restantes,
                   %s AS tiene_sol,
                   (SELECT count(*) FROM serie s WHERE s.tenant_id = t.id AND s.activa) AS series,
                   (SELECT count(*) FROM documento d WHERE d.tenant_id = t.id AND d.fecha_emision >= ?::date AND d.fecha_emision < ?::date) AS del_mes,
                   (SELECT max(d.fecha_emision) FROM documento d WHERE d.tenant_id = t.id) AS ultima_emision
            FROM tenant t LEFT JOIN cuenta c ON c.id = t.cuenta_id
            """.formatted(ESTADO, VIGENTE_HASTA, DIAS_RESTANTES, SOL_CARGADAS);

    private final JdbcTemplate jdbc;

    @Override public List<EmpresaResumen> listar(Filtro filtro, LocalDate hoy, int pagina, int porPagina) {
        LocalDate inicioDelMes = hoy.withDayOfMonth(1);
        List<Object> args = new ArrayList<>(List.of(hoy, hoy, hoy, inicioDelMes, inicioDelMes.plusMonths(1)));
        String donde = donde(filtro, hoy, args);
        args.add(porPagina);
        args.add((long) (pagina - 1) * porPagina);
        return jdbc.query(SELECT + donde + " ORDER BY t.created_at DESC, t.id LIMIT ? OFFSET ?", (rs, i) -> new EmpresaResumen(
                rs.getObject("id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"), rs.getObject("cuenta_id", UUID.class),
                rs.getString("cuenta_nombre"), Entorno.valueOf(rs.getString("entorno")), EstadoCertificado.valueOf(rs.getString("estado")),
                rs.getObject("vigente_hasta", LocalDate.class), rs.getObject("dias_restantes", Integer.class), rs.getBoolean("tiene_sol"),
                rs.getInt("series"), rs.getInt("del_mes"), rs.getObject("ultima_emision", LocalDate.class), instante(rs.getTimestamp("cuenta_baja_en"))), args.toArray());
    }

    @Override public long contar(Filtro filtro, LocalDate hoy) {
        List<Object> args = new ArrayList<>();
        return jdbc.queryForObject("SELECT count(*) FROM tenant t" + donde(filtro, hoy, args), Long.class, args.toArray());
    }

    // --- detalle de una empresa (#186) ----------------------------------------------------------------------------------------------

    /** Cuántos comprobantes recientes y cuántas tareas pendientes trae el detalle: lo que cabe leer de un vistazo, no un historial. */
    static final int RECIENTES = 10;
    /** Cuántos cambios de estado de esos comprobantes recientes. */
    static final int EVENTOS = 20;

    private static final String DETALLE = """
            SELECT t.id, t.ruc, t.razon_social, t.nombre_comercial, t.entorno, t.created_at, t.cuenta_id, c.nombre AS cuenta_nombre,
                   %s AS estado, %s AS vigente_hasta, %s AS dias_restantes, %s AS tiene_sol,
                   t.dom_ubigeo, t.dom_direccion, t.dom_urbanizacion, t.dom_distrito, t.dom_provincia, t.dom_departamento, t.dom_establecimiento,
                   t.cuenta_detracciones, t.padron_tasa_especial_igv,
                   t.pdf_plantilla, t.pdf_color, t.pdf_logo_key IS NOT NULL AS tiene_logo, t.pdf_pie, t.pdf_observaciones
            FROM tenant t LEFT JOIN cuenta c ON c.id = t.cuenta_id WHERE t.id = ?
            """.formatted(ESTADO, VIGENTE_HASTA, DIAS_RESTANTES, SOL_CARGADAS);

    /** Lo de la fila de la empresa; las listas de abajo salen de otras lecturas cortas, todas por la clave de la empresa. */
    private record Base(UUID id, String ruc, String razonSocial, String nombreComercial, Entorno entorno, Instant creadaEn, UUID cuentaId, String cuentaNombre,
                        EstadoCertificado certificado, LocalDate vigenteHasta, Integer diasRestantes, boolean tieneSol, DomicilioDeEmpresa domicilio,
                        String cuentaDetracciones, boolean padron, PdfDeEmpresa pdf) {}

    @Override public Optional<EmpresaDetalle> detalle(UUID empresaId, LocalDate hoy) {
        return jdbc.query(DETALLE, (rs, i) -> base(rs), hoy, hoy, hoy, empresaId).stream().findFirst().map(b -> {
            List<ComprobanteReciente> comprobantes = comprobantesDe(empresaId);
            return new EmpresaDetalle(b.id(), b.ruc(), b.razonSocial(), b.nombreComercial(), b.entorno(), b.creadaEn(), b.cuentaId(), b.cuentaNombre(),
                    b.certificado(), b.vigenteHasta(), b.diasRestantes(), b.tieneSol(), b.domicilio(), b.cuentaDetracciones(), b.padron(), b.pdf(),
                    seriesDe(empresaId), establecimientosDe(empresaId), apiKeysDe(empresaId), comprobantes, eventosDe(comprobantes), outboxDe(empresaId));
        });
    }

    private static Base base(ResultSet rs) throws SQLException {
        DomicilioDeEmpresa domicilio = rs.getString("dom_ubigeo") == null ? null
                : new DomicilioDeEmpresa(rs.getString("dom_ubigeo"), rs.getString("dom_direccion"), rs.getString("dom_urbanizacion"), rs.getString("dom_distrito"),
                        rs.getString("dom_provincia"), rs.getString("dom_departamento"), rs.getString("dom_establecimiento"));
        PdfDeEmpresa pdf = new PdfDeEmpresa(rs.getString("pdf_plantilla"), rs.getString("pdf_color"), rs.getBoolean("tiene_logo"), rs.getString("pdf_pie"),
                rs.getString("pdf_observaciones"));
        return new Base(rs.getObject("id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"), rs.getString("nombre_comercial"),
                Entorno.valueOf(rs.getString("entorno")), rs.getTimestamp("created_at").toInstant(), rs.getObject("cuenta_id", UUID.class), rs.getString("cuenta_nombre"),
                EstadoCertificado.valueOf(rs.getString("estado")), rs.getObject("vigente_hasta", LocalDate.class), rs.getObject("dias_restantes", Integer.class),
                rs.getBoolean("tiene_sol"), domicilio, rs.getString("cuenta_detracciones"), rs.getBoolean("padron_tasa_especial_igv"), pdf);
    }

    private List<SerieDeEmpresa> seriesDe(UUID empresaId) {
        return jdbc.query("SELECT tipo, codigo, ultimo_numero, activa, establecimiento FROM serie WHERE tenant_id = ? ORDER BY tipo, codigo",
                (rs, i) -> new SerieDeEmpresa(rs.getString("tipo"), rs.getString("codigo"), rs.getLong("ultimo_numero"), rs.getBoolean("activa"), rs.getString("establecimiento")),
                empresaId);
    }

    private List<EstablecimientoDeEmpresa> establecimientosDe(UUID empresaId) {
        return jdbc.query("""
                SELECT codigo, nombre, dom_ubigeo, dom_direccion, dom_urbanizacion, dom_distrito, dom_provincia, dom_departamento, activo
                FROM establecimiento WHERE tenant_id = ? ORDER BY codigo
                """, (rs, i) -> new EstablecimientoDeEmpresa(rs.getString("nombre"), new DomicilioDeEmpresa(rs.getString("dom_ubigeo"), rs.getString("dom_direccion"),
                rs.getString("dom_urbanizacion"), rs.getString("dom_distrito"), rs.getString("dom_provincia"), rs.getString("dom_departamento"), rs.getString("codigo")),
                rs.getBoolean("activo")), empresaId);
    }

    /** Solo el prefijo y el estado: el hash de la clave (`key_hash`) ni se lee. */
    private List<ApiKeyDeEmpresa> apiKeysDe(UUID empresaId) {
        return jdbc.query("SELECT id, prefijo, activa, created_at, revoked_at FROM api_key WHERE tenant_id = ? ORDER BY created_at DESC, id",
                (rs, i) -> new ApiKeyDeEmpresa(rs.getObject("id", UUID.class), rs.getString("prefijo"), rs.getBoolean("activa"), rs.getTimestamp("created_at").toInstant(),
                        instante(rs.getTimestamp("revoked_at"))), empresaId);
    }

    private List<ComprobanteReciente> comprobantesDe(UUID empresaId) {
        return jdbc.query("""
                SELECT d.id, d.tipo, d.serie, d.numero, d.fecha_emision, d.estado, c.moneda, c.total, d.intentos, d.ultimo_error,
                       d.cdr_codigo, d.cdr_descripcion, d.cdr_observaciones::text AS cdr_obs
                FROM documento d JOIN comprobante c ON c.documento_id = d.id
                WHERE d.tenant_id = ?
                ORDER BY d.fecha_emision DESC, d.created_at DESC, d.id
                LIMIT ?
                """, (rs, i) -> {
            BigDecimal total = rs.getBigDecimal("total");
            Cdr cdr = rs.getString("cdr_codigo") == null ? null
                    : new Cdr(rs.getString("cdr_codigo"), rs.getString("cdr_descripcion"), JdbcComprobanteRepository.deJson(rs.getString("cdr_obs")));
            return new ComprobanteReciente(rs.getObject("id", UUID.class), rs.getString("tipo"), rs.getString("serie"), rs.getLong("numero"),
                    rs.getObject("fecha_emision", LocalDate.class), rs.getString("estado"), rs.getString("moneda"), total, rs.getInt("intentos"),
                    rs.getString("ultimo_error"), cdr);
        }, empresaId, RECIENTES);
    }

    /** Los cambios de estado de los comprobantes que el detalle muestra, no la historia entera de la empresa: se piden por su id y su índice. */
    private List<EventoDeComprobante> eventosDe(List<ComprobanteReciente> comprobantes) {
        if (comprobantes.isEmpty()) return List.of();
        String marcas = String.join(", ", Collections.nCopies(comprobantes.size(), "?"));
        List<Object> args = new ArrayList<>(comprobantes.stream().map(ComprobanteReciente::id).collect(Collectors.toList()));
        args.add(EVENTOS);
        return jdbc.query("""
                SELECT d.serie, d.numero, e.estado_anterior, e.estado_nuevo, e.detalle, e.ocurrido_en
                FROM evento_documento e JOIN documento d ON d.id = e.documento_id
                WHERE e.documento_id IN (%s)
                ORDER BY e.ocurrido_en DESC, e.id
                LIMIT ?
                """.formatted(marcas), (rs, i) -> new EventoDeComprobante(rs.getString("serie"), rs.getLong("numero"), rs.getString("estado_anterior"),
                rs.getString("estado_nuevo"), rs.getString("detalle"), rs.getTimestamp("ocurrido_en").toInstant()), args.toArray());
    }

    /** El outbox se vacía al completar cada tarea: lo que queda es lo pendiente o lo que está fallando. */
    private Outbox outboxDe(UUID empresaId) {
        long total = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE tenant_id = ?", Long.class, empresaId);
        List<TareaPendiente> proximas = jdbc.query("""
                SELECT agregado, agregado_id, accion, intentos, siguiente_intento, ultimo_error
                FROM outbox WHERE tenant_id = ? ORDER BY siguiente_intento, id LIMIT ?
                """, (rs, i) -> new TareaPendiente(rs.getString("agregado"), rs.getObject("agregado_id", UUID.class), rs.getString("accion"), rs.getInt("intentos"),
                rs.getTimestamp("siguiente_intento").toInstant(), rs.getString("ultimo_error")), empresaId, RECIENTES);
        return new Outbox(total, proximas);
    }

    private static Instant instante(Timestamp t) { return t == null ? null : t.toInstant(); }

    private static String donde(Filtro filtro, LocalDate hoy, List<Object> args) {
        List<String> condiciones = new ArrayList<>();
        if (filtro.entorno() != null) {
            condiciones.add("t.entorno = ?");
            args.add(filtro.entorno().name());
        }
        if (filtro.certificado() != null) {
            condiciones.add("(" + ESTADO + ") = ?");
            args.add(hoy);
            args.add(hoy);
            args.add(filtro.certificado().name());
        }
        String baja = BajasEnListado.deEmpresa(filtro.bajas());
        if (baja != null) condiciones.add(baja);
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }
}
