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

    /** {@code porEstado} solo trae los estados que tienen comprobantes; {@code facturado} va ordenado por moneda y solo trae las monedas que tienen algo. */
    record Agregados(Map<EstadoDocumento, Long> porEstado, List<Facturado> facturado) {}

    /** {@code desde}/{@code hasta} acotan la fecha de emisión, inclusive; uno nulo deja ese lado abierto. */
    Agregados resumir(UUID tenantId, LocalDate desde, LocalDate hasta);
}
