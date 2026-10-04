package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.PagoRepository;
import pe.factura.domain.plan.MedioDePago;
import pe.factura.domain.plan.Pago;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Los pagos registrados a mano (#194). Solo se agregan. El mismo apunte repetido (misma cuenta, medio y referencia, sin distinguir mayúsculas) lo detiene el índice único
 * parcial: el insert pasa a ser «no hacer nada» y el repositorio lo cuenta como repetido, sin lanzar una excepción que aborte la transacción del administrador.
 */
@RequiredArgsConstructor
public class JdbcPagoRepository implements PagoRepository {
    private final JdbcTemplate jdbc;

    @Override public boolean registrar(Pago p) {
        return jdbc.update("""
                INSERT INTO pago (id, cuenta_id, suscripcion_id, periodo_desde, periodo_hasta, monto, medio, fecha_de_pago, referencia, nota, registrado_en, extendio_hasta)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (cuenta_id, medio, lower(referencia)) WHERE referencia IS NOT NULL DO NOTHING
                """, p.id(), p.cuentaId(), p.suscripcionId(), Date.valueOf(p.periodoDesde()), Date.valueOf(p.periodoHasta()), p.monto(), p.medio().name(), Date.valueOf(p.fechaDePago()),
                p.referencia(), p.nota(), Timestamp.from(p.registradoEn()), p.extendioHasta() == null ? null : Timestamp.from(p.extendioHasta())) == 1;
    }

    @Override public List<Pago> deLaCuenta(UUID cuentaId, int pagina, int porPagina) {
        return jdbc.query("""
                SELECT id, cuenta_id, suscripcion_id, periodo_desde, periodo_hasta, monto, medio, fecha_de_pago, referencia, nota, registrado_en, extendio_hasta
                FROM pago WHERE cuenta_id = ? ORDER BY fecha_de_pago DESC, registrado_en DESC, id LIMIT ? OFFSET ?
                """, this::mapear, cuentaId, porPagina, (long) (pagina - 1) * porPagina);
    }

    @Override public long contarDeLaCuenta(UUID cuentaId) {
        return jdbc.queryForObject("SELECT count(*) FROM pago WHERE cuenta_id = ?", Long.class, cuentaId);
    }

    private Pago mapear(ResultSet rs, int i) throws SQLException {
        Timestamp extendio = rs.getTimestamp("extendio_hasta");
        Instant hasta = extendio == null ? null : extendio.toInstant();
        return new Pago(rs.getObject("id", UUID.class), rs.getObject("cuenta_id", UUID.class), rs.getObject("suscripcion_id", UUID.class), rs.getDate("periodo_desde").toLocalDate(),
                rs.getDate("periodo_hasta").toLocalDate(), rs.getBigDecimal("monto"), MedioDePago.valueOf(rs.getString("medio")), rs.getDate("fecha_de_pago").toLocalDate(),
                rs.getString("referencia"), rs.getString("nota"), rs.getTimestamp("registrado_en").toInstant(), hasta);
    }
}
