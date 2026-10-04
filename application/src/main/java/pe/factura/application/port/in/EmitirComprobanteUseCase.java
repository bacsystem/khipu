package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import java.util.UUID;

public interface EmitirComprobanteUseCase {
    /** {@code repetida}: el pedido ya se había hecho con esa clave y se devuelve el comprobante de entonces, sin emitir otro. */
    record Emision(Comprobante comprobante, boolean repetida) {}

    default Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd) { return emitirFactura(tenantId, cmd, null).comprobante(); }

    /** Con {@code idempotencia} nula, igual que sin clave: cada llamada emite una factura nueva. */
    Emision emitirFactura(UUID tenantId, EmitirFacturaCommand cmd, Idempotencia idempotencia);

    /** Nota de crédito o débito sobre una factura aceptada de la empresa; mismo flujo que la factura (numerar, firmar, validar, enviar). */
    Comprobante emitirNota(UUID tenantId, EmitirNotaCommand cmd);
}
