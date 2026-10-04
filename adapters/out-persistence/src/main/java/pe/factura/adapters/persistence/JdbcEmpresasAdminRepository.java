package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EmpresaResumen;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.EstadoCertificado;
import pe.factura.application.port.in.ListarEmpresasAdminUseCase.Filtro;
import pe.factura.application.port.out.EmpresasAdminRepository;
import pe.factura.domain.tenant.Entorno;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private static final String ESTADO = """
            CASE WHEN t.cert_pkcs12_enc IS NULL THEN 'SIN_CERTIFICADO'
                 WHEN t.cert_vigencia_hasta IS NULL THEN 'SIN_FECHA'
                 WHEN t.cert_vigencia_hasta < ?::date THEN 'VENCIDO'
                 WHEN t.cert_vigencia_hasta - ?::date < %d THEN 'POR_VENCER'
                 ELSE 'VIGENTE' END""".formatted(ListarEmpresasAdminUseCase.DIAS_POR_VENCER);

    private static final String SELECT = """
            SELECT t.id, t.ruc, t.razon_social, t.cuenta_id, c.nombre AS cuenta_nombre, t.entorno,
                   %s AS estado,
                   CASE WHEN t.cert_pkcs12_enc IS NULL THEN NULL ELSE t.cert_vigencia_hasta END AS vigente_hasta,
                   CASE WHEN t.cert_pkcs12_enc IS NULL THEN NULL ELSE t.cert_vigencia_hasta - ?::date END AS dias_restantes,
                   (t.sol_usuario_enc IS NOT NULL AND t.sol_clave_enc IS NOT NULL) AS tiene_sol,
                   (SELECT count(*) FROM serie s WHERE s.tenant_id = t.id AND s.activa) AS series,
                   (SELECT count(*) FROM documento d WHERE d.tenant_id = t.id AND d.fecha_emision >= ?::date AND d.fecha_emision < ?::date) AS del_mes,
                   (SELECT max(d.fecha_emision) FROM documento d WHERE d.tenant_id = t.id) AS ultima_emision
            FROM tenant t LEFT JOIN cuenta c ON c.id = t.cuenta_id
            """.formatted(ESTADO);

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
                rs.getInt("series"), rs.getInt("del_mes"), rs.getObject("ultima_emision", LocalDate.class)), args.toArray());
    }

    @Override public long contar(Filtro filtro, LocalDate hoy) {
        List<Object> args = new ArrayList<>();
        return jdbc.queryForObject("SELECT count(*) FROM tenant t" + donde(filtro, hoy, args), Long.class, args.toArray());
    }

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
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }
}
