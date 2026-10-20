package pe.factura.application.port.out;

import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Tenant;

import java.util.List;

public interface UblGenerator {
    // XML sin firmar, UTF-8
    String generar(Comprobante c, Tenant t);
    /**
     * La baja sin firmar (UBL 2.0): VoidedDocuments para una factura o nota, SummaryDocuments con la línea en estado 3 para una boleta (#20). El resumen
     * repite el comprador, los valores de venta y los tributos de la boleta, por eso recibe el comprobante dado de baja.
     */
    String generarBaja(ComunicacionBaja baja, Comprobante comprobante, Tenant t);
    /**
     * El resumen diario (SummaryDocuments) de varias líneas (#301): {@code filas.get(i)} es la línea {@code i + 1} y {@code comprobantes.get(i)} su comprobante. Todas las
     * filas son del mismo resumen (mismo identificador) y deben ser de boleta.
     */
    String generarResumen(List<ComunicacionBaja> filas, List<Comprobante> comprobantes, Tenant t);
}
