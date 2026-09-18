package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
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

/** Comunicación de baja: VoidedDocuments 2.0/1.0 con ID RA-yyyymmdd-N, ReferenceDate = emisión del comprobante y una línea (hoja "Comunicación de Baja1_0"). */
class VoidedDocumentsUblTest {
    static final Clock HOY = Clock.fixed(Instant.parse("2026-09-18T15:00:00Z"), ZoneId.of("America/Lima"));

    static ComunicacionBaja baja() {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", null),
                List.of(new Item("A", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)), FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(45, "20100066603"); c.firmar("H", "k"); c.marcarEnviado(); c.aplicarCdr(new Cdr("0", "aceptada", List.of()), "cdr");
        return ComunicacionBaja.crear(c, 3, "Error en el RUC del cliente", HOY);
    }

    @Test void voidedDocuments() throws Exception {
        String xml = new FreemarkerUblGenerator().generarBaja(baja(), FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        XPath xp = XPathFactory.newInstance().newXPath();
        xp.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String p) {
                return switch (p) {
                    case "ra" -> "urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1";
                    case "sac" -> "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
                    case "cbc" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
                    case "cac" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
                    default -> XMLConstants.NULL_NS_URI;
                };
            }
            public String getPrefix(String u) { return null; }
            public Iterator<String> getPrefixes(String u) { return null; }
        });
        assertThat(xp.evaluate("/ra:VoidedDocuments/cbc:UBLVersionID", d)).isEqualTo("2.0");                       // 2074
        assertThat(xp.evaluate("/ra:VoidedDocuments/cbc:CustomizationID", d)).isEqualTo("1.0");                    // 2072
        assertThat(xp.evaluate("/ra:VoidedDocuments/cbc:ID", d)).isEqualTo("RA-20260918-3");                        // 2220
        assertThat(xp.evaluate("/ra:VoidedDocuments/cbc:ReferenceDate", d)).isEqualTo("2026-09-13");               // 2375: emisión del comprobante
        assertThat(xp.evaluate("/ra:VoidedDocuments/cbc:IssueDate", d)).isEqualTo("2026-09-18");                   // 2346
        assertThat(xp.evaluate("/ra:VoidedDocuments/cac:AccountingSupplierParty/cbc:CustomerAssignedAccountID", d)).isEqualTo("20100066603");   // 1034
        assertThat(xp.evaluate("/ra:VoidedDocuments/cac:AccountingSupplierParty/cbc:AdditionalAccountID", d)).isEqualTo("6");                   // 2287
        assertThat(xp.evaluate("/ra:VoidedDocuments/cac:AccountingSupplierParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName", d)).isNotBlank();
        String linea = "/ra:VoidedDocuments/sac:VoidedDocumentsLine";
        assertThat(xp.evaluate("count(" + linea + ")", d)).isEqualTo("1");
        assertThat(xp.evaluate(linea + "/cbc:LineID", d)).isEqualTo("1");
        assertThat(xp.evaluate(linea + "/cbc:DocumentTypeCode", d)).isEqualTo("01");
        assertThat(xp.evaluate(linea + "/sac:DocumentSerialID", d)).isEqualTo("F001");
        assertThat(xp.evaluate(linea + "/sac:DocumentNumberID", d)).isEqualTo("45");
        assertThat(xp.evaluate(linea + "/sac:VoidReasonDescription", d)).isEqualTo("Error en el RUC del cliente");
        new JaxpXsdValidator().validarBaja(xml.replace("<ext:ExtensionContent/>", "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"));
    }
}
