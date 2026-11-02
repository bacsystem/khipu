package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JaxpXsdValidatorTest {
    @Test void xmlInvalidoLanzaConDetalle() {
        String xml = "<?xml version=\"1.0\"?><Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"><Basura/></Invoice>";
        assertThatThrownBy(() -> new JaxpXsdValidator().validar(xml, TipoDocumento.FACTURA))
                .isInstanceOf(DomainException.class).hasMessageContaining("Basura").extracting("codigo").isEqualTo("XSD_INVALIDO");
    }

    /**
     * Los esquemas UBL compilados retienen mucha memoria (los tipos, elementos y anotaciones de Xerces): cada contexto de Spring que construía su propio
     * validador compilaba otros cinco, y las pruebas E2E, que dejan sus contextos en caché, agotaban el heap de 1 GB. Un {@code Schema} es
     * inmutable y seguro entre hilos, así que se compila una vez por JVM.
     */
    @Test void losEsquemasSeCompilanUnaVezPorJvmYLosComparteCadaValidador() {
        new JaxpXsdValidator();
        int antes = JaxpXsdValidator.cargas();

        new JaxpXsdValidator();
        new JaxpXsdValidator();

        assertThat(JaxpXsdValidator.cargas()).isEqualTo(antes);
    }
}
