package pe.factura.application.port.in;

import pe.factura.domain.documento.ComunicacionBaja;

import java.util.List;
import java.util.UUID;

public interface DarDeBajaUseCase {
    /** Genera, firma y envía la comunicación de baja del comprobante; consulta el ticket en el acto y, si SUNAT sigue procesando, deja el seguimiento al outbox. */
    ComunicacionBaja solicitar(UUID tenantId, UUID comprobanteId, String motivo);
    /** Reanuda una baja pendiente: envía si aún no tiene ticket, o consulta el ticket si ya lo tiene (lo usa el outbox). */
    ComunicacionBaja continuar(UUID tenantId, UUID bajaId);
    ComunicacionBaja obtener(UUID tenantId, UUID bajaId);                  // DomainException("NO_ENCONTRADO")
    List<ComunicacionBaja> deComprobante(UUID tenantId, UUID comprobanteId);
}
