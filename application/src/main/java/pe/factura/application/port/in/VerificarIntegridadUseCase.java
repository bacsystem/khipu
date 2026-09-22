package pe.factura.application.port.in;

import java.time.LocalDate;
import java.util.List;

/**
 * Verificación periódica de integridad del storage (#38): que cada comprobante firmado siga teniendo su XML —con el
 * DigestValue que se registró al firmar— y su CDR cuando SUNAT lo emitió. Detecta objetos perdidos, truncados o
 * alterados antes de que un cliente o un auditor los pida.
 */
public interface VerificarIntegridadUseCase {
    /** Revisa los comprobantes emitidos entre las dos fechas (inclusive), de todas las empresas. */
    Informe verificar(LocalDate desde, LocalDate hasta);

    record Informe(LocalDate desde, LocalDate hasta, int verificados, List<Problema> problemas) {
        public boolean limpio() { return problemas.isEmpty(); }
    }

    /** {@code tipo}: XML_FALTANTE, XML_CORRUPTO, CDR_FALTANTE, STORAGE_INACCESIBLE. */
    record Problema(java.util.UUID comprobanteId, java.util.UUID tenantId, String nombreArchivo, String tipo, String detalle) {}
}
