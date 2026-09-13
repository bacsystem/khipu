package pe.factura.adapters.sunat;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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

    @Test void extraeDentroDeZipConCarpetaDummy() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            out.putNextEntry(new ZipEntry("dummy/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("R-20100066603-01-F001-1.xml"));
            out.write("<cdr/>".getBytes());
            out.closeEntry();
        }
        assertThat(new String(ZipUtil.extraerPrimero(bos.toByteArray(), ".xml"))).isEqualTo("<cdr/>");
    }

    @Test void extraeArchivoDentroDeCarpeta() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            out.putNextEntry(new ZipEntry("carpeta/R-x.xml"));
            out.write("<cdr/>".getBytes());
            out.closeEntry();
        }
        assertThat(new String(ZipUtil.extraerPrimero(bos.toByteArray(), ".xml"))).isEqualTo("<cdr/>");
    }
}
