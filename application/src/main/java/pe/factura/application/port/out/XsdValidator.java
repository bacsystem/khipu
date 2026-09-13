package pe.factura.application.port.out;

import pe.factura.domain.documento.TipoDocumento;

public interface XsdValidator {
    // valida el XML ya firmado (con el ds:Signature dentro de ext:ExtensionContent);
    // lanza DomainException("XSD_INVALIDO", …)
    void validar(String xml, TipoDocumento tipo);
}
