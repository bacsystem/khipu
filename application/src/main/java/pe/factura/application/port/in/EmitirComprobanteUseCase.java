package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import java.util.UUID;

public interface EmitirComprobanteUseCase {
    Comprobante emitirFactura(UUID tenantId, EmitirFacturaCommand cmd);
}
