package pe.factura.application.port.out;

import pe.factura.domain.documento.TipoDocumento;

public interface XsdValidator {
    // lanza DomainException("XSD_INVALIDO", …)
    void validar(String xml, TipoDocumento tipo);
}
