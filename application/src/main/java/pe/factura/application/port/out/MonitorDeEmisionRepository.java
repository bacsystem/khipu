package pe.factura.application.port.out;

import pe.factura.domain.documento.EstadoDocumento;

import java.time.Instant;
import java.util.List;

/**
 * Lo que el monitor global de emisión (#195) lee de **todas** las empresas a la vez: cuántos comprobantes se crearon cada hora, en qué estado están hoy, y cómo va el
 * outbox. Solo datos crudos; qué es «atención», «en camino» o «worker caído» lo deciden el dominio y el servicio.
 */
public interface MonitorDeEmisionRepository {
    /** Los comprobantes creados en esa hora (el inicio de la hora, en UTC: las horas de Lima caen en las mismas fronteras) y su estado de hoy. */
    record Conteo(Instant hora, EstadoDocumento estado, long cantidad) {}

    /**
     * Cómo está la cola de envíos pendientes a SUNAT. {@code vencidos}: ya tocaba enviarlos y nadie los tomó (no cuenta lo que está en proceso ni lo que espera su próximo
     * reintento). {@code masViejoDesde}: cuándo se programó el más antiguo; nulo si la cola está vacía. {@code vencidoDesde}: desde cuándo está vencido el que más espera;
     * nulo si no hay vencidos.
     */
    record Cola(long pendientes, long vencidos, Instant masViejoDesde, Instant vencidoDesde) {}

    /** Los comprobantes creados desde {@code desde} (inclusive), agrupados por hora y estado. Solo trae las combinaciones que tienen comprobantes. */
    List<Conteo> porHoraYEstado(Instant desde);

    Cola cola(Instant ahora);
}
