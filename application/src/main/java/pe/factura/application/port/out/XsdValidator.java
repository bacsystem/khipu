package pe.factura.application.port.out;

import pe.factura.domain.documento.TipoDocumento;

public interface XsdValidator {
    void validar(String xml, TipoDocumento tipo); // lanza DomainException("XSD_INVALIDO")
    /** VoidedDocuments (UBL 2.0 SUNAT). */
    void validarBaja(String xml);                 // lanza DomainException("XSD_INVALIDO")
}
