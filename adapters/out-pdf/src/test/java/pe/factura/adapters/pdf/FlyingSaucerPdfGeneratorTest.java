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

    static Comprobante liquidacionCompra() {
        Comprobante c = Comprobante.liquidacionCompra(UUID.randomUUID(), "L001", LocalDate.of(2026, 9, 13), "PEN", null, new Receptor("1", "45678912", "JUAN QUISPE MAMANI", "Caserío Santa Rosa s/n"),
                        new LiquidacionCompra("060101", "02", new LiquidacionCompra.Ubicacion("Fundo Santa Rosa, km 12", "060801", "03"), null),
                        List.of(new Item("CAF-01", "Café pergamino", "KGM", new BigDecimal("10"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), CodigoProductoSunat.de("50201706"), null),
                                new Item("PAP-01", "Papa blanca", "KGM", new BigDecimal("3"), new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO, null, null, false, List.of(), CodigoProductoSunat.de("50405700"), null)))
                .referencias(new Referencias(null, List.of(new GuiaRelacionada("31", "V001-45")), List.of())).crear(CLOCK);
        c.asignarNumero(15, "20100066603");
        c.firmar("hashliquidacion==", "k");
        return c;
    }

    /** #327: la liquidación de compra imprime al vendedor (con su domicilio y su condición), el lugar de la operación, lo que el comprador retiene y el neto que paga. */
    @Test void laLiquidacionDeCompraLlevaVendedorLugarRetencionesYNeto() {
        String html = generador.xhtml(liquidacionCompra(), TENANT, "qr", null);
        assertThat(html).contains("LIQUIDACIÓN DE COMPRA ELECTRÓNICA", "R.U.C. 20100066603", "L001-15", "EMPRESA DE PRUEBA S.A.C.")
                .contains("Vendedor", "JUAN QUISPE MAMANI", "45678912", "Caserío Santa Rosa s/n (CAJAMARCA / CAJAMARCA / CAJAMARCA)", "Producción")
                .contains("Lugar de la operación", "Fundo Santa Rosa, km 12", "Extracción", "Guía V001-45")
                .contains("Op. gravadas", "PEN 1,000.00", "Op. exoneradas", "PEN 150.00", "IGV (18%)", "PEN 180.00", "Subtotal", "PEN 1,330.00")
                .contains("IGV retenido", "- PEN 180.00", "Retención Impuesto a la Renta (1.5%)", "- PEN 17.25", "Importe neto a pagar", "PEN 1,132.75")
                .contains("MIL CIENTO TREINTA Y DOS CON 75/100 SOLES", "Representación impresa de la LIQUIDACIÓN DE COMPRA ELECTRÓNICA", "hashliquidacion==")
                .doesNotContain("Señor(es)", "Forma de pago");
        byte[] pdf = generador.generar(liquidacionCompra(), TENANT, "20100066603|04|L001|15|180.00|1132.75|2026-09-13|1|45678912|hashliquidacion==|", null);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
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

    /**
     * #291: la frase de la leyenda 1002 va impresa cuando el XML la lleva, es decir, solo si toda la operación es gratuita. En una factura mixta cada línea gratuita
     * ya dice «Transferencia gratuita» y el total de operaciones gratuitas, pero no la leyenda.
     */
    @Test void laLeyenda1002SeImprimeSoloSiTodaLaOperacionEsGratuita() {
        Item bonificacion = new Item("B", "Bonificación", "NIU", BigDecimal.ONE, new BigDecimal("10.00"), TipoAfectacionIgv.GRAVADO_BONIFICACION);
        Comprobante gratuita = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", RECEPTOR, List.of(bonificacion))
                .referencias(Referencias.ninguna()).crear(CLOCK);
        gratuita.asignarNumero(301, "20100066603");
        gratuita.firmar("hashgratuita==", "k");
        Comprobante mixta = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", RECEPTOR,
                        List.of(new Item("A", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO), bonificacion))
                .referencias(Referencias.ninguna()).crear(CLOCK);
        mixta.asignarNumero(302, "20100066603");
        mixta.firmar("hashmixta==", "k");

        assertThat(generador.xhtml(gratuita, TENANT, "qr", null)).contains("TRANSFERENCIA GRATUITA DE UN BIEN Y/O SERVICIO PRESTADO GRATUITAMENTE");
        assertThat(generador.xhtml(mixta, TENANT, "qr", null)).contains("Op. gratuitas").doesNotContain("TRANSFERENCIA GRATUITA DE UN BIEN Y/O SERVICIO PRESTADO GRATUITAMENTE");
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

    /** #316: con tipo de cambio el PDF muestra el tipo y el equivalente en soles; sin él, o en soles, no hay nada de eso. */
    @Test void elTipoDeCambioMuestraElEquivalenteEnSoles() {
        Comprobante usd = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "USD", "0101", RECEPTOR,
                List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).tipoCambio(new BigDecimal("3.756")).crear(CLOCK);
        usd.asignarNumero(128, "20100066603");
        usd.firmar("h", "k");
        assertThat(generador.xhtml(usd, TENANT, "qr", null)).contains("Tipo de cambio (S/ por USD)", "3.756", "Equivalente en soles", "S/ 443.21");
        Comprobante sinTipo = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "USD", "0101", RECEPTOR,
                List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        sinTipo.asignarNumero(129, "20100066603");
        sinTipo.firmar("h", "k");
        assertThat(generador.xhtml(sinTipo, TENANT, "qr", null)).doesNotContain("Tipo de cambio", "Equivalente en soles");
    }

    @Test void laNotaDeCreditoIndicaElComprobanteQueModificaYElMotivo() {
        String html = generador.xhtml(notaCredito(), TENANT, "qr", null);
        assertThat(html).contains("NOTA DE CRÉDITO ELECTRÓNICA", "FC01-7", "Documento que modifica", "Factura F001-125", "07 - Devolución por ítem", "Devolución de una laptop", "hashnota==");
    }

    /** #293: la nota B nombra la boleta que modifica (catálogo 01: «Boleta de venta») y su comprador sin documento. */
    @Test void laNotaSobreUnaBoletaIndicaLaBoletaQueModifica() {
        Comprobante c = Comprobante.nota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "BC01", LocalDate.of(2026, 9, 13), new Nota(TipoDocumento.BOLETA, "B001", 45, "07", "Devolución de un libro"),
                new Receptor("-", "-", "CLIENTES VARIOS", null), List.of(new Item("L", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO))).crear(CLOCK);
        c.asignarNumero(2, "20100066603");
        c.firmar("hashnotab==", "k");
        String html = generador.xhtml(c, TENANT, "qr", null);
        assertThat(html).contains("NOTA DE CRÉDITO ELECTRÓNICA", "BC01-2", "Documento que modifica", "Boleta de venta B001-45", "CLIENTES VARIOS");
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

    /** #20: la boleta usa su plantilla y un comprador sin documento se imprime como tal, no como «- -». */
    @Test void laBoletaSinDocumentoDiceSinDocumento() {
        Comprobante b = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("-", "-", "CLIENTES VARIOS", null),
                List.of(new Item("P", "Pan francés", "NIU", new BigDecimal("10"), new BigDecimal("0.50"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        b.asignarNumero(9, "20100066603");
        b.firmar("hashboleta==", "k");
        String html = generador.xhtml(b, TENANT, "qr", null);
        assertThat(html).contains("BOLETA DE VENTA ELECTRÓNICA", "B001-9", "CLIENTES VARIOS", "Sin documento", "PEN 5.00")
                .doesNotContain("<td>-</td><td class=\"k\">Fecha de vencimiento");
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
