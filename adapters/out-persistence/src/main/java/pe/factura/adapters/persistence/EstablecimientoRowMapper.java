package pe.factura.adapters.persistence;

import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Establecimiento;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Fila con las columnas de {@code establecimiento} (tenant_id, codigo, nombre, dom_*, activo) a {@link Establecimiento}.
 * Compartido por {@link JdbcEstablecimientoRepository} y {@link JdbcEmisorDeSerieRepository} (su JOIN selecciona las
 * mismas columnas bajo el alias {@code est}) para no mantener el mapeo en dos sitios.
 */
final class EstablecimientoRowMapper {
    private EstablecimientoRowMapper() {}

    static Establecimiento mapear(ResultSet rs) throws SQLException {
        Domicilio d = new Domicilio(rs.getString("dom_ubigeo"), rs.getString("dom_direccion"), rs.getString("dom_urbanizacion"), rs.getString("dom_distrito"),
                rs.getString("dom_provincia"), rs.getString("dom_departamento"), rs.getString("codigo"));
        return new Establecimiento(rs.getObject("tenant_id", UUID.class), rs.getString("codigo"), rs.getString("nombre"), d, rs.getBoolean("activo"));
    }
}
