package pe.factura.adapters.pdf;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlyingSaucerPdfGeneratorTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Tenant TENANT = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA DE PRUEBA S.A.C.", Entorno.BETA, null, null,
            new Domicilio("150101", "Av. Javier Prado Este 123", "San Borja Norte", null, null, null, null), null, "Andina Store");
    static final Receptor RECEPTOR = new Receptor("6", "20601234567", "CLIENTE S.A.C.", "AV. LIMA 1");
    final FlyingSaucerPdfGenerator generador = new FlyingSaucerPdfGenerator();

    static Comprobante factura() {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), LocalDate.of(2026, 10, 13), "PEN", "1001", RECEPTOR,
                List.of(new Item("A", "Laptop Lenovo ThinkPad", "NIU", new BigDecimal("2"), new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("B", "Libro técnico", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)),
                FormaPago.credito(new BigDecimal("2410.00"), List.of(new FormaPago.Cuota(new BigDecimal("2410.00"), LocalDate.of(2026, 10, 13)))),
                null, List.of(), new Detraccion("022", new BigDecimal("12"), new BigDecimal("289.00"), "00-000-123456", "001"), null, null, List.of(), Referencias.ninguna(), null, CLOCK);
        c.asignarNumero(125, "20100066603");
        c.firmar("y4M8+jW8Xp278K1aM02q19KjvO3k=", "k");
        return c;
    }

    static Comprobante notaCredito() {
        Comprobante c = Comprobante.crearNota(UUID.randomUUID(), TipoDocumento.NOTA_CREDITO, "FC01", LocalDate.of(2026, 9, 13), "PEN", "0101", RECEPTOR,
                List.of(new Item("A", "Laptop", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), new Nota(TipoDocumento.FACTURA, "F001", 125, "07", "Devolución de una laptop"), CLOCK);
        c.asignarNumero(7, "20100066603");
        c.firmar("hashnota==", "k");
        return c;
    }

    @Test void laFacturaLlevaEmisorReceptorLineasTotalesCuotasDetraccionYHash() {
        String html = generador.xhtml(factura(), TENANT, "qr");
        assertThat(html).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("FACTURA ELECTRÓNICA", "R.U.C. 20100066603", "F001-125", "EMPRESA DE PRUEBA S.A.C.", "Andina Store", "Av. Javier Prado Este 123, San Borja Norte")
                .contains("CLIENTE S.A.C.", "20601234567", "13/09/2026", "13/10/2026", "Laptop Lenovo ThinkPad", "Libro técnico")
                .contains("Op. gravadas", "PEN 2,000.00", "Op. exoneradas", "PEN 50.00", "IGV (18%)", "PEN 360.00", "Importe total", "PEN 2,410.00")
                .contains("DOS MIL CUATROCIENTOS DIEZ CON 00/100 SOLES", "Crédito (pendiente PEN 2,410.00)", "Cuota 1", "detracción", "00-000-123456")
                .contains("y4M8+jW8Xp278K1aM02q19KjvO3k=", "Representación impresa de la FACTURA ELECTRÓNICA")
                .doesNotContain("Documento que modifica");
    }

    @Test void laNotaDeCreditoIndicaElComprobanteQueModificaYElMotivo() {
        String html = generador.xhtml(notaCredito(), TENANT, "qr");
        assertThat(html).contains("NOTA DE CRÉDITO ELECTRÓNICA", "FC01-7", "Documento que modifica", "Factura F001-125", "07 - Devolución por ítem", "Devolución de una laptop", "hashnota==");
    }

    @Test void generaUnPdfConElQrLegible() throws Exception {
        String contenido = "20100066603|01|F001|125|360.00|2410.00|2026-09-13|6|20601234567|y4M8+jW8Xp278K1aM02q19KjvO3k=|";
        byte[] pdf = generador.generar(factura(), TENANT, contenido);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(5_000);

        var imagen = ImageIO.read(new ByteArrayInputStream(FlyingSaucerPdfGenerator.qrPng(contenido)));
        var leido = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen))));
        assertThat(leido.getText()).isEqualTo(contenido);
    }

    @Test void cadaTipoTieneSuPlantilla() {
        assertThat(generador.xhtml(notaDebito(), TENANT, "qr")).contains("NOTA DE DÉBITO ELECTRÓNICA", "01 - Intereses por mora");
    }

    static Comprobante notaDebito() {
        Comprobante c = Comprobante.crearNota(UUID.randomUUID(), TipoDocumento.NOTA_DEBITO, "FD01", LocalDate.of(2026, 9, 13), "PEN", "0101", RECEPTOR,
                List.of(new Item("I", "Intereses", "ZZ", BigDecimal.ONE, new BigDecimal("59.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), new Nota(TipoDocumento.FACTURA, "F001", 125, "01", "Intereses por mora de 30 días"), CLOCK);
        c.asignarNumero(3, "20100066603");
        c.firmar("hashnd==", "k");
        return c;
    }
}
