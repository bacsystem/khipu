package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.SerieRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Serie;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcSerieRepository implements SerieRepository {
    private final JdbcTemplate jdbc;

    @Override public long siguienteNumero(UUID tenantId, TipoDocumento tipo, String serie) {
        List<Long> actual = jdbc.queryForList(
                "SELECT ultimo_numero FROM serie WHERE tenant_id = ? AND tipo = ? AND codigo = ? AND activa FOR UPDATE",
                Long.class, tenantId, tipo.codigo(), serie);
        if (actual.isEmpty()) throw new DomainException("SERIE_NO_CONFIGURADA", "Serie no configurada o inactiva: " + serie);
        long siguiente = actual.get(0) + 1;
        // El chequeo va dentro del FOR UPDATE: es el único punto donde se sabe, sin carrera, cuál va a ser el número.
        // Sin esto la serie sigue avanzando más allá de los 8 dígitos de la regla 1001 y cada emisión gasta un
        // correlativo que SUNAT va a rechazar (la serie no se puede editar ni reiniciar).
        if (siguiente > Serie.NUMERO_MAXIMO)
            throw new DomainException("SERIE_AGOTADA", "1001 - La serie " + serie + " agotó sus 8 dígitos de correlativo (" + Serie.NUMERO_MAXIMO + "). Creá una serie nueva para seguir emitiendo.");
        jdbc.update("UPDATE serie SET ultimo_numero = ? WHERE tenant_id = ? AND tipo = ? AND codigo = ?", siguiente, tenantId, tipo.codigo(), serie);
        return siguiente;
    }
    @Override public void avanzarHasta(UUID tenantId, TipoDocumento tipo, String serie, long numero) {
        List<Long> actual = jdbc.queryForList(
                "SELECT ultimo_numero FROM serie WHERE tenant_id = ? AND tipo = ? AND codigo = ? AND activa FOR UPDATE",
                Long.class, tenantId, tipo.codigo(), serie);
        if (actual.isEmpty()) throw new DomainException("SERIE_NO_CONFIGURADA", "Serie no configurada o inactiva: " + serie);
        if (numero > Serie.NUMERO_MAXIMO)
            throw new DomainException("SERIE_AGOTADA", "1001 - El correlativo " + numero + " de la serie " + serie + " pasa los 8 dígitos que admite SUNAT.");
        jdbc.update("UPDATE serie SET ultimo_numero = GREATEST(ultimo_numero, ?) WHERE tenant_id = ? AND tipo = ? AND codigo = ? AND activa",
                numero, tenantId, tipo.codigo(), serie);
    }
    @Override public void crear(Serie s) {
        jdbc.update("INSERT INTO serie (tenant_id, tipo, codigo, ultimo_numero, activa, establecimiento) VALUES (?, ?, ?, ?, ?, ?)",
                s.tenantId(), s.tipo().codigo(), s.codigo(), s.ultimoNumero(), s.activa(), s.establecimiento());
    }
    @Override public List<Serie> listar(UUID tenantId) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? ORDER BY tipo, codigo", this::mapear, tenantId);
    }

    private static final String SELECT = "SELECT tenant_id, tipo, codigo, ultimo_numero, activa, establecimiento FROM serie";

    private Serie mapear(ResultSet rs, int i) throws SQLException {
        return new Serie(rs.getObject("tenant_id", UUID.class), TipoDocumento.porCodigo(rs.getString("tipo")),
                rs.getString("codigo"), rs.getLong("ultimo_numero"), rs.getBoolean("activa"), rs.getString("establecimiento"));
    }
}
