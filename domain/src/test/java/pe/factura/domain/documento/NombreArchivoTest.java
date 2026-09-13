package pe.factura.domain.documento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NombreArchivoTest {
    @Test void formatoSunat() {
        assertThat(NombreArchivo.de("20100066603", TipoDocumento.FACTURA, "F001", 1))
                .isEqualTo("20100066603-01-F001-1");
    }
}
