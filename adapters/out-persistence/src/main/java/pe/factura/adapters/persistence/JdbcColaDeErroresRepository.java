package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase.Filtro;
import pe.factura.application.port.out.ColaDeErroresRepository;
import pe.factura.domain.documento.ClaseDeError;
import pe.factura.domain.documento.EstadoDocumento;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * La cola global de errores (#196): los comprobantes con problema de todas las empresas, por un índice parcial que ya los trae en el orden de la cola. Qué entra lo decide lo
 * mismo que {@link ClaseDeError}: un error de envío, un fuera de plazo y un rechazo cuyo código de CDR es un fault de formato (1000–1999, cuatro dígitos). El reintento sale
 * de un {@code LEFT JOIN} al outbox, que tiene como mucho una fila por (comprobante, acción).
 */
@RequiredArgsConstructor
public class JdbcColaDeErroresRepository implements ColaDeErroresRepository {
    private static final String ENVIO = "d.estado = '" + EstadoDocumento.ERROR_ENVIO.name() + "'";
    private static final String PLAZO = "d.estado = '" + EstadoDocumento.FUERA_DE_PLAZO.name() + "'";
    /** Cuatro dígitos de 1000 a 1999: la misma regla que {@code ClaseDeError.esCodigoDeFormato}. */
    private static final String FORMATO = "(d.estado = '" + EstadoDocumento.RECHAZADO.name() + "' AND d.cdr_codigo ~ '^1[0-9]{3}$')";

    private static final String DESDE = """
            FROM documento d
            JOIN tenant t ON t.id = d.tenant_id
            LEFT JOIN cuenta c ON c.id = t.cuenta_id
            LEFT JOIN outbox o ON o.agregado_id = d.id AND o.accion = 'ENVIAR'
            """;

    private final JdbcTemplate jdbc;

    @Override public List<Fila> listar(Filtro filtro, int pagina, int porPagina) {
        List<Object> args = new ArrayList<>();
        String donde = donde(filtro, args);
        args.add(porPagina);
        args.add((long) (pagina - 1) * porPagina);
        return jdbc.query("""
                SELECT d.id, d.tenant_id, t.ruc, t.razon_social, t.cuenta_id, c.nombre AS cuenta_nombre, d.nombre_archivo, d.tipo, d.serie, d.numero, d.fecha_emision, d.estado,
                       d.intentos, d.ultimo_error, d.cdr_codigo, d.cdr_descripcion, o.siguiente_intento, d.updated_at
                """ + DESDE + donde + " ORDER BY d.fecha_emision, d.created_at, d.id LIMIT ? OFFSET ?", (rs, i) -> new Fila(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("ruc"), rs.getString("razon_social"), rs.getObject("cuenta_id", UUID.class),
                rs.getString("cuenta_nombre"), rs.getString("nombre_archivo"), rs.getString("tipo"), rs.getString("serie"), rs.getLong("numero"),
                rs.getObject("fecha_emision", LocalDate.class), EstadoDocumento.valueOf(rs.getString("estado")), rs.getInt("intentos"), rs.getString("ultimo_error"),
                rs.getString("cdr_codigo"), rs.getString("cdr_descripcion"), instante(rs.getTimestamp("siguiente_intento")), instante(rs.getTimestamp("updated_at"))), args.toArray());
    }

    @Override public long contar(Filtro filtro) {
        List<Object> args = new ArrayList<>();
        return jdbc.queryForObject("SELECT count(*) " + DESDE + donde(filtro, args), Long.class, args.toArray());
    }

    @Override public Optional<Ubicacion> ubicar(UUID comprobanteId) {
        return jdbc.query("SELECT tenant_id, nombre_archivo FROM documento WHERE id = ?", (rs, i) -> new Ubicacion(rs.getObject("tenant_id", UUID.class), rs.getString("nombre_archivo")),
                comprobanteId).stream().findFirst();
    }

    private static String donde(Filtro filtro, List<Object> args) {
        List<String> condiciones = new ArrayList<>();
        condiciones.add(filtro.clase() == null ? "(" + ENVIO + " OR " + PLAZO + " OR " + FORMATO + ")" : switch (filtro.clase()) {
            case ERROR_DE_ENVIO -> ENVIO;
            case FUERA_DE_PLAZO -> PLAZO;
            case ERROR_DE_FORMATO -> FORMATO;
        });
        if (filtro.empresaId() != null) {
            condiciones.add("d.tenant_id = ?");
            args.add(filtro.empresaId());
        }
        if (filtro.texto() != null) condiciones.add(busqueda(filtro.texto(), args));
        return " WHERE " + String.join(" AND ", condiciones);
    }

    /**
     * El cliente por RUC (prefijo: un fragmento interno de un RUC no identifica a nadie), razón social, y nombre o correo de su cuenta; sin distinguir mayúsculas ni tildes y con
     * los comodines del texto tomados literalmente, como en el listado de cuentas.
     */
    private static String busqueda(String texto, List<Object> args) {
        String literal = JdbcCuentasAdminRepository.escaparComodines(Normalizer.normalize(texto, Normalizer.Form.NFC));
        String contiene = "%" + literal + "%";
        args.add(literal + "%");
        args.add(contiene);
        args.add(contiene);
        args.add(contiene);
        String buscado = JdbcCuentasAdminRepository.SIN_TILDES.formatted("?");
        return """
                (t.ruc LIKE ? ESCAPE '\\' OR %s ILIKE %s ESCAPE '\\' OR %s ILIKE %s ESCAPE '\\' OR c.email ILIKE ? ESCAPE '\\')
                """.formatted(JdbcCuentasAdminRepository.SIN_TILDES.formatted("t.razon_social"), buscado, JdbcCuentasAdminRepository.SIN_TILDES.formatted("c.nombre"), buscado);
    }

    private static Instant instante(Timestamp t) { return t == null ? null : t.toInstant(); }
}
