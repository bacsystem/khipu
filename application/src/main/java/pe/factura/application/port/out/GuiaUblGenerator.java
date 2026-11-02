package pe.factura.application.port.out;

import pe.factura.domain.documento.GuiaRemision;
import pe.factura.domain.tenant.Tenant;

/** El XML sin firmar (UTF-8) de una guía de remisión numerada (#39): UBL 2.1 {@code DespatchAdvice}. {@code t} es el emisor. */
public interface GuiaUblGenerator {
    String generar(GuiaRemision g, Tenant t);
}
