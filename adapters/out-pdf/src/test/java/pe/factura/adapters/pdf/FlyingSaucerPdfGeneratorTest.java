package pe.factura.adapters.pdf;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.PersonalizacionPdf;
import pe.factura.domain.tenant.PlantillaPdf;
import pe.factura.domain.tenant.Tenant;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlyingSaucerPdfGeneratorTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Tenant TENANT = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA DE PRUEBA S.A.C.", Entorno.BETA, null, null,
            new Domicilio("150101", "Av. Javier Prado Este 123", "San Borja Norte", null, null, null, null), null, "Andina Store");
    static final Receptor RECEPTOR = new Receptor("6", "20601234565", "CLIENTE S.A.C.", "AV. LIMA 1");
    final FlyingSaucerPdfGenerator generador = new FlyingSaucerPdfGenerator();

    static Comprobante factura() {
        Comprobante c = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "1001", RECEPTOR, List.of(new Item("A", "Laptop Lenovo ThinkPad", "NIU", new BigDecimal("2"), new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("B", "Libro técnico", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO))).fechaVencimiento(LocalDate.of(2026, 10, 13)).formaPago(FormaPago.credito(new BigDecimal("2410.00"), List.of(new FormaPago.Cuota(new BigDecimal("2410.00"), LocalDate.of(2026, 10, 13))))).detraccion(new Detraccion("022", new BigDecimal("12"), new BigDecimal("289.00"), "00-000-123456", "001")).referencias(Referencias.ninguna()).crear(CLOCK);
        c.asignarNumero(125, "20100066603");
        c.firmar("y4M8+jW8Xp278K1aM02q19KjvO3k=", "k");
        return c;
    }

    static Comprobante notaCredito() {
        Comprobante c = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "FC01", LocalDate.of(2026, 9, 13), new Nota(TipoDocumento.FACTURA, "F001", 125, "07", "Devolución de una laptop"), RECEPTOR, List.of(new Item("A", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        c.asignarNumero(7, "20100066603");
        c.firmar("hashnota==", "k");
        return c;
    }

    @Test void laFacturaLlevaEmisorReceptorLineasTotalesCuotasDetraccionYHash() {
        String html = generador.xhtml(factura(), TENANT, "qr", null);
        assertThat(html).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("FACTURA ELECTRÓNICA", "R.U.C. 20100066603", "F001-125", "EMPRESA DE PRUEBA S.A.C.", "Andina Store", "Av. Javier Prado Este 123, San Borja Norte")
                .contains("CLIENTE S.A.C.", "20601234565", "13/09/2026", "13/10/2026", "Laptop Lenovo ThinkPad", "Libro técnico")
                .contains("Op. gravadas", "PEN 2,000.00", "Op. exoneradas", "PEN 50.00", "IGV (18%)", "PEN 360.00", "Importe total", "PEN 2,410.00")
                .contains("DOS MIL CUATROCIENTOS DIEZ CON 00/100 SOLES", "Crédito (pendiente PEN 2,410.00)", "Cuota 1", "detracción", "00-000-123456")
                .contains("y4M8+jW8Xp278K1aM02q19KjvO3k=", "Representación impresa de la FACTURA ELECTRÓNICA")
                .doesNotContain("Documento que modifica");
    }

    /**
     * Las leyendas del catálogo 52 que declara el emisor tienen que salir impresas: en una factura de la Amazonía o de la zona
     * comercial de Tacna, el texto del catálogo es la frase que sustenta la exoneración. Faltaba justo en esos comprobantes.
     * Las automáticas no se repiten acá porque ya tienen su lugar en el cuerpo (monto en letras, detracción, IVAP, gratuitas).
     */
    @Test void lasLeyendasDeclaradasPorElEmisorSeImprimen() {
        Comprobante c = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", RECEPTOR,
                        List.of(new Item("A", "Madera", "NIU", BigDecimal.ONE, new BigDecimal("100.00"), TipoAfectacionIgv.EXONERADO)))
                .referencias(Referencias.ninguna()).leyendas(List.of("2001", "2008")).crear(CLOCK);
        c.asignarNumero(300, "20100066603");
        c.firmar("hashamazonia==", "k");

        String html = generador.xhtml(c, TENANT, "qr", null);

        assertThat(html)
                .contains("BIENES TRANSFERIDOS EN LA AMAZONÍA REGIÓN SELVA PARA SER CONSUMIDOS EN LA MISMA")
                .contains("VENTA EXONERADA DEL IGV-ISC-IPM. PROHIBIDA LA VENTA FUERA DE LA ZONA COMERCIAL DE TACNA")
                // El texto va sin el prefijo ni las comillas con que el catálogo lo describe.
                .doesNotContain("Leyenda “BIENES", "Leyenda: “VENTA");
    }

    /** Sin leyendas declaradas no aparece el bloque: una factura común no gana ruido. */
    @Test void sinLeyendasDeclaradasNoHayBloque() {
        assertThat(generador.xhtml(factura(), TENANT, "qr", null)).doesNotContain("class=\"leyenda leyenda-declarada\"");
    }

    /** Exportación (#65) e IVAP (#67): la fila de totales cambia (sin IGV / IVAP 4 %). */
    @Test void laExportacionYElIvapCambianLasFilasDeTotales() {
        Comprobante exp = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "USD", "0200", new Receptor("0", "US123456789", "ACME IMPORTS LLC", "1200 Main St", "US"),
                List.of(new Item("CAF", "Café verde en grano", "KGM", new BigDecimal("1000"), new BigDecimal("4.50"), TipoAfectacionIgv.EXPORTACION))).exportacion(new Exportacion("FOB", null)).crear(CLOCK);
        exp.asignarNumero(126, "20100066603");
        exp.firmar("h", "k");
        assertThat(generador.xhtml(exp, TENANT, "qr", null))
                .contains("Exportación (sin IGV)", "USD 4,500.00", "Importe total")
                .doesNotContain("IGV (18%)", "Op. gravadas");
        Comprobante ivap = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", RECEPTOR,
                List.of(new Item("ARZ", "Arroz pilado", "KGM", new BigDecimal("100"), new BigDecimal("3.12"), TipoAfectacionIgv.IVAP))).crear(CLOCK);
        ivap.asignarNumero(127, "20100066603");
        ivap.firmar("h", "k");
        assertThat(generador.xhtml(ivap, TENANT, "qr", null))
                .contains("Op. sujetas al IVAP", "PEN 300.00", "IVAP (4%)", "PEN 12.00", "PEN 312.00")
                .doesNotContain("IGV (18%)");
    }

    @Test void laNotaDeCreditoIndicaElComprobanteQueModificaYElMotivo() {
        String html = generador.xhtml(notaCredito(), TENANT, "qr", null);
        assertThat(html).contains("NOTA DE CRÉDITO ELECTRÓNICA", "FC01-7", "Documento que modifica", "Factura F001-125", "07 - Devolución por ítem", "Devolución de una laptop", "hashnota==");
    }

    @Test void generaUnPdfConElQrLegible() throws Exception {
        String contenido = "20100066603|01|F001|125|360.00|2410.00|2026-09-13|6|20601234565|y4M8+jW8Xp278K1aM02q19KjvO3k=|";
        byte[] pdf = generador.generar(factura(), TENANT, contenido, null);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        // Flying Saucer no falla si no resuelve el data: URI de la imagen: comprobamos que el XObject del QR (220 px) quedó dentro del PDF.
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("/Subtype/Image").contains("/Width " + FlyingSaucerPdfGenerator.QR_PX);

        var imagen = ImageIO.read(new ByteArrayInputStream(FlyingSaucerPdfGenerator.qrPng(contenido)));
        var leido = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen))));
        assertThat(leido.getText()).isEqualTo(contenido);
    }

    @Test void cadaTipoTieneSuPlantilla() {
        assertThat(generador.xhtml(notaDebito(), TENANT, "qr", null)).contains("NOTA DE DÉBITO ELECTRÓNICA", "01 - Intereses por mora");
    }

    static Comprobante notaDebito() {
        Comprobante c = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_DEBITO, "FD01", LocalDate.of(2026, 9, 13), new Nota(TipoDocumento.FACTURA, "F001", 125, "01", "Intereses por mora de 30 días"), RECEPTOR, List.of(new Item("I", "Intereses", "ZZ", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        c.asignarNumero(3, "20100066603");
        c.firmar("hashnd==", "k");
        return c;
    }

    static final byte[] PNG_1PX = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Test void laClasicaNoImprimeObservacionesNiPieSiNoHay() {
        String html = generador.xhtml(factura(), TENANT, "qr", null);
        assertThat(html).contains("<body class=\"clasico\">").doesNotContain("Observaciones").doesNotContain("class=\"logo\"");
    }

    @Test void cadaPlantillaCambiaLaClaseYElColorEntraDondeCorresponde() {
        for (PlantillaPdf pl : PlantillaPdf.values()) {
            Tenant t = TENANT.conPersonalizacionPdf(new PersonalizacionPdf(pl, "#C8552B", null, null, null));
            String html = generador.xhtml(factura(), t, "qr", null);
            assertThat(html).contains("<body class=\"" + pl.nombre() + "\">");
            assertThat(html).contains(".corporativo .recuadro { border-color: #C8552B; background: #C8552B;");
        }
        // La gris no usa el color: sus reglas son fijas.
        assertThat(generador.xhtml(factura(), TENANT.conPersonalizacionPdf(new PersonalizacionPdf(PlantillaPdf.GRIS, "#C8552B", null, null, null)), "qr", null))
                .contains(".gris .recuadro { border-color: #3a3a3a;");
    }

    @Test void logoPieYObservacionesDeLaEmpresaVanAlPdfYLasDelComprobanteMandan() {
        Tenant t = TENANT.conPersonalizacionPdf(new PersonalizacionPdf(PlantillaPdf.MODERNO, "#1F5F4A", "k/logo.png", "Gracias por su preferencia", "Obs por defecto"));
        String html = generador.xhtml(factura(), t, "qr", PNG_1PX);
        assertThat(html).contains("<img class=\"logo\" src=\"data:image/png;base64,iVBOR")
                .contains("<p class=\"leyenda\">Gracias por su preferencia</p>").doesNotContain("a través de khipu")
                .contains("<h2>Observaciones</h2>").contains("<p>Obs por defecto</p>");

        Comprobante conObs = factura();
        conObs.anotar("Entrega en almacén.\nHorario 9-18.");
        assertThat(generador.xhtml(conObs, t, "qr", PNG_1PX)).contains("<p>Entrega en almacén.\nHorario 9-18.</p>").doesNotContain("Obs por defecto");

        // El QR (220 px) y el logo (1 px) quedan incrustados: Flying Saucer omite el logo si no lleva tamaño explícito, ver tamañoLogo.
        byte[] pdf = generador.generar(conObs, t, "20100066603|01|F001|125|360.00|2410.00|2026-09-13|6|20601234565|hash|", PNG_1PX);
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("/Width " + FlyingSaucerPdfGenerator.QR_PX).contains("/Width 1/");
    }

    @Test void elLogoSeEscalaAlRecuadroConservandoLaProporcion() {
        assertThat(FlyingSaucerPdfGenerator.tamañoLogo(PNG_1PX)).isEqualTo("width: 18.0mm; height: 18.0mm;");
        assertThat(generador.xhtml(factura(), TENANT.conPersonalizacionPdf(PersonalizacionPdf.porDefecto().conLogo("k")), "qr", PNG_1PX)).contains("style=\"width: 18.0mm; height: 18.0mm;\"");
    }
}
