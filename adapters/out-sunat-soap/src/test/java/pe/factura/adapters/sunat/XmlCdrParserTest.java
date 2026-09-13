package pe.factura.adapters.sunat;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.Cdr;
import static org.assertj.core.api.Assertions.assertThat;

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
}
