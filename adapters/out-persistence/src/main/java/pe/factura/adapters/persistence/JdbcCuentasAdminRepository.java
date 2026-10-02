package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.Filtro;
import pe.factura.application.port.out.CuentasAdminRepository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Correo y nombre por subcadena; RUC por prefijo (un fragmento interno de un RUC no identifica a nadie); razón social por
     * subcadena. Todo sin distinguir mayúsculas y con los comodines del texto buscado tomados literalmente.
     */
    private static String donde(Filtro filtro, List<Object> args) {
        if (filtro.q() == null) return "";
        String literal = escaparComodines(filtro.q());
        String contiene = "%" + literal + "%";
        args.add(contiene);
        args.add(contiene);
        args.add(literal + "%");
        args.add(contiene);
        return """
                 WHERE (c.email ILIKE ? ESCAPE '\\' OR c.nombre ILIKE ? ESCAPE '\\'
                        OR EXISTS (SELECT 1 FROM tenant t WHERE t.cuenta_id = c.id
                                   AND (t.ruc LIKE ? ESCAPE '\\' OR t.razon_social ILIKE ? ESCAPE '\\')))
                """;
    }

    static String escaparComodines(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
