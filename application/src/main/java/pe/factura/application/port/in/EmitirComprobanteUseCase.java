package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import java.util.UUID;

public interface EmitirComprobanteUseCase {
    /** {@code repetida}: el pedido ya se había hecho con esa clave y se devuelve el comprobante de entonces, sin emitir otro. */
    record Emision(Comprobante comprobante, boolean repetida) {}

    default Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd) { return emitirFactura(tenantId, cmd, null).comprobante(); }

    /** Con {@code idempotencia} nula, igual que sin clave: cada llamada emite una factura nueva. */
    Emision emitirFactura(UUID tenantId, EmitirFacturaCommand cmd, Idempotencia idempotencia);

    /**
     * #405: el comprobante que {@link #emitirFactura} armaría con este pedido, sin numerarlo, firmarlo, guardarlo ni enviarlo: los mismos importes y los mismos errores de
     * dominio. No hace lo que depende de datos guardados (que la factura de anticipo exista, el padrón de RUC, el cupo del plan, que haya serie y certificado).
     */
    Comprobante calcularFactura(UUID tenantId, EmitirFacturaCommand cmd);

    /** Nota de crédito o débito sobre una factura o boleta aceptada de la empresa; mismo flujo que la factura (numerar, firmar, validar, enviar). */
    default Comprobante emitirNota(UUID tenantId, EmitirNotaCommand cmd) { return emitirNota(tenantId, cmd, null).comprobante(); }

    /**
     * Con {@code idempotencia} (#355) un reintento de la misma nota devuelve la ya emitida, sin numerar otra; nula, igual que sin clave.
     * Las claves de las notas son un espacio aparte del de las facturas: la misma cadena en {@code /v1/facturas} y en {@code /v1/notas} no se cruza.
     */
    Emision emitirNota(UUID tenantId, EmitirNotaCommand cmd, Idempotencia idempotencia);

    /** Liquidación de compra (04, #327) a una persona sin RUC; mismo flujo que la factura (numerar con la serie bloqueada, firmar, validar, guardar y enviar). */
    default Comprobante emitirLiquidacionCompra(UUID tenantId, EmitirLiquidacionCommand cmd) { return emitirLiquidacionCompra(tenantId, cmd, null).comprobante(); }

    /** Con {@code idempotencia} un reintento devuelve la liquidación ya emitida; sus claves son un espacio aparte del de las facturas y las notas. */
    Emision emitirLiquidacionCompra(UUID tenantId, EmitirLiquidacionCommand cmd, Idempotencia idempotencia);

    /** La liquidación que {@link #emitirLiquidacionCompra} armaría, sin numerarla, firmarla, guardarla ni enviarla (como {@link #calcularFactura}). */
    Comprobante calcularLiquidacionCompra(UUID tenantId, EmitirLiquidacionCommand cmd);

    /**
     * Comprobante de retención (20) o de percepción (40), #40, de una empresa que SUNAT designó agente de ese tipo ({@code AGENTE_NO_DESIGNADO} si no); mismo flujo que la factura
     * (numerar con la serie R###/P### bloqueada, firmar, validar, guardar y enviar), al servicio de otros CPE de SUNAT.
     */
    default Comprobante emitirDeAgente(UUID tenantId, EmitirAgenteCommand cmd) { return emitirDeAgente(tenantId, cmd, null).comprobante(); }

    /** Con {@code idempotencia} un reintento devuelve el comprobante ya emitido; las claves de las retenciones y las de las percepciones son, cada una, un espacio aparte. */
    Emision emitirDeAgente(UUID tenantId, EmitirAgenteCommand cmd, Idempotencia idempotencia);

    /** El comprobante que {@link #emitirDeAgente} armaría, sin numerarlo, firmarlo, guardarlo ni enviarlo (como {@link #calcularFactura}); también exige la designación. */
    Comprobante calcularDeAgente(UUID tenantId, EmitirAgenteCommand cmd);
}
