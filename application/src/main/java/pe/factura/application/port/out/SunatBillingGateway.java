package pe.factura.application.port.out;

import pe.factura.domain.tenant.Tenant;

public interface SunatBillingGateway {
    /** Comprime el XML en {nombreArchivo}.zip y llama a sendBill. Devuelve el ZIP del CDR. */
    byte[] sendBill(Tenant tenant, String nombreArchivo, byte[] xmlFirmado); // lanza SunatTransientException | SunatRechazoException
    /** Comprime el XML en {nombreArchivo}.zip y llama a sendSummary (RA/RC). Devuelve el ticket que SUNAT asigna al proceso. */
    String sendSummary(Tenant tenant, String nombreArchivo, byte[] xmlFirmado); // lanza SunatTransientException | SunatRechazoException
    /** Consulta el ticket de un sendSummary: {@code 0} procesado (ZIP del CDR), {@code 98} en proceso, {@code 99} con errores (ZIP del CDR de rechazo). */
    EstadoTicket getStatus(Tenant tenant, String ticket); // lanza SunatTransientException | SunatRechazoException
    /**
     * El ticket de un resumen se consulta en el servicio que lo emitió: el de un resumen de reversiones ({@code RUC-RR-…}, #40) en el de retenciones y percepciones, los demás en el
     * de facturas. {@code nombreArchivo} es el del resumen enviado, sin extensión; por defecto se ignora.
     */
    default EstadoTicket getStatus(Tenant tenant, String nombreArchivo, String ticket) { return getStatus(tenant, ticket); }

    record EstadoTicket(String statusCode, byte[] cdrZip) {
        public boolean enProceso() { return "98".equals(statusCode); }
        public boolean procesado() { return "0".equals(statusCode) || "99".equals(statusCode); }
    }
}
