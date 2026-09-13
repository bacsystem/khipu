package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;
import java.util.UUID;

public interface EnviarDocumentoUseCase {
    /** Envía a SUNAT un comprobante FIRMADO o en ERROR_ENVIO y persiste el resultado. Nunca lanza por errores de SUNAT. */
    Comprobante enviar(UUID tenantId, UUID comprobanteId);
}
