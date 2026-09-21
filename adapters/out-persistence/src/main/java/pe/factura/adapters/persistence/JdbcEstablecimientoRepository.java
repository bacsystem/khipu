package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.EstablecimientoRepository;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcEstablecimientoRepository implements EstablecimientoRepository {
    private static final String SELECT = "SELECT tenant_id, codigo, nombre, dom_ubigeo, dom_direccion, dom_urbanizacion, dom_distrito, dom_provincia, dom_departamento, activo FROM establecimiento";
    private final JdbcTemplate jdbc;

    @Override public void guardar(Establecimiento e) {
        Domicilio d = e.domicilio();
        jdbc.update("""
            INSERT INTO establecimiento (tenant_id, codigo, nombre, dom_ubigeo, dom_direccion, dom_urbanizacion, dom_distrito, dom_provincia, dom_departamento, activo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, codigo) DO UPDATE SET nombre = EXCLUDED.nombre, dom_ubigeo = EXCLUDED.dom_ubigeo, dom_direccion = EXCLUDED.dom_direccion,
              dom_urbanizacion = EXCLUDED.dom_urbanizacion, dom_distrito = EXCLUDED.dom_distrito, dom_provincia = EXCLUDED.dom_provincia,
              dom_departamento = EXCLUDED.dom_departamento, activo = EXCLUDED.activo, updated_at = now()
            """, e.tenantId(), e.codigo(), e.nombre(), d.ubigeo(), d.direccion(), d.urbanizacion(), d.distrito(), d.provincia(), d.departamento(), e.activo());
    }
    @Override public Optional<Establecimiento> buscar(UUID tenantId, String codigo) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? AND codigo = ?", this::mapear, tenantId, codigo).stream().findFirst();
    }
    @Override public Optional<Establecimiento> buscarConBloqueo(UUID tenantId, String codigo) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? AND codigo = ? FOR UPDATE", this::mapear, tenantId, codigo).stream().findFirst();
    }
    @Override public List<Establecimiento> listar(UUID tenantId) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? ORDER BY codigo", this::mapear, tenantId);
    }

    // LEFT JOIN: si la serie está en 0000 o no existe, "asignado" sale null (el caller lo trata como "sin asignación");
    // si está asignada a un código sin fila en establecimiento, "codigo" (del establecimiento) sale null.
    private static final String SELECT_ASIGNACION = """
        SELECT s.establecimiento AS asignado, est.tenant_id, est.codigo, est.nombre, est.dom_ubigeo, est.dom_direccion,
               est.dom_urbanizacion, est.dom_distrito, est.dom_provincia, est.dom_departamento, est.activo
        FROM serie s LEFT JOIN establecimiento est ON est.tenant_id = s.tenant_id AND est.codigo = s.establecimiento
        WHERE s.tenant_id = ? AND s.tipo = ? AND s.codigo = ?
        """;

    @Override public Optional<Asignacion> buscarAsignacionDeSerie(UUID tenantId, TipoDocumento tipo, String serie) {
        List<Asignacion> filas = jdbc.query(SELECT_ASIGNACION, (rs, i) -> mapearAsignacion(rs), tenantId, tipo.codigo(), serie);
        return filas.isEmpty() ? Optional.empty() : Optional.ofNullable(filas.get(0));
    }

    private Asignacion mapearAsignacion(ResultSet rs) throws SQLException {
        String asignado = rs.getString("asignado");
        if (asignado == null || Domicilio.ESTABLECIMIENTO_PRINCIPAL.equals(asignado)) return null;
        Establecimiento e = rs.getString("codigo") == null ? null : mapear(rs, 0);
        return new Asignacion(asignado, e);
    }

    private Establecimiento mapear(ResultSet rs, int i) throws SQLException {
        Domicilio d = new Domicilio(rs.getString("dom_ubigeo"), rs.getString("dom_direccion"), rs.getString("dom_urbanizacion"), rs.getString("dom_distrito"),
                rs.getString("dom_provincia"), rs.getString("dom_departamento"), rs.getString("codigo"));
        return new Establecimiento(rs.getObject("tenant_id", UUID.class), rs.getString("codigo"), rs.getString("nombre"), d, rs.getBoolean("activo"));
    }
}
