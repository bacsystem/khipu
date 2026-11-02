package pe.factura.application.port.out;

import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.tenant.Tenant;

/**
 * Representación impresa de una guía de remisión firmada (#39), con el diseño de {@code t.personalizacionPdf()}. {@code enlaceQr}: la URL de consulta que
 * SUNAT pone en el CDR, que el QR codifica; {@code null} mientras no haya CDR (o si el CDR no la trae), y entonces el PDF sale sin QR. {@code logo}: los
 * bytes del logo de la empresa o {@code null}.
 */
public interface GuiaPdfGenerator {
    byte[] generar(GuiaRemision g, Tenant t, String enlaceQr, byte[] logo);
}
