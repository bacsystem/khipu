package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;

import java.util.List;
import java.util.UUID;

public interface RecuperarCdrUseCase {
    /**
     * Pide a SUNAT (getStatusCdr) la constancia de un comprobante propio ENVIADO, en ERROR_ENVIO o aceptado sin CDR en el
     * storage; si SUNAT la tiene, la guarda y aplica su resultado. Devuelve el comprobante (sin cambios si no hay CDR).
     */
    Comprobante recuperar(UUID tenantId, UUID comprobanteId);
    /** Barrido: intenta recuperar el CDR de todos los comprobantes sin constancia de empresas en producción. */
    List<Comprobante> recuperarPendientes();
}
