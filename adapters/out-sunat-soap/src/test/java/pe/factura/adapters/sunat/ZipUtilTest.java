package pe.factura.adapters.sunat;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.*;

class ZipUtilTest {
    @Test void comprimeConUnaSolaEntrada() throws Exception {
        byte[] zip = ZipUtil.comprimir("20100066603-01-F001-1.xml", "<x/>".getBytes());
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e = in.getNextEntry();
            assertThat(e.getName()).isEqualTo("20100066603-01-F001-1.xml");
            assertThat(new String(in.readAllBytes())).isEqualTo("<x/>");
            assertThat(in.getNextEntry()).isNull();
        }
    }
    @Test void extraeIgnorandoCarpetas() {
        byte[] zip = ZipUtil.comprimir("R-20100066603-01-F001-1.xml", "<cdr/>".getBytes());
        assertThat(new String(ZipUtil.extraerPrimero(zip, ".xml"))).isEqualTo("<cdr/>");
        assertThatThrownBy(() -> ZipUtil.extraerPrimero(zip, ".pdf")).isInstanceOf(IllegalStateException.class);
    }
}
