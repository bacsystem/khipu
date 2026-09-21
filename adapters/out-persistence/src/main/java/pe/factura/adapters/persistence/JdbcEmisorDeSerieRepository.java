package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.EmisorDeSerieRepository;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Clase propia (no {@link JdbcEstablecimientoRepository}) para que ambos puertos queden en beans de tipos distintos y sin ambigüedad de wiring. */
@RequiredArgsConstructor
public class JdbcEmisorDeSerieRepository implements EmisorDeSerieRepository {
    private final JdbcTemplate jdbc;

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
        Establecimiento e = rs.getString("codigo") == null ? null : mapear(rs);
        return new Asignacion(asignado, e);
    }

    private Establecimiento mapear(ResultSet rs) throws SQLException {
        Domicilio d = new Domicilio(rs.getString("dom_ubigeo"), rs.getString("dom_direccion"), rs.getString("dom_urbanizacion"), rs.getString("dom_distrito"),
                rs.getString("dom_provincia"), rs.getString("dom_departamento"), rs.getString("codigo"));
        return new Establecimiento(rs.getObject("tenant_id", UUID.class), rs.getString("codigo"), rs.getString("nombre"), d, rs.getBoolean("activo"));
    }
}
