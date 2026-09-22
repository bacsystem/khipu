package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.EstablecimientoRepository;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;

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
        return jdbc.query(SELECT + " WHERE tenant_id = ? AND codigo = ?", (rs, i) -> EstablecimientoRowMapper.mapear(rs), tenantId, codigo).stream().findFirst();
    }
    @Override public Optional<Establecimiento> buscarConBloqueo(UUID tenantId, String codigo) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? AND codigo = ? FOR UPDATE", (rs, i) -> EstablecimientoRowMapper.mapear(rs), tenantId, codigo).stream().findFirst();
    }
    @Override public List<Establecimiento> listar(UUID tenantId) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? ORDER BY codigo", (rs, i) -> EstablecimientoRowMapper.mapear(rs), tenantId);
    }
}
