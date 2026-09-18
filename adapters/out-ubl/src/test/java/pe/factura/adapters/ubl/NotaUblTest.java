package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import pe.factura.domain.documento.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.adapters.ubl.AtributosSunatFacturaTest.valor;

/**
 * Notas de crédito (CreditNote) y débito (DebitNote): raíz, DiscrepancyResponse (catálogo 09/10, reglas 2128–2136),
 * BillingReference (2524, 2117), sin PaymentTerms "Contado" (3246), elementos de línea y total propios de cada tipo, y XSD.
 */
class NotaUblTest {
    static final Nota NOTA = new Nota(TipoDocumento.FACTURA, "F001", 123, "01", "Anulación de la operación por error en el pedido");

    static Comprobante nota(TipoDocumento tipo, Nota nota, FormaPago formaPago) {
        Comprobante c = Comprobante.crearNota(UUID.randomUUID(), tipo, "FC01", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", "AV. LIMA 1"),
                List.of(new Item("A", "Laptop", "NIU", new BigDecimal("2"), new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("B", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)),
                formaPago, null, List.of(), nota, FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(7, "20100066603");
        return c;
    }

    static Document parsear(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @Test void notaDeCredito() throws Exception {
        String xml = new FreemarkerUblGenerator().generar(nota(TipoDocumento.NOTA_CREDITO, NOTA, null), FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);
        assertThat(valor(d, "/cn:CreditNote/cbc:ID")).isEqualTo("FC01-7");
        assertThat(valor(d, "count(/cn:CreditNote/cbc:InvoiceTypeCode | /cn:CreditNote/cbc:DueDate)")).isEqualTo("0");
        assertThat(valor(d, "/cn:CreditNote/cbc:Note[@languageLocaleID='1000']")).startsWith("DOS MIL CUATROCIENTOS DIEZ CON 00/100");
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:ReferenceID")).isEqualTo("F001-123");
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:ResponseCode")).isEqualTo("01");
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:ResponseCode/@listName")).isEqualTo("Tipo de nota de credito");   // 4252
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:ResponseCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo09");
        assertThat(valor(d, "/cn:CreditNote/cac:DiscrepancyResponse/cbc:Description")).isEqualTo(NOTA.descripcion());
        assertThat(valor(d, "/cn:CreditNote/cac:BillingReference/cac:InvoiceDocumentReference/cbc:ID")).isEqualTo("F001-123");
        assertThat(valor(d, "/cn:CreditNote/cac:BillingReference/cac:InvoiceDocumentReference/cbc:DocumentTypeCode")).isEqualTo("01");
        assertThat(valor(d, "/cn:CreditNote/cac:BillingReference/cac:InvoiceDocumentReference/cbc:DocumentTypeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");
        // Sin forma de pago: una nota no lleva "Contado" (3246) y la firma/emisor/receptor son los mismos bloques que en la factura.
        assertThat(valor(d, "count(/cn:CreditNote/cac:PaymentTerms)")).isEqualTo("0");
        assertThat(valor(d, "/cn:CreditNote/cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo("20100066603");
        assertThat(valor(d, "/cn:CreditNote/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo("20601234567");
        assertThat(valor(d, "/cn:CreditNote/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("360.00");
        assertThat(valor(d, "/cn:CreditNote/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("2410.00");
        assertThat(valor(d, "count(/cn:CreditNote/cac:CreditNoteLine)")).isEqualTo("2");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[1]/cbc:CreditedQuantity")).isEqualTo("2");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[1]/cbc:CreditedQuantity/@unitCode")).isEqualTo("NIU");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[1]/cbc:LineExtensionAmount")).isEqualTo("2000.00");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID")).isEqualTo("1000");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine[2]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID")).isEqualTo("9997");
        new JaxpXsdValidator().validar(firmaFalsa(xml), TipoDocumento.NOTA_CREDITO);
    }

    @Test void notaDeCreditoConCuotasCorregidas_motivo13() throws Exception {
        Nota nota13 = new Nota(TipoDocumento.FACTURA, "F001", 123, "13", "Se reprograman las cuotas");
        FormaPago fp = FormaPago.credito(new BigDecimal("2410.00"), List.of(new FormaPago.Cuota(new BigDecimal("2410.00"), LocalDate.of(2026, 12, 1))));
        String xml = new FreemarkerUblGenerator().generar(nota(TipoDocumento.NOTA_CREDITO, nota13, fp), FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);
        assertThat(valor(d, "count(/cn:CreditNote/cac:PaymentTerms)")).isEqualTo("2");                                     // Credito + Cuota001 (3257)
        assertThat(valor(d, "/cn:CreditNote/cac:PaymentTerms[1]/cbc:PaymentMeansID")).isEqualTo("Credito");
        assertThat(valor(d, "/cn:CreditNote/cac:PaymentTerms[2]/cbc:PaymentMeansID")).isEqualTo("Cuota001");
        assertThat(valor(d, "/cn:CreditNote/cac:PaymentTerms[2]/cbc:PaymentDueDate")).isEqualTo("2026-12-01");
        assertThat(valor(d, "/cn:CreditNote/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("0.00");                 // 3315
        assertThat(valor(d, "count(/cn:CreditNote/cac:CreditNoteLine)")).isEqualTo("1");
        assertThat(valor(d, "/cn:CreditNote/cac:CreditNoteLine/cac:Item/cbc:Description")).isEqualTo("Se reprograman las cuotas");
        assertThat(valor(d, "/cn:CreditNote/cbc:Note[@languageLocaleID='1000']")).startsWith("CERO CON 00/100");
        new JaxpXsdValidator().validar(firmaFalsa(xml), TipoDocumento.NOTA_CREDITO);
    }

    @Test void notaDeDebito() throws Exception {
        Nota interes = new Nota(TipoDocumento.FACTURA, "F001", 123, "01", "Intereses por mora de 30 días");
        String xml = new FreemarkerUblGenerator().generar(nota(TipoDocumento.NOTA_DEBITO, interes, null), FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);
        assertThat(valor(d, "/dn:DebitNote/cbc:ID")).isEqualTo("FC01-7");
        assertThat(valor(d, "/dn:DebitNote/cac:DiscrepancyResponse/cbc:ResponseCode/@listName")).isEqualTo("Tipo de nota de debito");
        assertThat(valor(d, "/dn:DebitNote/cac:DiscrepancyResponse/cbc:ResponseCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo10");
        assertThat(valor(d, "/dn:DebitNote/cac:RequestedMonetaryTotal/cbc:PayableAmount")).isEqualTo("2410.00");     // ND: RequestedMonetaryTotal (UBL 2.1)
        assertThat(valor(d, "count(/dn:DebitNote/cac:LegalMonetaryTotal)")).isEqualTo("0");
        assertThat(valor(d, "/dn:DebitNote/cac:DebitNoteLine[1]/cbc:DebitedQuantity")).isEqualTo("2");
        assertThat(valor(d, "/dn:DebitNote/cac:DebitNoteLine[1]/cac:Price/cbc:PriceAmount")).isEqualTo("1000.0000000000");
        new JaxpXsdValidator().validar(firmaFalsa(xml), TipoDocumento.NOTA_DEBITO);
    }

    private static String firmaFalsa(String xml) {
        return xml.replace("<ext:ExtensionContent/>", "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>");
    }
}
