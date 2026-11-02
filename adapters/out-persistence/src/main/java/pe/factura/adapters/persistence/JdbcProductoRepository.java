package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.ProductoRepository;
import pe.factura.domain.documento.ProductoGuardado;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los productos guardados (#320). El código interno es único por empresa cuando existe; la búsqueda por texto sigue la de los clientes: el código por prefijo y la descripción por subcadena, sin
 * distinguir mayúsculas ni tildes y con los comodines de LIKE tomados literalmente.
 */
@RequiredArgsConstructor
public class JdbcProductoRepository implements ProductoRepository {
    private final JdbcTemplate jdbc;

    private static final String COLUMNAS = "id, tenant_id, codigo, descripcion, unidad, tipo_afectacion_igv, precio_unitario, codigo_sunat, created_at, updated_at";

    private static final RowMapper<ProductoGuardado> FILA = (rs, i) -> new ProductoGuardado(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("codigo"),
            rs.getString("descripcion"), rs.getString("unidad"), rs.getString("tipo_afectacion_igv"), ProductoGuardado.escala(rs.getBigDecimal("precio_unitario")), rs.getString("codigo_sunat"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    @Override public boolean crear(ProductoGuardado p) {
        try {
            return jdbc.update("INSERT INTO producto (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    p.id(), p.tenantId(), p.codigo(), p.descripcion(), p.unidad(), p.tipoAfectacionIgv(), p.precioUnitario(), p.codigoSunat(), Timestamp.from(p.creadoEn()), Timestamp.from(p.actualizadoEn())) == 1;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override public boolean actualizar(ProductoGuardado p) {
        try {
            return jdbc.update("UPDATE producto SET codigo = ?, descripcion = ?, unidad = ?, tipo_afectacion_igv = ?, precio_unitario = ?, codigo_sunat = ?, updated_at = ? WHERE id = ? AND tenant_id = ?",
                    p.codigo(), p.descripcion(), p.unidad(), p.tipoAfectacionIgv(), p.precioUnitario(), p.codigoSunat(), Timestamp.from(p.actualizadoEn()), p.id(), p.tenantId()) == 1;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override public Optional<ProductoGuardado> buscar(UUID tenantId, UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM producto WHERE id = ? AND tenant_id = ?", FILA, id, tenantId).stream().findFirst();
    }

    @Override public List<ProductoGuardado> listar(UUID tenantId, String texto, int pagina, int porPagina) {
        List<Object> args = new ArrayList<>();
        String where = where(tenantId, texto, args);
        args.add(porPagina);
        args.add((long) (pagina - 1) * porPagina);
        return jdbc.query("SELECT " + COLUMNAS + " FROM producto" + where + " ORDER BY descripcion, id LIMIT ? OFFSET ?", FILA, args.toArray());
    }

    @Override public long contar(UUID tenantId, String texto) {
        List<Object> args = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT count(*) FROM producto" + where(tenantId, texto, args), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    @Override public boolean eliminar(UUID tenantId, UUID id) {
        return jdbc.update("DELETE FROM producto WHERE id = ? AND tenant_id = ?", id, tenantId) > 0;
    }

    private static String where(UUID tenantId, String texto, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE tenant_id = ?");
        args.add(tenantId);
        if (texto != null && !texto.isBlank()) {
            String literal = JdbcCuentasAdminRepository.escaparComodines(Normalizer.normalize(texto.strip(), Normalizer.Form.NFC));
            w.append(" AND (" + JdbcCuentasAdminRepository.SIN_TILDES.formatted("descripcion") + " ILIKE " + JdbcCuentasAdminRepository.SIN_TILDES.formatted("?") + " ESCAPE '\\'"
                    + " OR codigo ILIKE ? ESCAPE '\\')");
            args.add("%" + literal + "%");
            args.add(literal + "%");
        }
        return w.toString();
    }
}
