package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JaxpXsdValidatorTest {
    @Test void xmlInvalidoLanzaConDetalle() {
        String xml = "<?xml version=\"1.0\"?><Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"><Basura/></Invoice>";
        assertThatThrownBy(() -> new JaxpXsdValidator().validar(xml, TipoDocumento.FACTURA))
                .isInstanceOf(DomainException.class).hasMessageContaining("Basura").extracting("codigo").isEqualTo("XSD_INVALIDO");
    }
}
