package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.BajaRepository;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.TipoDocumento;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class JdbcBajaRepository implements BajaRepository {
    private final JdbcTemplate jdbc;

    private static final String COLS = "id, tenant_id, fecha_generacion, correlativo, comprobante_id, tipo_comprobante, serie, numero, fecha_referencia, motivo, "
            + "estado, ticket, xml_key, cdr_key, cdr_codigo, cdr_descripcion, intentos, ultimo_error";

    @Override public void guardar(ComunicacionBaja b) {
        int filas = jdbc.update("UPDATE comunicacion_baja SET estado = ?, ticket = ?, xml_key = ?, cdr_key = ?, cdr_codigo = ?, cdr_descripcion = ?, intentos = ?, ultimo_error = ?, updated_at = now() WHERE id = ? AND tenant_id = ?",
                b.estado().name(), b.ticket(), b.xmlKey(), b.cdrKey(), b.cdr() == null ? null : b.cdr().codigo(), b.cdr() == null ? null : b.cdr().descripcion(), b.intentos(), b.ultimoError(), b.id(), b.tenantId());
        if (filas > 0) return;
        jdbc.update("INSERT INTO comunicacion_baja (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                b.id(), b.tenantId(), Date.valueOf(b.fechaGeneracion()), b.correlativo(), b.comprobanteId(), b.tipoComprobante().codigo(), b.serie(), b.numero(),
                Date.valueOf(b.fechaReferencia()), b.motivo(), b.estado().name(), b.ticket(), b.xmlKey(), b.cdrKey(),
                b.cdr() == null ? null : b.cdr().codigo(), b.cdr() == null ? null : b.cdr().descripcion(), b.intentos(), b.ultimoError());
    }

    @Override public Optional<ComunicacionBaja> buscar(UUID tenantId, UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM comunicacion_baja WHERE id = ? AND tenant_id = ?", this::mapear, id, tenantId).stream().findFirst();
    }

    @Override public List<ComunicacionBaja> deComprobante(UUID tenantId, UUID comprobanteId) {
        return jdbc.query("SELECT " + COLS + " FROM comunicacion_baja WHERE tenant_id = ? AND comprobante_id = ? ORDER BY created_at DESC", this::mapear, tenantId, comprobanteId);
    }

    /** Serializa las bajas del día de la empresa con el bloqueo de la fila del tenant, así dos peticiones concurrentes no obtienen el mismo RA-yyyymmdd-N. */
    @Override public int siguienteCorrelativo(UUID tenantId, LocalDate fecha) {
        jdbc.queryForObject("SELECT id FROM tenant WHERE id = ? FOR UPDATE", UUID.class, tenantId);
        Integer max = jdbc.queryForObject("SELECT coalesce(max(correlativo), 0) FROM comunicacion_baja WHERE tenant_id = ? AND fecha_generacion = ?", Integer.class, tenantId, Date.valueOf(fecha));
        return (max == null ? 0 : max) + 1;
    }

    private ComunicacionBaja mapear(ResultSet rs, int i) throws SQLException {
        Cdr cdr = rs.getString("cdr_codigo") == null ? null : new Cdr(rs.getString("cdr_codigo"), rs.getString("cdr_descripcion"), List.of());
        return ComunicacionBaja.rehidratar(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getDate("fecha_generacion").toLocalDate(), rs.getInt("correlativo"),
                rs.getObject("comprobante_id", UUID.class), TipoDocumento.porCodigo(rs.getString("tipo_comprobante")), rs.getString("serie"), rs.getLong("numero"),
                rs.getDate("fecha_referencia").toLocalDate(), rs.getString("motivo"), ComunicacionBaja.EstadoBaja.valueOf(rs.getString("estado")), rs.getString("ticket"),
                rs.getString("xml_key"), rs.getString("cdr_key"), cdr, rs.getInt("intentos"), rs.getString("ultimo_error"));
    }
}
