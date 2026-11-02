package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.factura.application.port.out.ClienteRepository;
import pe.factura.domain.documento.ClienteGuardado;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los clientes guardados (#319). El documento es único por empresa; la búsqueda por texto sigue la del listado de comprobantes: el documento por prefijo y el nombre por
 * subcadena, sin distinguir mayúsculas ni tildes (con {@code translate}, del núcleo de Postgres) y con los comodines de LIKE tomados literalmente.
 */
@RequiredArgsConstructor
public class JdbcClienteRepository implements ClienteRepository {
    private final JdbcTemplate jdbc;

    private static final String COLUMNAS = "id, tenant_id, tipo_doc, num_doc, razon_social, direccion, pais, email, created_at, updated_at";

    private static final RowMapper<ClienteGuardado> FILA = (rs, i) -> new ClienteGuardado(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("tipo_doc"),
            rs.getString("num_doc"), rs.getString("razon_social"), rs.getString("direccion"), rs.getString("pais"), rs.getString("email"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    @Override public boolean crear(ClienteGuardado c) {
        return jdbc.update("INSERT INTO cliente (id, tenant_id, tipo_doc, num_doc, razon_social, direccion, pais, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenant_id, tipo_doc, num_doc) DO NOTHING",
                c.id(), c.tenantId(), c.tipoDoc(), c.numDoc(), c.razonSocial(), c.direccion(), c.pais(), c.email(), Timestamp.from(c.creadoEn()), Timestamp.from(c.actualizadoEn())) == 1;
    }

    @Override public boolean actualizar(ClienteGuardado c) {
        try {
            return jdbc.update("UPDATE cliente SET tipo_doc = ?, num_doc = ?, razon_social = ?, direccion = ?, pais = ?, email = ?, updated_at = ? WHERE id = ? AND tenant_id = ?",
                    c.tipoDoc(), c.numDoc(), c.razonSocial(), c.direccion(), c.pais(), c.email(), Timestamp.from(c.actualizadoEn()), c.id(), c.tenantId()) == 1;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override public Optional<ClienteGuardado> buscar(UUID tenantId, UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM cliente WHERE id = ? AND tenant_id = ?", FILA, id, tenantId).stream().findFirst();
    }

    @Override public Optional<ClienteGuardado> buscarPorDocumento(UUID tenantId, String tipoDoc, String numDoc) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM cliente WHERE tenant_id = ? AND tipo_doc = ? AND num_doc = ?", FILA, tenantId, tipoDoc, numDoc).stream().findFirst();
    }

    @Override public List<ClienteGuardado> listar(UUID tenantId, String texto, int pagina, int porPagina) {
        List<Object> args = new ArrayList<>();
        String where = where(tenantId, texto, args);
        args.add(porPagina);
        args.add((long) (pagina - 1) * porPagina);
        return jdbc.query("SELECT " + COLUMNAS + " FROM cliente" + where + " ORDER BY razon_social, id LIMIT ? OFFSET ?", FILA, args.toArray());
    }

    @Override public long contar(UUID tenantId, String texto) {
        List<Object> args = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT count(*) FROM cliente" + where(tenantId, texto, args), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    @Override public boolean eliminar(UUID tenantId, UUID id) {
        return jdbc.update("DELETE FROM cliente WHERE id = ? AND tenant_id = ?", id, tenantId) > 0;
    }

    private static String where(UUID tenantId, String texto, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE tenant_id = ?");
        args.add(tenantId);
        if (texto != null && !texto.isBlank()) {
            String literal = JdbcCuentasAdminRepository.escaparComodines(Normalizer.normalize(texto.strip(), Normalizer.Form.NFC));
            w.append(" AND (" + JdbcCuentasAdminRepository.SIN_TILDES.formatted("razon_social") + " ILIKE " + JdbcCuentasAdminRepository.SIN_TILDES.formatted("?") + " ESCAPE '\\'"
                    + " OR num_doc ILIKE ? ESCAPE '\\')");
            args.add("%" + literal + "%");
            args.add(literal + "%");
        }
        return w.toString();
    }
}
