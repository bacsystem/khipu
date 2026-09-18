package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultarComprobanteServiceTest {
    private final Fakes.Comprobantes repo = new Fakes.Comprobantes();
    private final Fakes.Storage storage = new Fakes.Storage();
    private final Fakes.Tenants tenants = new Fakes.Tenants();
    private final List<String> qrs = new ArrayList<>();
    private final ConsultarComprobanteService service = new ConsultarComprobanteService(repo, tenants, storage, (c, t, qr) -> { qrs.add(qr); return ("%PDF " + qr).getBytes(); });
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

    @Test void elPdfSeGeneraUnaVezConElQrYSeGuardaJuntoAlXml() {
        tenants.guardar(Fakes.tenantListo(tenant));
        Comprobante c = Fakes.facturaFirmada(tenant, storage);
        repo.guardar(c);

        byte[] primero = service.pdf(tenant, c.id());
        byte[] segundo = service.pdf(tenant, c.id());

        assertThat(new String(primero)).startsWith("%PDF 20100066603|01|F001|" + c.numero() + "|").endsWith("|" + c.hash() + "|");
        assertThat(segundo).isEqualTo(primero);
        assertThat(qrs).hasSize(1);
        assertThat(storage.datos).containsKey(c.xmlKey().replace(".xml", ".pdf"));
    }

    @Test void sinFirmaNoHayPdf() {
        Comprobante c = Comprobante.crearFactura(tenant, "F001", java.time.LocalDate.of(2026, 9, 13), "PEN", "0101",
                new pe.factura.domain.documento.Receptor("6", "20601234567", "CLIENTE SAC", null),
                List.of(new pe.factura.domain.documento.Item("P1", "Prod", "NIU", java.math.BigDecimal.ONE, new java.math.BigDecimal("118.00"), pe.factura.domain.documento.TipoAfectacionIgv.GRAVADO)),
                java.time.Clock.fixed(java.time.Instant.parse("2026-09-13T15:00:00Z"), java.time.ZoneId.of("America/Lima")));
        repo.guardar(c);
        assertThatThrownBy(() -> service.pdf(tenant, c.id())).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SIN_FIRMA");
    }
}
