package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase.ComprobanteReciente;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase.CuentaDetalle;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase.EmpresaDeCuenta;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase.EventoReciente;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase.UsuarioDeCuenta;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;
import pe.factura.application.port.out.CuentasAdminRepository;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Listado del backoffice (#180). Una sola consulta: el número de empresas y el último acceso salen de subconsultas
 * correlacionadas y la búsqueda por empresa de un {@code EXISTS}, así que no hay N+1 ni cuentas repetidas.
 */
@RequiredArgsConstructor
public class JdbcCuentasAdminRepository implements CuentasAdminRepository {
    private static final String SELECT = """
            SELECT c.id, c.nombre, c.email, c.telefono, c.created_at,
                   (SELECT count(*) FROM tenant t WHERE t.cuenta_id = c.id) AS empresas,
                   (SELECT max(s.created_at) FROM sesion s JOIN usuario u ON u.id = s.usuario_id WHERE u.cuenta_id = c.id) AS ultimo_acceso
            FROM cuenta c
            """;
    private static final RowMapper<CuentaResumen> MAPPER = (rs, i) -> {
        Timestamp ultimoAcceso = rs.getTimestamp("ultimo_acceso");
        return new CuentaResumen(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("email"), rs.getString("telefono"),
                rs.getTimestamp("created_at").toInstant(), rs.getInt("empresas"), ultimoAcceso == null ? null : ultimoAcceso.toInstant());
    };

    private final JdbcTemplate jdbc;

    @Override public List<CuentaResumen> listar(Filtro filtro, int pagina, int porPagina) {
        List<Object> args = new ArrayList<>();
        String donde = donde(filtro, args);
        args.add(porPagina);
        args.add((long) (pagina - 1) * porPagina);
        return jdbc.query(SELECT + donde + " ORDER BY c.created_at DESC, c.id LIMIT ? OFFSET ?", MAPPER, args.toArray());
    }

    @Override public long contar(Filtro filtro) {
        List<Object> args = new ArrayList<>();
        return jdbc.queryForObject("SELECT count(*) FROM cuenta c" + donde(filtro, args), Long.class, args.toArray());
    }

    /** Cuántas filas de actividad reciente trae el detalle (#181): lo que cabe leer de un vistazo, no un historial. */
    static final int RECIENTES = 10;

    /**
     * Detalle de una cuenta (#181): cuatro lecturas cortas por la clave de la cuenta. Los comprobantes se piden por empresa, con el
     * índice de {@code (tenant_id, fecha_emision)}, y se reducen a los últimos de todas: una cuenta con mucha historia no recorre sus
     * comprobantes para mostrar diez.
     */
    @Override public Optional<CuentaDetalle> detalle(UUID cuentaId) {
        return jdbc.query("SELECT id, nombre, email, telefono, created_at FROM cuenta WHERE id = ?", (rs, i) -> new Object[]{
                        rs.getString("nombre"), rs.getString("email"), rs.getString("telefono"), rs.getTimestamp("created_at").toInstant()}, cuentaId)
                .stream().findFirst().map(c -> new CuentaDetalle(cuentaId, (String) c[0], (String) c[1], (String) c[2], (Instant) c[3],
                        usuariosDe(cuentaId), empresasDe(cuentaId), comprobantesDe(cuentaId), eventosDe(cuentaId)));
    }

    private List<UsuarioDeCuenta> usuariosDe(UUID cuentaId) {
        return jdbc.query("""
                SELECT u.id, u.email, u.rol, u.activo, u.correo_verificado_at,
                       (SELECT max(s.created_at) FROM sesion s WHERE s.usuario_id = u.id) AS ultimo_acceso
                FROM usuario u WHERE u.cuenta_id = ? ORDER BY u.created_at, u.email
                """, (rs, i) -> new UsuarioDeCuenta(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("rol"), rs.getBoolean("activo"),
                instante(rs.getTimestamp("correo_verificado_at")), instante(rs.getTimestamp("ultimo_acceso"))), cuentaId);
    }

    /**
     * El certificado y la clave SOL se informan como «cargados» sin tocar su contenido (las columnas están cifradas y ni se leen). Las
     * credenciales SOL cuentan solo con usuario y clave: una a medias no sirve para enviar.
     */
    private List<EmpresaDeCuenta> empresasDe(UUID cuentaId) {
        return jdbc.query("""
                SELECT id, ruc, razon_social, entorno, cert_pkcs12_enc IS NOT NULL AS tiene_certificado, cert_vigencia_hasta,
                       (sol_usuario_enc IS NOT NULL AND sol_clave_enc IS NOT NULL) AS tiene_sol
                FROM tenant WHERE cuenta_id = ? ORDER BY created_at, ruc
                """, (rs, i) -> new EmpresaDeCuenta(rs.getObject("id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"), rs.getString("entorno"),
                rs.getBoolean("tiene_certificado"), rs.getObject("cert_vigencia_hasta", LocalDate.class), rs.getBoolean("tiene_sol")), cuentaId);
    }

    private List<ComprobanteReciente> comprobantesDe(UUID cuentaId) {
        return jdbc.query("""
                SELECT d.id, d.tenant_id, t.ruc, d.tipo, d.serie, d.numero, d.fecha_emision, d.estado, c.moneda, c.total
                FROM tenant t
                CROSS JOIN LATERAL (SELECT * FROM documento x WHERE x.tenant_id = t.id ORDER BY x.fecha_emision DESC, x.created_at DESC LIMIT ?) d
                JOIN comprobante c ON c.documento_id = d.id
                WHERE t.cuenta_id = ?
                ORDER BY d.fecha_emision DESC, d.created_at DESC, d.id
                LIMIT ?
                """, (rs, i) -> new ComprobanteReciente(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("ruc"), rs.getString("tipo"),
                rs.getString("serie"), rs.getLong("numero"), rs.getObject("fecha_emision", LocalDate.class), rs.getString("estado"), rs.getString("moneda"),
                rs.getBigDecimal("total")), RECIENTES, cuentaId, RECIENTES);
    }

    private List<EventoReciente> eventosDe(UUID cuentaId) {
        return jdbc.query("""
                SELECT accion, actor_tipo, ocurrido_en, detalle FROM auditoria_admin WHERE cuenta_id = ? ORDER BY ocurrido_en DESC, id LIMIT ?
                """, (rs, i) -> new EventoReciente(rs.getString("accion"), rs.getString("actor_tipo"), rs.getTimestamp("ocurrido_en").toInstant(), rs.getString("detalle")),
                cuentaId, RECIENTES);
    }

    private static Instant instante(Timestamp t) { return t == null ? null : t.toInstant(); }

    /**
     * Vocales con tilde, diéresis, acento grave o circunflejo y su vocal sin marca (#214), en minúscula y mayúscula: así no depende de
     * que el {@code LC_CTYPE} de la base sepa pasar «Í» a minúscula (con {@code C}, {@code ILIKE} solo lo hace con el ASCII). La ñ no se
     * vuelve n, porque es otra letra («peña» no es «pena»), pero la «Ñ» sí pasa a «ñ» por la misma razón que «Í»: si no, en una base
     * {@code C} «PEÑA» no encuentra «Peña».
     */
    private static final String CON_TILDE = "áéíóúàèìòùäëïöüâêîôûÁÉÍÓÚÀÈÌÒÙÄËÏÖÜÂÊÎÔÛÑ";
    private static final String SIN_TILDE = "aeiouaeiouaeiouaeiouAEIOUAEIOUAEIOUAEIOUñ";
    /**
     * {@code translate} es del núcleo de Postgres: no exige la extensión {@code unaccent}, que no todos los proveedores ofrecen. Se
     * aplica a la columna y al texto buscado, así la regla vale en los dos sentidos.
     */
    private static final String SIN_TILDES = "translate(%s, '" + CON_TILDE + "', '" + SIN_TILDE + "')";

    /**
     * Correo y nombre por subcadena; RUC por prefijo (un fragmento interno de un RUC no identifica a nadie); razón social por
     * subcadena. Todo sin distinguir mayúsculas ni tildes y con los comodines del texto buscado tomados literalmente.
     */
    private static String donde(Filtro filtro, List<Object> args) {
        if (filtro.q() == null) return "";
        // Una tilde puede llegar como «í» o como «i» + acento combinado: en forma compuesta (NFC) las dos son «í».
        String literal = escaparComodines(Normalizer.normalize(filtro.q(), Normalizer.Form.NFC));
        String contiene = "%" + literal + "%";
        args.add(contiene);
        args.add(contiene);
        args.add(literal + "%");
        args.add(contiene);
        String buscado = SIN_TILDES.formatted("?");
        return """
                 WHERE (c.email ILIKE ? ESCAPE '\\' OR %s ILIKE %s ESCAPE '\\'
                        OR EXISTS (SELECT 1 FROM tenant t WHERE t.cuenta_id = c.id
                                   AND (t.ruc LIKE ? ESCAPE '\\' OR %s ILIKE %s ESCAPE '\\')))
                """.formatted(SIN_TILDES.formatted("c.nombre"), buscado, SIN_TILDES.formatted("t.razon_social"), buscado);
    }

    static String escaparComodines(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
