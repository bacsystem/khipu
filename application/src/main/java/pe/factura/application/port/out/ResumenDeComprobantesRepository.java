package pe.factura.application.port.out;

import pe.factura.domain.documento.EstadoDocumento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que se agrega sobre los comprobantes de una empresa en un rango de fechas de emisión (#15), en un solo recorrido por el índice de tenant y fecha. Solo trae los
 * datos crudos —cuántos hay en cada estado y cuánto suman los que cuentan como facturados—: qué estados son «emitidos», «aceptados» o «atención requerida» lo decide el
 * dominio ({@link EstadoDocumento}), una sola vez.
 */
public interface ResumenDeComprobantesRepository {
    /** El total facturado en una moneda: las facturas, boletas y notas de débito suman y las notas de crédito restan. Puede salir negativo si las notas superan lo emitido. */
    record Facturado(String moneda, BigDecimal total) {}

    /**
     * {@code porEstado} solo trae los estados que tienen comprobantes; {@code facturado} va ordenado por moneda y solo trae las monedas que tienen algo. {@code boletasSinEnviar}:
     * cuántas boletas (o notas que las modifican) siguen FIRMADAS sin que nadie las envíe y ya están en el 5.º día o más (#356); depende de la fecha, así que el estado solo no lo dice.
     */
    record Agregados(Map<EstadoDocumento, Long> porEstado, List<Facturado> facturado, long boletasSinEnviar) {}

    /** {@code desde}/{@code hasta} acotan la fecha de emisión, inclusive; uno nulo deja ese lado abierto. {@code hoy} fija desde cuándo una boleta sin enviar pide atención. */
    Agregados resumir(UUID tenantId, LocalDate desde, LocalDate hasta, LocalDate hoy);

    /**
     * Los comprobantes de un día en un estado y una moneda (#24): cuántos son y su neto (las notas de crédito restan). {@code moneda} y {@code neto} son nulos para los
     * documentos sin fila de totales (los que nunca llegaron a armarse, como un {@code RECIBIDO}).
     */
    record Celda(LocalDate fecha, EstadoDocumento estado, String moneda, long cuantos, BigDecimal neto) {}

    /**
     * Las celdas de {@code desde} a {@code hasta} (fecha de emisión, inclusive, los dos obligatorios), en un solo recorrido agrupado por día, estado y moneda. Qué cuenta como
     * emitido o facturado no se decide acá: lo decide el dominio sobre el estado de cada celda.
     */
    List<Celda> porDia(UUID tenantId, LocalDate desde, LocalDate hasta);
}
