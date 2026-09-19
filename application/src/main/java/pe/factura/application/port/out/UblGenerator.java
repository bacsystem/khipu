package pe.factura.application.port.out;

import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Tenant;

public interface UblGenerator {
    // XML sin firmar, UTF-8
    String generar(Comprobante c, Tenant t);
    /** VoidedDocuments (UBL 2.0) sin firmar. */
    String generarBaja(ComunicacionBaja baja, Tenant t);
}
