package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Tenant;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato con la hoja "Factura2_0" de las reglas de validación de SUNAT (docs/sunat/ref/reglas_validacion_2026-08-26.xlsx).
 * Salvo InvoiceTypeCode/@listID (tipo de operación, obligatorio: ERROR 3205), los atributos son opcionales, pero si van
 * deben llevar exactamente estos valores o SUNAT observa el comprobante (4251 @listAgencyName, 4252 @listName,
 * 4253 @listURI, 4254 @listID, 4255 @schemeName, 4256 @schemeAgencyName, 4257 @schemeURI, 4258 @unitCodeListID,
 * 4259 @unitCodeListAgencyName). Los de UN/ECE 5305/5153 no se validan; siguen la guía UBL 2.1 y son iguales en
 * subtotales globales y líneas. Cambiar un literal aquí sin cambiar la hoja es una regresión, no un ajuste.
 */
class AtributosSunatFacturaTest {
    private static final String CAT06 = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06";
    private static final String UNECE = "United Nations Economic Commission for Europe";

    static Comprobante facturaConTresAfectaciones() {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", null),
                List.of(new Item("G", "Gravado", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("E", "Exonerado", "NIU", BigDecimal.ONE, new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO),
                        new Item("I", "Inafecto", "NIU", BigDecimal.ONE, new BigDecimal("30.00"), TipoAfectacionIgv.INAFECTO)),
                FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(7, "20100066603");
        return c;
    }

    static XPath xpath() {
        XPath xp = XPathFactory.newInstance().newXPath();
        xp.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String p) {
                return switch (p) {
                    case "cbc" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
                    case "cac" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
                    case "inv" -> "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
                    default -> XMLConstants.NULL_NS_URI;
                };
            }
            public String getPrefix(String uri) { return null; }
            public Iterator<String> getPrefixes(String uri) { return null; }
        });
        return xp;
    }

    static Document documento() throws Exception {
        String xml = new FreemarkerUblGenerator().generar(facturaConTresAfectaciones(), FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    static String valor(Document d, String expr) throws Exception { return xpath().evaluate(expr, d); }

    /** Cada fila: XPath del atributo → valor exacto que exige la hoja Factura2_0 (o la guía UBL 2.1 cuando SUNAT no lo valida). */
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = '|', textBlock = """
        /inv:Invoice/cbc:InvoiceTypeCode/@listID                          | 0101
        /inv:Invoice/cbc:InvoiceTypeCode/@listAgencyName                  | PE:SUNAT
        /inv:Invoice/cbc:InvoiceTypeCode/@listName                        | Tipo de Documento
        /inv:Invoice/cbc:InvoiceTypeCode/@listURI                         | urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01
        /inv:Invoice/cbc:DocumentCurrencyCode/@listID                     | ISO 4217 Alpha
        /inv:Invoice/cbc:DocumentCurrencyCode/@listName                   | Currency
        /inv:Invoice/cbc:DocumentCurrencyCode/@listAgencyName             | United Nations Economic Commission for Europe
        /inv:Invoice/cbc:Note/@languageLocaleID                           | 1000
        /inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeID         | 6
        /inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeName       | Documento de Identidad
        /inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeAgencyName | PE:SUNAT
        /inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeURI        | urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06
        /inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyLegalEntity/cac:RegistrationAddress/cbc:AddressTypeCode | 0000
        /inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeID         | 6
        /inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeName       | Documento de Identidad
        /inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeAgencyName | PE:SUNAT
        /inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeURI        | urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06
        /inv:Invoice/cac:PaymentTerms/cbc:ID                              | FormaPago
        /inv:Invoice/cac:PaymentTerms/cbc:PaymentMeansID                  | Contado
        /inv:Invoice/cac:InvoiceLine[1]/cbc:InvoicedQuantity/@unitCode                  | NIU
        /inv:Invoice/cac:InvoiceLine[1]/cbc:InvoicedQuantity/@unitCodeListID            | UN/ECE rec 20
        /inv:Invoice/cac:InvoiceLine[1]/cbc:InvoicedQuantity/@unitCodeListAgencyName    | United Nations Economic Commission for Europe
        /inv:Invoice/cac:InvoiceLine[1]/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceTypeCode                 | 01
        /inv:Invoice/cac:InvoiceLine[1]/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceTypeCode/@listName       | Tipo de Precio
        /inv:Invoice/cac:InvoiceLine[1]/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceTypeCode/@listAgencyName | PE:SUNAT
        /inv:Invoice/cac:InvoiceLine[1]/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceTypeCode/@listURI        | urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cbc:TaxExemptionReasonCode                 | 10
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cbc:TaxExemptionReasonCode/@listName       | Afectacion del IGV
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cbc:TaxExemptionReasonCode/@listAgencyName | PE:SUNAT
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cbc:TaxExemptionReasonCode/@listURI        | urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID/@schemeID            | UN/ECE 5153
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID/@schemeName          | Tax Scheme Identifier
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID/@schemeAgencyName    | United Nations Economic Commission for Europe
        """)
    void atributoConElValorQueExigeSunat(String expr, String esperado) throws Exception {
        assertThat(valor(documento(), expr.trim())).isEqualTo(esperado.trim());
    }

    /** Catálogo 05: categoría del tributo (UN/ECE 5305) por afectación, en cada línea y en los subtotales globales. */
    @ParameterizedTest(name = "línea {0}: afectación {1} → tributo {2}, categoría {3}")
    @CsvSource({"1,10,1000,S", "2,20,9997,E", "3,30,9998,O"})
    void categoriaDelTributoPorAfectacion(int linea, String afectacion, String tributo, String categoria) throws Exception {
        Document d = documento();
        String cat = "/inv:Invoice/cac:InvoiceLine[" + linea + "]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory";
        assertThat(valor(d, cat + "/cbc:TaxExemptionReasonCode")).isEqualTo(afectacion);
        assertThat(valor(d, cat + "/cbc:ID")).isEqualTo(categoria);
        assertThat(valor(d, cat + "/cbc:ID/@schemeID")).isEqualTo("UN/ECE 5305");
        assertThat(valor(d, cat + "/cbc:ID/@schemeName")).isEqualTo("Tax Category Identifier");
        assertThat(valor(d, cat + "/cbc:ID/@schemeAgencyName")).isEqualTo(UNECE);
        assertThat(valor(d, cat + "/cac:TaxScheme/cbc:ID")).isEqualTo(tributo);

        String global = "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='" + tributo + "']/cac:TaxCategory";
        assertThat(valor(d, global + "/cbc:ID")).isEqualTo(categoria);
        assertThat(valor(d, global + "/cbc:ID/@schemeID")).isEqualTo("UN/ECE 5305");
        assertThat(valor(d, global + "/cac:TaxScheme/cbc:ID/@schemeID")).isEqualTo("UN/ECE 5153");
        assertThat(valor(d, global + "/cac:TaxScheme/cbc:ID/@schemeName")).isEqualTo("Tax Scheme Identifier");
        assertThat(valor(d, global + "/cac:TaxScheme/cbc:ID/@schemeAgencyName")).isEqualTo(UNECE);
    }

    /** Solo aparecen los subtotales con base > 0, en el orden del catálogo, y el impuesto global es la suma de las líneas gravadas. */
    @Test void subtotalesGlobalesSoloConBase() throws Exception {
        Document d = documento();
        assertThat(valor(d, "count(/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal)")).isEqualTo("3");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[1]/cac:TaxCategory/cac:TaxScheme/cbc:ID")).isEqualTo("1000");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[1]/cbc:TaxableAmount")).isEqualTo("100.00");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[1]/cbc:TaxAmount")).isEqualTo("18.00");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[2]/cbc:TaxAmount")).isEqualTo("0.00");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("18.00");

        Comprobante soloGravado = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", null),
                List.of(new Item("G", "Gravado", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FreemarkerUblGeneratorTest.CLOCK);
        soloGravado.asignarNumero(8, "20100066603");
        assertThat(soloGravado.totales().subtotales()).extracting(t -> t.afectacion()).containsExactly(TipoAfectacionIgv.GRAVADO);
    }

    @Test void sigueValidandoContraElXsdOficial() {
        Tenant t = FreemarkerUblGeneratorTest.tenant();
        String xml = new FreemarkerUblGenerator().generar(facturaConTresAfectaciones(), t)
                .replace("<ext:ExtensionContent/>", "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>");
        new JaxpXsdValidator().validar(xml, TipoDocumento.FACTURA);   // no lanza
        assertThat(xml).contains(CAT06);
    }
}
