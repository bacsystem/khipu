package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FreemarkerUblGeneratorTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static Tenant tenant() {
        return new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA DE PRUEBA S.A.C.", Entorno.BETA, null,
                new CertificadoDigital(new byte[0], "", LocalDate.of(2030, 1, 1)));
    }
    static Comprobante factura() {
        Comprobante c = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE & CIA S.A.C.", "AV. LIMA 123"), List.of(new Item("P001", "Laptop <15\">", "NIU", BigDecimal.ONE, new BigDecimal("2360.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P002", "Libro", "NIU", new BigDecimal("2"), new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO))).crear(CLOCK);
        c.asignarNumero(1, "20100066603");
        return c;
    }

    @Test void generaInvoiceConDatosClave() {
        String xml = new FreemarkerUblGenerator().generar(factura(), tenant());
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"");
        assertThat(xml).contains("<cbc:UBLVersionID>2.1</cbc:UBLVersionID>")
                .contains("<cbc:CustomizationID>2.0</cbc:CustomizationID>")
                .contains("<cbc:ID>F001-1</cbc:ID>")
                .contains("<cbc:IssueDate>2026-09-13</cbc:IssueDate>")
                .contains("<cbc:InvoiceTypeCode listID=\"0101\"")
                .contains("<cbc:Note languageLocaleID=\"1000\">DOS MIL CUATROCIENTOS SESENTA CON 00/100 SOLES</cbc:Note>")
                .contains("schemeURI=\"urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06\">20100066603</cbc:ID>")
                .contains("schemeURI=\"urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06\">20601234565</cbc:ID>")
                .contains("CLIENTE &amp; CIA S.A.C.")
                .contains("Laptop &lt;15&quot;&gt;")
                .contains("<cbc:TaxableAmount currencyID=\"PEN\">2000.00</cbc:TaxableAmount>")
                .contains("<cbc:TaxAmount currencyID=\"PEN\">360.00</cbc:TaxAmount>")
                .contains("<cbc:TaxableAmount currencyID=\"PEN\">100.00</cbc:TaxableAmount>")
                .contains("schemeURI=\"urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo05\">9997</cbc:ID>")
                .contains("<cbc:PayableAmount currencyID=\"PEN\">2460.00</cbc:PayableAmount>")
                .contains("<ext:ExtensionContent/>")
                .contains("<cac:InvoiceLine>")
                .contains("<cbc:ID>1</cbc:ID>")
                .contains("<cbc:ID>2</cbc:ID>")
                .doesNotContain("<cbc:ID>1.00</cbc:ID>")
                .doesNotContain("<cbc:ID>2.00</cbc:ID>");
        assertThat(xml.split("<cac:InvoiceLine>")).hasSize(3);
    }

    @Test void esValidoContraXsd() {
        String xml = new FreemarkerUblGenerator().generar(factura(), tenant());
        // El ExtensionContent va vacío hasta que XmlDsigSigner (adapters:out-signing) coloca el
        // ds:Signature real dentro; el XSD oficial exige contenido no vacío en ese elemento
        // (xsd:any minOccurs="1"), así que en el pipeline real la validación XSD corre sobre el
        // XML YA FIRMADO, no sobre este XML sin firmar. Aquí simulamos ese relleno con un
        // placeholder de una única etiqueta en un namespace ajeno al esquema (out-ubl no puede
        // depender de out-signing) solo para poder ejercitar el validador en este módulo.
        String xmlConSlotRelleno = xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>");
        new JaxpXsdValidator().validar(xmlConSlotRelleno, TipoDocumento.FACTURA);   // no lanza
    }

    /** #20: la boleta es un Invoice con InvoiceTypeCode 03, el comprador sin documento va como «-» y no lleva forma de pago (Boleta2_0 no la tiene). */
    @Test void generaUnaBoletaValidaContraElXsd() {
        Comprobante b = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("-", "-", "CLIENTES VARIOS", null),
                List.of(new Item("P001", "Pan", "NIU", new BigDecimal("10"), new BigDecimal("0.50"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        b.asignarNumero(3, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(b, tenant());
        assertThat(xml).contains("<cbc:ID>B001-3</cbc:ID>")
                .contains("listURI=\"urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01\">03</cbc:InvoiceTypeCode>")
                .contains("<cbc:ID schemeID=\"-\"")
                .contains("<cbc:RegistrationName>CLIENTES VARIOS</cbc:RegistrationName>")
                .doesNotContain("FormaPago");
        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.BOLETA);
        // La factura sigue informando la forma de pago (3244).
        assertThat(new FreemarkerUblGenerator().generar(factura(), tenant())).contains("<cbc:PaymentMeansID>Contado</cbc:PaymentMeansID>");
    }
}
