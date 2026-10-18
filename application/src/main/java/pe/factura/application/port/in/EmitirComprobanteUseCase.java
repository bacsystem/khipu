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
}
