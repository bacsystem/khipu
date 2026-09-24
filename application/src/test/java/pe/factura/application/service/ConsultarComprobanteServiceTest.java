package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Cdr;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.EmisorImpreso;
import pe.factura.domain.tenant.PersonalizacionPdf;
import pe.factura.domain.tenant.Tenant;
import pe.factura.domain.tenant.PlantillaPdf;

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
    /** El emisor con el que se armó el PDF: es lo que este flujo tiene que acertar. */
    private final List<Tenant> emisoresImpresos = new ArrayList<>();
    /** Doble del lector del XML: el parseo real se prueba en `adapters/out-ubl`; acá importa de dónde salen los datos. */
    private java.util.Optional<EmisorImpreso> enElXml = java.util.Optional.empty();
    private final ConsultarComprobanteService service = new ConsultarComprobanteService(repo, tenants, storage,
            (c, t, qr, logo) -> { qrs.add(qr); emisoresImpresos.add(t); return ("%PDF " + qr + (logo == null ? "" : " logo=" + logo.length)).getBytes(); },
            new Fakes.Establecimientos(new Fakes.Series()), xml -> enElXml);
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
        assertThat(storage.datos).containsKey(c.xmlKey().replace(".xml", "-v" + ConsultarComprobanteService.VERSION_PDF + "-" + Fakes.tenantListo(tenant).personalizacionPdf().huella() + ".pdf"));
    }

    @Test void cambiarElDiseñoRegeneraElPdfConElLogoSinPisarElAnterior() {
        tenants.guardar(Fakes.tenantListo(tenant));
        Comprobante c = Fakes.facturaFirmada(tenant, storage);
        repo.guardar(c);
        byte[] clasico = service.pdf(tenant, c.id());

        storage.guardar(tenant + "/logo.png", new byte[]{1, 2, 3});
        tenants.guardar(Fakes.tenantListo(tenant).conPersonalizacionPdf(new PersonalizacionPdf(PlantillaPdf.CORPORATIVO, "#1F5F4A", tenant + "/logo.png", null, null)));
        byte[] corporativo = service.pdf(tenant, c.id());

        assertThat(new String(corporativo)).endsWith("logo=3");
        assertThat(corporativo).isNotEqualTo(clasico);
        assertThat(qrs).hasSize(2);
        assertThat(storage.datos.keySet().stream().filter(k -> k.endsWith(".pdf"))).hasSize(2);
    }

    @Test void sinFirmaNoHayPdf() {
        Comprobante c = Comprobante.factura(tenant, "F001", java.time.LocalDate.of(2026, 9, 13), "PEN", "0101", new pe.factura.domain.documento.Receptor("6", "20601234565", "CLIENTE SAC", null), List.of(new pe.factura.domain.documento.Item("P1", "Prod", "NIU", java.math.BigDecimal.ONE, new java.math.BigDecimal("118.00"), pe.factura.domain.documento.TipoAfectacionIgv.GRAVADO))).crear(java.time.Clock.fixed(java.time.Instant.parse("2026-09-13T15:00:00Z"), java.time.ZoneId.of("America/Lima")));
        repo.guardar(c);
        assertThatThrownBy(() -> service.pdf(tenant, c.id())).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("SIN_FIRMA");
    }

    /**
     * El bloqueante que esto corrige: el PDF se armaba con los datos fiscales de **hoy**. Si la empresa mudaba su
     * domicilio después de emitir, la representación impresa salía con la dirección nueva mientras el XML firmado y el
     * CDR de SUNAT llevaban la vieja, y cuál te tocaba dependía de si el PDF ya estaba en caché.
     */
    @Test void elPdfLlevaElEmisorDelXmlFirmado_aunqueLaEmpresaHayaCambiadoDespues() {
        tenants.guardar(Fakes.tenantListo(tenant).conDatosFiscales(Domicilio.de("040101", "Calle Mercaderes 100 (la nueva)"), null, "Nombre de hoy"));
        Comprobante c = Fakes.facturaFirmada(tenant, storage);
        repo.guardar(c);
        enElXml = java.util.Optional.of(new EmisorImpreso("20601234565", "RAZON AL EMITIR S.A.C.", "Comercial al emitir",
                Domicilio.de("150122", "Av. Larco 345 (la del XML)")));

        service.pdf(tenant, c.id());

        Tenant impreso = emisoresImpresos.get(0);
        assertThat(impreso.razonSocial()).isEqualTo("RAZON AL EMITIR S.A.C.");
        assertThat(impreso.nombreComercial()).isEqualTo("Comercial al emitir");
        assertThat(impreso.domicilio().direccion()).isEqualTo("Av. Larco 345 (la del XML)");
        assertThat(impreso.domicilio().distrito()).isEqualTo("MIRAFLORES");
        // El diseño sí es el de hoy a propósito: su huella entra en la clave de la caché.
        assertThat(impreso.personalizacionPdf()).isEqualTo(tenants.buscar(tenant).orElseThrow().personalizacionPdf());
        // El QR lleva el RUC del emisor, y el que vale es el que se firmó: es lo que SUNAT contrasta al verificarlo.
        assertThat(qrs.get(0)).startsWith("20601234565|");
        // La clave del PDF lleva la versión del diseño. Subirla es lo que regenera los que quedaron mal en caché.
        assertThat(storage.datos.keySet()).anySatisfy(k -> assertThat(k).contains("-v" + ConsultarComprobanteService.VERSION_PDF + "-").endsWith(".pdf"));
        assertThat(ConsultarComprobanteService.VERSION_PDF).isEqualTo(2);
    }

    /** Si el XML no se puede leer, imprimir con el emisor actual es peor que exacto, pero mejor que no imprimir. */
    @Test void siElXmlNoDiceQuienEmitioSeCaeAlEmisorActual() {
        tenants.guardar(Fakes.tenantListo(tenant).conDatosFiscales(Domicilio.de("040101", "Calle Mercaderes 100"), null, "Nombre de hoy"));
        Comprobante c = Fakes.facturaFirmada(tenant, storage);
        repo.guardar(c);
        enElXml = java.util.Optional.empty();

        service.pdf(tenant, c.id());

        assertThat(emisoresImpresos.get(0).razonSocial()).isEqualTo(tenants.buscar(tenant).orElseThrow().razonSocial());
        assertThat(emisoresImpresos.get(0).domicilio().direccion()).isEqualTo("Calle Mercaderes 100");
    }
}
