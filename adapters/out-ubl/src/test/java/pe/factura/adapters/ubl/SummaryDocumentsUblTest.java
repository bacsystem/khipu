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

    // --- #289: gratuitas y percepción en la línea del resumen -----------------------------------------------------------------------------------------------

    static final Clock ENERO_2027 = Clock.fixed(Instant.parse("2027-01-02T15:00:00Z"), ZoneId.of("America/Lima"));
    static final Item VENDIDO = new Item("A", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO);
    static final Item BONIFICACION = new Item("B", "Bonificación", "NIU", new BigDecimal("5"), new BigDecimal("10.00"), TipoAfectacionIgv.GRAVADO_BONIFICACION);

    static String instruccion(Document d, String codigo) throws Exception {
        return xpath().evaluate("/rc:SummaryDocuments/sac:SummaryDocumentsLine/sac:BillingPayment[cbc:InstructionID='" + codigo + "']/cbc:PaidAmount", d);
    }

    /**
     * #387: las gratuitas no van en el resumen. e-beta rechaza el 05 (2256) y también el 06 (2256, «no se encuentra en el catálogo 11»), y acepta el mismo resumen sin ellas
     * (RC-20261017-1); un resumen con una línea con error se rechaza entero (2987), y con él la baja de la boleta. Ni el 05, ni el 06–08 del Control de Cambios del 2027, ni el
     * IGV de las gratuitas en 9996 «GRA»: ni antes ni después del 2027-01-01.
     */
    @Test void lasGratuitasNoSeInformanEnElResumenAntesNiDespuesDe2027() throws Exception {
        for (Clock reloj : List.of(HOY, ENERO_2027)) {
            Comprobante c = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.now(reloj).minusDays(2), "PEN", "0101", new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(VENDIDO, BONIFICACION,
                    new Item("C", "Muestra", "NIU", BigDecimal.ONE, new BigDecimal("20.00"), TipoAfectacionIgv.INAFECTO_RETIRO),
                    new Item("D", "Libro de regalo", "NIU", BigDecimal.ONE, new BigDecimal("30.00"), TipoAfectacionIgv.EXONERADO_GRATUITO))).crear(reloj);
            c.asignarNumero(47, "20100066603"); c.firmar("H", "k"); c.marcarEnviado(); c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr");
            ComunicacionBaja b = ComunicacionBaja.crear(c, 1, "Error en el monto", reloj);

            String xml = new FreemarkerUblGenerator().generarBaja(b, c, FreemarkerUblGeneratorTest.tenant());
            Document d = parsear(xml);

            String cuando = reloj == HOY ? "antes de 2027" : "desde 2027";
            for (String codigo : List.of("05", "06", "07", "08", "09"))
                assertThat(instruccion(d, codigo)).as("gratuitas con el código " + codigo + " " + cuando).isEmpty();
            assertThat(xpath().evaluate("count(//cac:TaxScheme[cbc:ID='9996'])", d)).as("IGV de gratuitas " + cuando).isEqualTo("0");
            assertThat(instruccion(d, "01")).as("lo vendido sí va").isEqualTo("100.00");
            assertThat(xpath().evaluate("/rc:SummaryDocuments/sac:SummaryDocumentsLine/sac:TotalAmount", d)).as("las gratuitas no se cobran").isEqualTo("118.00");
            new JaxpXsdValidator().validarBaja(conFirma(xml), b);
        }
    }

    /**
     * La percepción de una boleta 2001 va en el resumen solo en el alta (estado 1): en una baja o una modificación SUNAT rechaza todo el resumen (2986). Régimen
     * del catálogo 22 (no el 51–53 del catálogo 53 de la boleta), la tasa, el monto, el total cobrado (total + percepción, 2608) y la base, en soles (2685/2690).
     */
    @Test void laPercepcionVaSoloEnElAltaDeUnaBoleta2001() throws Exception {
        Comprobante c = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 12), "PEN", "2001", new Receptor("1", "12345678", "JUAN PEREZ", null),
                List.of(new Item("A", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO))).percepcion(new Percepcion("51", null, null, null)).crear(FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(49, "20100066603"); c.firmar("H", "k");
        ComunicacionBaja alta = ComunicacionBaja.altaEnResumen(c, 6, HOY);

        String xml = new FreemarkerUblGenerator().generarBaja(alta, c, FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);

        String p = "/rc:SummaryDocuments/sac:SummaryDocumentsLine/sac:SUNATPerceptionSummaryDocumentReference";
        assertThat(xpath().evaluate(p + "/sac:SUNATPerceptionSystemCode", d)).isEqualTo("01");
        assertThat(xpath().evaluate(p + "/sac:SUNATPerceptionPercent", d)).isEqualTo("2.00");
        assertThat(xpath().evaluate(p + "/cbc:TotalInvoiceAmount", d)).isEqualTo("23.60");
        assertThat(xpath().evaluate(p + "/cbc:TotalInvoiceAmount/@currencyID", d)).isEqualTo("PEN");
        assertThat(xpath().evaluate(p + "/sac:SUNATTotalCashed", d)).isEqualTo("1203.60");
        assertThat(xpath().evaluate(p + "/cbc:TaxableAmount", d)).isEqualTo("1180.00");
        new JaxpXsdValidator().validarBaja(conFirma(xml), alta);

        Comprobante aceptada = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 13), "PEN", "2001", new Receptor("1", "12345678", "JUAN PEREZ", null),
                List.of(new Item("A", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO))).percepcion(new Percepcion("51", null, null, null)).crear(FreemarkerUblGeneratorTest.CLOCK);
        aceptada.asignarNumero(50, "20100066603"); aceptada.firmar("H", "k"); aceptada.marcarEnviado(); aceptada.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr");
        Document baja = parsear(new FreemarkerUblGenerator().generarBaja(ComunicacionBaja.crear(aceptada, 7, "Error", HOY), aceptada, FreemarkerUblGeneratorTest.tenant()));
        assertThat(xpath().evaluate("count(" + p + ")", baja)).as("en la baja (estado 3) no va: 2986").isEqualTo("0");
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

    // --- #362 B13: nombre del comprador (campo 10.3 del RC, Resumen Diario 2.0 desde 2027-01-01) ----------------------------------------------------------------

    private static String nombreDelCompradorEnElResumen(Receptor comprador, boolean alta) throws Exception {
        List<Item> items = List.of(new Item("A", "Pan", "NIU", BigDecimal.TEN, new BigDecimal("0.50"), TipoAfectacionIgv.GRAVADO));
        Comprobante c = boleta(comprador, items);
        if (alta) {   // el alta es de una boleta que SUNAT todavía no tiene: firmada, sin CDR
            c = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 12), "PEN", "0101", comprador, items).crear(FreemarkerUblGeneratorTest.CLOCK);
            c.asignarNumero(46, "20100066603"); c.firmar("H", "k");
        }
        ComunicacionBaja b = alta ? ComunicacionBaja.altaEnResumen(c, 1, HOY) : ComunicacionBaja.crear(c, 1, "Error", HOY);
        String xml = new FreemarkerUblGenerator().generarBaja(b, c, FreemarkerUblGeneratorTest.tenant());
        new JaxpXsdValidator().validarBaja(conFirma(xml), b);
        return xpath().evaluate("/rc:SummaryDocuments/sac:SummaryDocumentsLine/cac:AccountingCustomerParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName", parsear(xml));
    }

    @Test void elResumenLlevaElNombreDelComprador() throws Exception {
        assertThat(nombreDelCompradorEnElResumen(new Receptor("1", "12345678", "JUAN PEREZ", null), false)).isEqualTo("JUAN PEREZ");
        assertThat(nombreDelCompradorEnElResumen(new Receptor("1", "12345678", "JUAN PEREZ", null), true)).isEqualTo("JUAN PEREZ");
    }

    @Test void elCompradorSinDocumentoVaConSuNombreConvenido() throws Exception {
        assertThat(nombreDelCompradorEnElResumen(new Receptor("-", "-", "CLIENTES VARIOS", null), false)).isEqualTo("CLIENTES VARIOS");
    }

    /** 2022: de 3 a 250 caracteres en el resumen (la boleta admite 1500) y sin espacios en los extremos. */
    @Test void elNombreDelCompradorSeAjustaALoQueAdmiteElResumen() throws Exception {
        assertThat(nombreDelCompradorEnElResumen(new Receptor("1", "12345678", "  JUAN PEREZ  ", null), false)).isEqualTo("JUAN PEREZ");
        assertThat(nombreDelCompradorEnElResumen(new Receptor("1", "12345678", "A".repeat(300), null), false)).hasSize(250);
    }

    /**
     * 274-H1: la boleta que pasó el envío individual va en el mismo resumen, con su línea en estado 1 (catálogo 19: adicionar). Del 12, el 18 es su 6.º día: el
     * envío individual llega hasta el 5.º (#290).
     */
    @Test void unaBoletaInformadaPorPrimeraVezVaEnEstadoUno() throws Exception {
        Comprobante c = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 12), "PEN", "0101", new Receptor("1", "12345678", "JUAN PEREZ", null),
                List.of(new Item("A", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(46, "20100066603"); c.firmar("H", "k");
        ComunicacionBaja alta = ComunicacionBaja.altaEnResumen(c, 4, HOY);

        String xml = new FreemarkerUblGenerator().generarBaja(alta, c, FreemarkerUblGeneratorTest.tenant());

        assertThat(xpath().evaluate("/rc:SummaryDocuments/sac:SummaryDocumentsLine/cac:Status/cbc:ConditionCode", parsear(xml))).isEqualTo("1");
        new JaxpXsdValidator().validarBaja(conFirma(xml), alta);
    }

    /**
     * #331: pasado el 7.º día la boleta se informa igual (del 10, el 18 es su 8.º día). Mismo XML que en plazo: el ReferenceDate es su emisión, y es ese «más de
     * 7 días antes de la recepción» lo que SUNAT observa con 4443 (RC fila 14), sin rechazar. Es un RC propio, con esta sola línea: no arrastra boletas en plazo.
     */
    @Test void unaBoletaFueraDePlazoVaSolaEnSuResumenConSuFechaDeEmision() throws Exception {
        Comprobante c = Comprobante.boleta(UUID.randomUUID(), "B001", LocalDate.of(2026, 9, 10), "PEN", "0101", new Receptor("1", "12345678", "JUAN PEREZ", null),
                List.of(new Item("A", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(51, "20100066603"); c.firmar("H", "k");
        ComunicacionBaja alta = ComunicacionBaja.altaEnResumen(c, 5, HOY);

        String xml = new FreemarkerUblGenerator().generarBaja(alta, c, FreemarkerUblGeneratorTest.tenant());

        Document d = parsear(xml);
        assertThat(alta.fueraDePlazo()).isTrue();
        assertThat(xpath().evaluate("/rc:SummaryDocuments/cbc:ID", d)).isEqualTo("RC-20260918-5");
        assertThat(xpath().evaluate("/rc:SummaryDocuments/cbc:ReferenceDate", d)).isEqualTo("2026-09-10");
        assertThat(xpath().evaluate("/rc:SummaryDocuments/cbc:IssueDate", d)).isEqualTo("2026-09-18");
        assertThat(xpath().evaluate("count(/rc:SummaryDocuments/sac:SummaryDocumentsLine)", d)).isEqualTo("1");
        assertThat(xpath().evaluate("/rc:SummaryDocuments/sac:SummaryDocumentsLine/cbc:ID", d)).isEqualTo("B001-51");
        assertThat(xpath().evaluate("/rc:SummaryDocuments/sac:SummaryDocumentsLine/cac:Status/cbc:ConditionCode", d)).isEqualTo("1");
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

    // --- #293: notas de crédito y débito sobre boletas ---------------------------------------------------------------------------------------------------------

    static Comprobante notaDeBoleta(TipoDocumento tipo, String serie, LocalDate emision) {
        Comprobante c = Comprobante.nota(UUID.randomUUID(), tipo, serie, emision, new Nota(TipoDocumento.BOLETA, "B001", 45, "01", "Anulación de la operación"),
                new Receptor("-", "-", "CLIENTES VARIOS", null), List.of(VENDIDO)).crear(HOY);
        c.asignarNumero(3, "20100066603"); c.firmar("H", "k");
        return c;
    }

    /**
     * Una nota de boleta informada en el resumen (estado 1) lleva su tipo (07/08) y el documento que modifica en cac:BillingReference (2524: serie-número,
     * 2583: tipo 03), entre el comprador y cac:Status como pide el XSD.
     */
    @Test void unaNotaDeBoletaInformadaLlevaLaBoletaQueModifica() throws Exception {
        for (TipoDocumento tipo : List.of(TipoDocumento.NOTA_CREDITO, TipoDocumento.NOTA_DEBITO)) {
            Comprobante nota = notaDeBoleta(tipo, tipo == TipoDocumento.NOTA_CREDITO ? "BC01" : "BD01", LocalDate.of(2026, 9, 12));
            ComunicacionBaja alta = ComunicacionBaja.altaEnResumen(nota, 4, HOY);

            String xml = new FreemarkerUblGenerator().generarBaja(alta, nota, FreemarkerUblGeneratorTest.tenant());
            Document d = parsear(xml);
            String l = "/rc:SummaryDocuments/sac:SummaryDocumentsLine";
            assertThat(xpath().evaluate(l + "/cbc:DocumentTypeCode", d)).isEqualTo(tipo.codigo());
            assertThat(xpath().evaluate(l + "/cbc:ID", d)).isEqualTo(nota.serie() + "-3");
            assertThat(xpath().evaluate(l + "/cac:BillingReference/cac:InvoiceDocumentReference/cbc:ID", d)).isEqualTo("B001-45");
            assertThat(xpath().evaluate(l + "/cac:BillingReference/cac:InvoiceDocumentReference/cbc:DocumentTypeCode", d)).isEqualTo("03");
            assertThat(xpath().evaluate(l + "/cac:Status/cbc:ConditionCode", d)).isEqualTo("1");
            new JaxpXsdValidator().validarBaja(conFirma(xml), alta);
        }
    }

    /** La baja (estado 3) de una nota de boleta no lleva cac:BillingReference: SUNAT no lo exige fuera del alta (2524/2583), y así no rebota por la boleta (2990). */
    @Test void laBajaDeUnaNotaDeBoletaNoRepiteLaReferencia() throws Exception {
        Comprobante nota = notaDeBoleta(TipoDocumento.NOTA_CREDITO, "BC01", LocalDate.of(2026, 9, 16));
        nota.marcarEnviado(); nota.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr");
        ComunicacionBaja baja = ComunicacionBaja.crear(nota, 2, "Error en el monto", HOY);

        String xml = new FreemarkerUblGenerator().generarBaja(baja, nota, FreemarkerUblGeneratorTest.tenant());
        Document d = parsear(xml);
        assertThat(xpath().evaluate("count(/rc:SummaryDocuments/sac:SummaryDocumentsLine/cac:BillingReference)", d)).isEqualTo("0");
        assertThat(xpath().evaluate("/rc:SummaryDocuments/sac:SummaryDocumentsLine/cac:Status/cbc:ConditionCode", d)).isEqualTo("3");
        new JaxpXsdValidator().validarBaja(conFirma(xml), baja);
    }

    /** El esquema se elige por la baja: un RC no pasa como RA ni al revés. */
    @Test void cadaBajaSeValidaContraSuEsquema() {
        Comprobante c = boleta(new Receptor("1", "12345678", "JUAN PEREZ", null), List.of(new Item("A", "Prod", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO)));
        String rc = conFirma(new FreemarkerUblGenerator().generarBaja(ComunicacionBaja.crear(c, 1, "Error", HOY), c, FreemarkerUblGeneratorTest.tenant()));
        ComunicacionBaja deUnaFactura = ComunicacionBaja.crear(VoidedDocumentsUblTest.factura(), 1, "Error", HOY);
        assertThatThrownBy(() -> new JaxpXsdValidator().validarBaja(rc, deUnaFactura)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("XSD_INVALIDO");
    }
}
