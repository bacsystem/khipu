package pe.factura.application.port.out;

import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.TipoDocumento;

public interface XsdValidator {
    void validar(String xml, TipoDocumento tipo); // lanza DomainException("XSD_INVALIDO")
    /** La baja (o el alta) contra su esquema: SummaryDocuments si va en un resumen diario, VoidedDocuments si no (UBL 2.0 SUNAT). */
    void validarBaja(String xml, ComunicacionBaja baja);   // lanza DomainException("XSD_INVALIDO")
}
