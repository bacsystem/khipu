package pe.factura.adapters.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SerieRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Serie;

import java.util.List;
import java.util.UUID;

public class JdbcSerieRepository implements SerieRepository {
    private final JdbcTemplate jdbc;
    public JdbcSerieRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public long siguienteNumero(UUID tenantId, TipoDocumento tipo, String serie) {
        List<Long> actual = jdbc.queryForList(
                "SELECT ultimo_numero FROM serie WHERE tenant_id = ? AND tipo = ? AND codigo = ? AND activa FOR UPDATE",
                Long.class, tenantId, tipo.codigo(), serie);
        if (actual.isEmpty()) throw new DomainException("SERIE_NO_CONFIGURADA", "Serie no configurada o inactiva: " + serie);
        long siguiente = actual.get(0) + 1;
        jdbc.update("UPDATE serie SET ultimo_numero = ? WHERE tenant_id = ? AND tipo = ? AND codigo = ?", siguiente, tenantId, tipo.codigo(), serie);
        return siguiente;
    }
    @Override public void crear(Serie s) {
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo, ultimo_numero, activa) VALUES (?, ?, ?, ?, ?)",
                s.tenantId(), s.tipo().codigo(), s.codigo(), s.ultimoNumero(), s.activa());
    }
    @Override public List<Serie> listar(UUID tenantId) {
        return jdbc.query("SELECT tenant_id, tipo, codigo, ultimo_numero, activa FROM serie WHERE tenant_id = ? ORDER BY tipo, codigo",
                (rs, i) -> new Serie(rs.getObject("tenant_id", UUID.class), TipoDocumento.porCodigo(rs.getString("tipo")),
                        rs.getString("codigo"), rs.getLong("ultimo_numero"), rs.getBoolean("activa")), tenantId);
    }
}
