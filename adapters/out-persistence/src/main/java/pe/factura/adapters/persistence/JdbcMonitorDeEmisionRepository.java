package pe.factura.adapters.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.factura.application.port.out.MonitorDeEmisionRepository;
import pe.factura.domain.documento.EstadoDocumento;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * El monitor global de emisión (#195): lo reciente de todas las empresas, por el índice de `created_at`. Las horas se agrupan en UTC de forma explícita (no según la zona
 * de la sesión de la base): Lima va 5 horas atrás sin horario de verano, así que sus horas caen en las mismas fronteras. La cola del outbox usa la misma condición de
 * «vencido» que el trabajo que la vacía ({@code JdbcOutboxRepository.tomarVencidas}): ya tocaba y nadie la tiene tomada.
 */
@RequiredArgsConstructor
public class JdbcMonitorDeEmisionRepository implements MonitorDeEmisionRepository {
    private final JdbcTemplate jdbc;

    @Override public List<Conteo> porHoraYEstado(Instant desde) {
        return jdbc.query("""
                SELECT extract(epoch FROM date_trunc('hour', created_at AT TIME ZONE 'UTC'))::bigint AS hora, estado, count(*) AS n
                FROM documento WHERE created_at >= ?
                GROUP BY 1, 2 ORDER BY 1, 2
                """, (rs, i) -> new Conteo(Instant.ofEpochSecond(rs.getLong("hora")), EstadoDocumento.valueOf(rs.getString("estado")), rs.getLong("n")), Timestamp.from(desde));
    }

    @Override public Cola cola(Instant ahora) {
        Timestamp t = Timestamp.from(ahora);
        return jdbc.queryForObject("""
                SELECT count(*) AS pendientes,
                       count(*) FILTER (WHERE siguiente_intento <= ? AND (locked_until IS NULL OR locked_until < ?)) AS vencidos,
                       min(created_at) AS mas_viejo,
                       min(siguiente_intento) FILTER (WHERE siguiente_intento <= ? AND (locked_until IS NULL OR locked_until < ?)) AS vencido_desde
                FROM outbox
                """, (rs, i) -> new Cola(rs.getLong("pendientes"), rs.getLong("vencidos"), instante(rs.getTimestamp("mas_viejo")), instante(rs.getTimestamp("vencido_desde"))), t, t, t, t);
    }

    /**
     * Lo ENVIADO con ticket (el índice parcial `ix_comunicacion_baja_enviada` lo encuentra sin recorrer la tabla) cuyo ticket se obtuvo antes del límite, estrictamente. Se mide
     * desde `enviada_en` y no desde `created_at`: un resumen que esperó horas en ERROR_ENVIO no lleva horas esperando respuesta.
     */
    @Override public ResumenesPendientes resumenesSinRespuesta(Instant enviadosAntesDe) {
        return jdbc.queryForObject("SELECT count(*) AS n, min(enviada_en) AS mas_viejo FROM comunicacion_baja WHERE estado = 'ENVIADA' AND ticket IS NOT NULL AND enviada_en < ?",
                (rs, i) -> new ResumenesPendientes(rs.getLong("n"), instante(rs.getTimestamp("mas_viejo"))), Timestamp.from(enviadosAntesDe));
    }

    /**
     * #356: lo de boleta por enviar —FIRMADO o con el envío fallando— que ya está en el 5.º día o más, de todas las empresas. Es la misma regla de {@code Comprobante.porInformar}
     * ({@link BoletasPorInformar}). El índice parcial de lo FIRMADO y el de lo que está en error encuentran estas filas sin recorrer la tabla.
     */
    @Override public BoletasPendientes boletasSinInformar(LocalDate hoy) {
        return jdbc.queryForObject("""
                SELECT count(*) AS n, count(*) FILTER (WHERE d.fecha_emision <= ?) AS urgentes, min(d.fecha_emision) AS mas_antigua
                FROM documento d LEFT JOIN comprobante c ON c.documento_id = d.id
                WHERE %s
                """.formatted(BoletasPorInformar.sinInformar("c")), (rs, i) -> new BoletasPendientes(rs.getLong("n"), rs.getLong("urgentes"), rs.getObject("mas_antigua", LocalDate.class)),
                BoletasPorInformar.corteUrgente(hoy), BoletasPorInformar.corte(hoy));
    }

    private static Instant instante(Timestamp t) { return t == null ? null : t.toInstant(); }
}
