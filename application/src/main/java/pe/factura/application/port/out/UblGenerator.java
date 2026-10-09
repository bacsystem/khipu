package pe.factura.application.port.out;

import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Tenant;

public interface UblGenerator {
    // XML sin firmar, UTF-8
    String generar(Comprobante c, Tenant t);
    /**
     * La baja sin firmar (UBL 2.0): VoidedDocuments para una factura o nota, SummaryDocuments con la línea en estado 3 para una boleta (#20). El resumen
     * repite el comprador, los valores de venta y los tributos de la boleta, por eso recibe el comprobante dado de baja.
     */
    String generarBaja(ComunicacionBaja baja, Comprobante comprobante, Tenant t);
}
