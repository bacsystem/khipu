package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Baja de una boleta (#20): SummaryDocuments 2.0/1.1 (hoja "Resumen Diario1_1") con ID RC-yyyymmdd-N, ReferenceDate = emisión de la boleta y una línea en
 * estado 3 con el comprador, los valores de venta, el total y el IGV de la boleta. Valida contra el XSD oficial.
 */
class SummaryDocumentsUblTest {
    static final Clock HOY = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneId.of("America/Lima"));

    static Comprobante boleta(Receptor comprador, List<Item> items) {
        Comprobante c = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 13), "PEN", "0101", comprador, items).crear(FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(45, "20100066603"); c.firmar("H", "k"); c.marcarEnviado(); c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr");
        return c;
    }

    static XPath xpath() {
        XPath xp = XPathFactory.newInstance().newXPath();
        xp.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String p) {
                return switch (p) {
                    case "rc" -> "urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1";
                    case "sac" -> "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
                    case "cbc" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
                    case "cac" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
                    default -> XMLConstants.NULL_NS_URI;
                };
            }
            public String getPrefix(String u) { return null; }
            public Iterator<String> getPrefixes(String u) { return null; }
        });
        return xp;
    }

    static Document parsear(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    static String conFirma(String xml) {
        return xml.replace("<ext:ExtensionContent/>", "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>");
    }

    @Test void laBajaDeUnaBoletaEsUnaLineaEnEstadoTresDelResumenDiario() throws Exception {
        Comprobante c = boleta(new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(
                new Item("A", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                new Item("B", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)));
        ComunicacionBaja b = ComunicacionBaja.crear(c, 3, "Error en el monto", HOY);

        String xml = new FreemarkerUblGenerator().generarBaja(b, c, FreemarkerUblGeneratorTest.tenant());

        Document d = parsear(xml);
        XPath xp = xpath();
        assertThat(xp.evaluate("/rc:SummaryDocuments/cbc:UBLVersionID", d)).isEqualTo("2.0");                       // 2074
        assertThat(xp.evaluate("/rc:SummaryDocuments/cbc:CustomizationID", d)).isEqualTo("1.1");                    // 2072
        assertThat(xp.evaluate("/rc:SummaryDocuments/cbc:ID", d)).isEqualTo("RC-20260918-3");                       // 2220
        assertThat(xp.evaluate("/rc:SummaryDocuments/cbc:ReferenceDate", d)).isEqualTo("2026-09-13");               // emisión de la boleta
        assertThat(xp.evaluate("/rc:SummaryDocuments/cbc:IssueDate", d)).isEqualTo("2026-09-18");                   // 2346
        assertThat(xp.evaluate("/rc:SummaryDocuments/cac:AccountingSupplierParty/cbc:CustomerAssignedAccountID", d)).isEqualTo("20100066603");   // 1034
        assertThat(xp.evaluate("/rc:SummaryDocuments/cac:AccountingSupplierParty/cbc:AdditionalAccountID", d)).isEqualTo("6");                   // 2218
        String l = "/rc:SummaryDocuments/sac:SummaryDocumentsLine";
        assertThat(xp.evaluate("count(" + l + ")", d)).isEqualTo("1");
        assertThat(xp.evaluate(l + "/cbc:LineID", d)).isEqualTo("1");
        assertThat(xp.evaluate(l + "/cbc:DocumentTypeCode", d)).isEqualTo("03");
        assertThat(xp.evaluate(l + "/cbc:ID", d)).isEqualTo("B001-45");                                              // 2513
        assertThat(xp.evaluate(l + "/cac:AccountingCustomerParty/cbc:CustomerAssignedAccountID", d)).isEqualTo("12345678");
        assertThat(xp.evaluate(l + "/cac:AccountingCustomerParty/cbc:AdditionalAccountID", d)).isEqualTo("1");
        assertThat(xp.evaluate(l + "/cac:Status/cbc:ConditionCode", d)).isEqualTo("3");                             // catálogo 19: anulado
        assertThat(xp.evaluate(l + "/sac:TotalAmount", d)).isEqualTo("168.00");
        assertThat(xp.evaluate(l + "/sac:TotalAmount/@currencyID", d)).isEqualTo("PEN");
        assertThat(xp.evaluate(l + "/sac:BillingPayment[cbc:InstructionID='01']/cbc:PaidAmount", d)).isEqualTo("100.00");
        assertThat(xp.evaluate(l + "/sac:BillingPayment[cbc:InstructionID='02']/cbc:PaidAmount", d)).isEqualTo("50.00");
        // 2254: un valor de venta en cero no se informa.
        assertThat(xp.evaluate("count(" + l + "/sac:BillingPayment[cbc:InstructionID='03'])", d)).isEqualTo("0");
        assertThat(xp.evaluate(l + "/cac:TaxTotal[cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']/cbc:TaxAmount", d)).isEqualTo("18.00");   // 2278
        assertThat(xp.evaluate(l + "/cac:TaxTotal/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']/cac:TaxCategory/cbc:Percent", d)).isEqualTo("18");   // 2992/3504
        new JaxpXsdValidator().validarBaja(conFirma(xml), b);
    }

    @Test void unCompradorSinDocumentoVaConGuion() throws Exception {
        Comprobante c = boleta(new Receptor("-", "-", "CLIENTES VARIOS", null), List.of(new Item("A", "Pan", "NIU", BigDecimal.TEN, new BigDecimal("0.50"), TipoAfectacionIgv.GRAVADO)));
        ComunicacionBaja b = ComunicacionBaja.crear(c, 1, "Error", HOY);
        String xml = new FreemarkerUblGenerator().generarBaja(b, c, FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);
        String l = "/rc:SummaryDocuments/sac:SummaryDocumentsLine";
        assertThat(xpath().evaluate(l + "/cac:AccountingCustomerParty/cbc:CustomerAssignedAccountID", d)).isEqualTo("-");
        assertThat(xpath().evaluate(l + "/cac:AccountingCustomerParty/cbc:AdditionalAccountID", d)).isEqualTo("-");   // 2016: «listado y guión»
        new JaxpXsdValidator().validarBaja(conFirma(xml), b);
    }

    /** 274-H1: la boleta que pasó el envío individual va en el mismo resumen, con su línea en estado 1 (catálogo 19: adicionar). */
    @Test void unaBoletaInformadaPorPrimeraVezVaEnEstadoUno() throws Exception {
        Comprobante c = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("1", "12345678", "JUAN PEREZ", null),
                List.of(new Item("A", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(46, "20100066603"); c.firmar("H", "k");
        ComunicacionBaja alta = ComunicacionBaja.altaEnResumen(c, 4, HOY);

        String xml = new FreemarkerUblGenerator().generarBaja(alta, c, FreemarkerUblGeneratorTest.tenant());

        assertThat(xpath().evaluate("/rc:SummaryDocuments/sac:SummaryDocumentsLine/cac:Status/cbc:ConditionCode", parsear(xml))).isEqualTo("1");
        new JaxpXsdValidator().validarBaja(conFirma(xml), alta);
    }

    /**
     * 275-H3: una boleta toda exonerada va sin valor de venta gravado (2254 lo prohíbe en cero) pero con el nodo del IGV en 0.00, que 2278 exige. SUNAT lo
     * acepta en la práctica; que lo acepte en beta se confirma en la homologación (#32).
     */
    @Test void unaBoletaExoneradaLlevaElIgvEnCeroYNingunValorGravado() throws Exception {
        Comprobante c = boleta(new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("L", "Libro", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)));
        ComunicacionBaja b = ComunicacionBaja.crear(c, 1, "Error", HOY);
        String xml = new FreemarkerUblGenerator().generarBaja(b, c, FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);
        String l = "/rc:SummaryDocuments/sac:SummaryDocumentsLine";
        assertThat(xpath().evaluate("count(" + l + "/sac:BillingPayment[cbc:InstructionID='01'])", d)).isEqualTo("0");
        assertThat(xpath().evaluate(l + "/sac:BillingPayment[cbc:InstructionID='02']/cbc:PaidAmount", d)).isEqualTo("50.00");
        assertThat(xpath().evaluate(l + "/cac:TaxTotal[cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']/cbc:TaxAmount", d)).isEqualTo("0.00");
        new JaxpXsdValidator().validarBaja(conFirma(xml), b);
    }

    /** El esquema se elige por la baja: un RC no pasa como RA ni al revés. */
    @Test void cadaBajaSeValidaContraSuEsquema() {
        Comprobante c = boleta(new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("A", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO)));
        String rc = conFirma(new FreemarkerUblGenerator().generarBaja(ComunicacionBaja.crear(c, 1, "Error", HOY), c, FreemarkerUblGeneratorTest.tenant()));
        ComunicacionBaja deUnaFactura = ComunicacionBaja.crear(VoidedDocumentsUblTest.factura(), 1, "Error", HOY);
        assertThatThrownBy(() -> new JaxpXsdValidator().validarBaja(rc, deUnaFactura)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("XSD_INVALIDO");
    }
}
