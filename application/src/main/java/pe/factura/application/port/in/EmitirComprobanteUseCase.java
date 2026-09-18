package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import java.util.UUID;

public interface EmitirComprobanteUseCase {
    Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd);
    /** Nota de crédito o débito sobre una factura aceptada de la empresa; mismo flujo que la factura (numerar, firmar, validar, enviar). */
    Comprobante emitirNota(UUID tenantId, EmitirNotaCommand cmd);
}
