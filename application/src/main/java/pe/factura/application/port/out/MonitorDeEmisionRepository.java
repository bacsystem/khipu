package pe.factura.application.port.out;

import pe.factura.domain.documento.EstadoDocumento;

import java.time.Instant;
import java.time.LocalDate;
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

    /** #362 B14: cuántos resúmenes diarios o comunicaciones de baja con ticket siguen sin respuesta de SUNAT, y desde cuándo espera el más antiguo (cuando SUNAT dio el ticket); nulo si no hay. */
    record ResumenesPendientes(long cantidad, Instant masViejoDesde) {}

    /**
     * #356: las boletas (y notas que van en el resumen diario) que siguen sin informarse a SUNAT —FIRMADAS o con el envío fallando— y ya están en el 5.º día o más. {@code desdeElQuintoDia}:
     * todas esas; {@code desdeElSeptimoDia}: las que están en el último día del resumen diario o lo pasaron; {@code masAntiguaEmitidaEl}: la fecha de emisión de la más antigua, nula si no hay.
     */
    record BoletasPendientes(long desdeElQuintoDia, long desdeElSeptimoDia, LocalDate masAntiguaEmitidaEl) {}

    /** Los comprobantes creados desde {@code desde} (inclusive), agrupados por hora y estado. Solo trae las combinaciones que tienen comprobantes. */
    List<Conteo> porHoraYEstado(Instant desde);

    Cola cola(Instant ahora);

    /**
     * Las comunicaciones de baja (RA) y resúmenes diarios (RC) enviadas a SUNAT (con ticket, estado {@code ENVIADA}) cuyo ticket se obtuvo antes de {@code enviadosAntesDe} y
     * siguen sin respuesta. Se mide desde que SUNAT dio el ticket, no desde que se creó la fila: uno que esperó horas en {@code ERROR_ENVIO} no lleva horas sin respuesta. Solo
     * cuenta lo que SUNAT recibió: lo que no tiene ticket (generado, con error de envío) lo vigila la cola de envíos.
     */
    ResumenesPendientes resumenesSinRespuesta(Instant enviadosAntesDe);

    /** Las boletas sin informar de todas las empresas, con la fecha de hoy que fija desde cuándo piden atención. */
    BoletasPendientes boletasSinInformar(LocalDate hoy);
}
