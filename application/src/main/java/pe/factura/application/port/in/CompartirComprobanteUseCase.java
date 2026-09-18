package pe.factura.application.port.in;

import java.util.UUID;

public interface CompartirComprobanteUseCase {
    /**
     * Envía al correo indicado la representación impresa (PDF) con el XML firmado y, si existe, el CDR adjuntos.
     * Solo comprobantes aceptados por SUNAT: DomainException("NO_ACEPTADO") en otro estado.
     */
    void enviarPorCorreo(UUID tenantId, UUID id, String email, String mensaje);
}
