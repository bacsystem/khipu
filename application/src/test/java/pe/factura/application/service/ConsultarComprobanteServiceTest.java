package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultarComprobanteServiceTest {
    private final Fakes.Comprobantes repo = new Fakes.Comprobantes();
    private final Fakes.Storage storage = new Fakes.Storage();
    private final ConsultarComprobanteService service = new ConsultarComprobanteService(repo, storage);
    private final UUID tenant = UUID.randomUUID();

    private static byte[] zip(String nombre, String contenido) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry(nombre));
            z.write(contenido.getBytes());
            z.closeEntry();
        }
        return bos.toByteArray();
    }

    private Comprobante conCdr(byte[] zip) {
        Comprobante c = Fakes.facturaFirmada(tenant, storage);
        c.marcarEnviado();
        storage.guardar("cdr/" + c.id(), zip);
        c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr/" + c.id());
        repo.guardar(c);
        return c;
    }

    @Test void cdrXmlExtraeElXmlDelZip() throws Exception {
        Comprobante c = conCdr(zip("R-F001-1.xml", "<ApplicationResponse/>"));
        assertThat(new String(service.cdrXml(tenant, c.id()))).isEqualTo("<ApplicationResponse/>");
    }

    @Test void cdrXmlFallaSiElZipNoTieneXml() throws Exception {
        Comprobante c = conCdr(zip("leeme.txt", "hola"));
        assertThatThrownBy(() -> service.cdrXml(tenant, c.id()))
                .isInstanceOf(DomainException.class).hasMessageContaining("XML");
    }

    @Test void cdrXmlFallaSinCdr() {
        Comprobante c = Fakes.facturaFirmada(tenant, storage);
        repo.guardar(c);
        assertThatThrownBy(() -> service.cdrXml(tenant, c.id()))
                .isInstanceOf(DomainException.class).hasMessageContaining("CDR");
    }
}
