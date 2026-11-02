package pe.factura.adapters.sunat;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.Cdr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlCdrParserTest {
    private byte[] zipDe(String recurso) throws Exception {
        byte[] xml = getClass().getResourceAsStream("/cdr/" + recurso).readAllBytes();
        return ZipUtil.comprimir(recurso, xml);
    }
    @Test void aceptado() throws Exception {
        Cdr c = new XmlCdrParser().parsear(zipDe("R-aceptado.xml"));
        assertThat(c.codigo()).isEqualTo("0");
        assertThat(c.descripcion()).contains("ha sido aceptada");
        assertThat(c.observaciones()).isEmpty();
        assertThat(c.esAceptado()).isTrue();
    }
    @Test void observado() throws Exception {
        Cdr c = new XmlCdrParser().parsear(zipDe("R-observado.xml"));
        assertThat(c.codigo()).isEqualTo("0");
        assertThat(c.observaciones()).containsExactly(
                "4252 - El dato ingresado como atributo @listName es incorrecto.",
                "4255 - El campo no cumple con el formato establecido.");
    }
    @Test void rechazado() throws Exception {
        Cdr c = new XmlCdrParser().parsear(zipDe("R-rechazado.xml"));
        assertThat(c.codigo()).isEqualTo("2324");
        assertThat(c.esRechazo()).isTrue();
    }

    /**
     * LOG-02: un CDR sin cbc:ResponseCode devolvía un Cdr con código "" que reventaba después, en el dominio, al decidir el estado. Es un CDR ilegible como
     * cualquier otro: IllegalStateException aquí, que el servicio de envío ya trata como falla de infraestructura reintentable.
     */
    @Test void sinResponseCodeEsIlegible() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
                    xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                    xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cac:DocumentResponse>
                    <cac:Response>
                      <cbc:Description>La Factura numero F001-1, ha sido aceptada</cbc:Description>
                    </cac:Response>
                  </cac:DocumentResponse>
                </ar:ApplicationResponse>
                """;
        assertThatThrownBy(() -> new XmlCdrParser().parsear(ZipUtil.comprimir("R-sin-codigo.xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ResponseCode");
    }
}
