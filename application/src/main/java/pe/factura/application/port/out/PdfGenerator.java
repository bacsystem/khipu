package pe.factura.application.port.out;

import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Tenant;

/**
 * Representación impresa de un comprobante numerado y firmado con el diseño de {@code t.personalizacionPdf()};
 * {@code contenidoQr} es el texto que codifica el QR (ver {@code CodigoQr}) y {@code logo} los bytes del logo de la empresa o {@code null}.
 */
public interface PdfGenerator {
    byte[] generar(Comprobante c, Tenant t, String contenidoQr, byte[] logo);
}
