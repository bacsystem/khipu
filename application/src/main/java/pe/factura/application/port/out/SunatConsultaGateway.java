package pe.factura.application.port.out;

import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Servicios de consulta de SUNAT (solo producción): {@code billConsultService} para el estado y el CDR de un comprobante
 * propio, y {@code billValidService} para la validez de un comprobante de un tercero. Ambos autentican con las
 * credenciales SOL del tenant. Lanzan {@link SunatTransientException} ante fallos de red/servicio.
 */
public interface SunatConsultaGateway {
    /** {@code getStatusCdr}: devuelve el CDR (ZIP) si SUNAT lo tiene; {@code content} nulo si no hay constancia. */
    Consulta getStatusCdr(Tenant tenant, String rucEmisor, String tipo, String serie, long numero);
    /** {@code getStatus} de {@code billConsultService}: 0001 aceptado, 0002 rechazado, 0003 de baja; sin CDR. */
    Consulta getStatus(Tenant tenant, String rucEmisor, String tipo, String serie, long numero);
    /** {@code validaCDPcriterios} de {@code billValidService}: validez de un comprobante de un tercero por sus datos. */
    Consulta validar(Tenant tenant, String rucEmisor, String tipo, String serie, long numero, String tipoDocReceptor, String numDocReceptor, LocalDate fechaEmision, BigDecimal importeTotal);

    /** {@code statusResponse} de SUNAT: código, mensaje y, cuando corresponde, el ZIP del CDR. */
    record Consulta(String statusCode, String statusMessage, byte[] cdrZip) {
        public boolean conCdr() { return cdrZip != null && cdrZip.length > 0; }
        public boolean aceptado() { return "0001".equals(statusCode); }
        public boolean rechazado() { return "0002".equals(statusCode); }
        public boolean deBaja() { return "0003".equals(statusCode); }
    }
}
