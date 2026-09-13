package pe.factura.application.port.out;

import pe.factura.domain.tenant.Tenant;

public interface SunatBillingGateway {
    /** Comprime el XML en {nombreArchivo}.zip y llama a sendBill. Devuelve el ZIP del CDR. */
    byte[] sendBill(Tenant tenant, String nombreArchivo, byte[] xmlFirmado); // lanza SunatTransientException | SunatRechazoException
}
