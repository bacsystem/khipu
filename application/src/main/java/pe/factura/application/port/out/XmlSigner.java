package pe.factura.application.port.out;

import pe.factura.domain.tenant.CertificadoDigital;

public interface XmlSigner {
    FirmaResultado firmar(String xml, CertificadoDigital cert);
}
