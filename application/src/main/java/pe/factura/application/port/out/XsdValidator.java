package pe.factura.application.port.out;

import pe.factura.domain.documento.TipoDocumento;

public interface XsdValidator {
    void validar(String xml, TipoDocumento tipo); // lanza DomainException("XSD_INVALIDO")
    /** La baja de un comprobante de ese tipo: SummaryDocuments para una boleta, VoidedDocuments para los demás (UBL 2.0 SUNAT). */
    void validarBaja(String xml, TipoDocumento tipoComprobante);   // lanza DomainException("XSD_INVALIDO")
}
