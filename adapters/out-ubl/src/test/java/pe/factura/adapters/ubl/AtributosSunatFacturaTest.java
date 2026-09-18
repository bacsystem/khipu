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

    /** La plantilla pinta un TaxSubtotal por cada Totales.subtotales() (la regla de qué subtotales existen se prueba en el dominio). */
    @Test void subtotalesGlobalesSoloConBase() throws Exception {
        Document d = documento();
        assertThat(valor(d, "count(/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal)")).isEqualTo("3");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[1]/cac:TaxCategory/cac:TaxScheme/cbc:ID")).isEqualTo("1000");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[1]/cbc:TaxableAmount")).isEqualTo("100.00");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[1]/cbc:TaxAmount")).isEqualTo("18.00");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[2]/cbc:TaxAmount")).isEqualTo("0.00");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("18.00");
    }

    /** Al crédito: un PaymentTerms 'Credito' con el neto pendiente y uno por cuota (Cuota001…, monto, vencimiento); reglas 3244–3267, 3319. */
    @Test void formaPagoAlCreditoConCuotas() throws Exception {
        LocalDate emision = LocalDate.of(2026, 9, 13);
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", emision, "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", null),
                List.of(new Item("G", "Gravado", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.credito(new BigDecimal("100.00"), List.of(
                        new FormaPago.Cuota(new BigDecimal("60.00"), emision.plusDays(30)),
                        new FormaPago.Cuota(new BigDecimal("40.00"), emision.plusDays(60)))),
                FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(9, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        assertThat(valor(d, "count(/inv:Invoice/cac:PaymentTerms)")).isEqualTo("3");
        assertThat(valor(d, "count(/inv:Invoice/cac:PaymentTerms[cbc:ID='FormaPago'])")).isEqualTo("3");   // 3244: todos con el indicador
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[1]/cbc:PaymentMeansID")).isEqualTo("Credito");
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[1]/cbc:Amount")).isEqualTo("100.00");
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[1]/cbc:Amount/@currencyID")).isEqualTo("PEN");       // 2071
        assertThat(valor(d, "count(/inv:Invoice/cac:PaymentTerms[1]/cbc:PaymentDueDate)")).isEqualTo("0");
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[2]/cbc:PaymentMeansID")).isEqualTo("Cuota001");     // 3246
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[2]/cbc:Amount")).isEqualTo("60.00");
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[2]/cbc:PaymentDueDate")).isEqualTo("2026-10-13");   // 3255
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[3]/cbc:PaymentMeansID")).isEqualTo("Cuota002");
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[3]/cbc:Amount")).isEqualTo("40.00");
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[3]/cbc:PaymentDueDate")).isEqualTo("2026-11-12");
        assertThat(valor(d, "count(//cac:PaymentTerms[cbc:PaymentMeansID='Contado'])")).isEqualTo("0");        // 3247
        assertThat(valor(d, "count(/inv:Invoice/cac:PaymentTerms[2]/cbc:PaymentMeansID)")).isEqualTo("1");     // 3461: uno por PaymentTerms

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /** Descuento de línea (00) y global (02): AllowanceCharge en su sitio del XSD, factor/monto/base, y totales netos (reglas 38, 46/47, 54). */
    @Test void descuentosDeLineaYGlobalEnElXml() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", null),
                List.of(new Item("A", "Con descuento", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, Descuento.porcentaje(new BigDecimal("10"), true)),
                        new Item("B", "Sin descuento", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), Descuento.monto(new BigDecimal("20.00"), false), FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(10, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        String linea = "/inv:Invoice/cac:InvoiceLine[1]";
        assertThat(valor(d, linea + "/cbc:LineExtensionAmount")).isEqualTo("180.00");                       // 200 − 20 (regla 38)
        assertThat(valor(d, linea + "/cac:AllowanceCharge/cbc:ChargeIndicator")).isEqualTo("false");
        assertThat(valor(d, linea + "/cac:AllowanceCharge/cbc:AllowanceChargeReasonCode")).isEqualTo("00");
        assertThat(valor(d, linea + "/cac:AllowanceCharge/cbc:AllowanceChargeReasonCode/@listName")).isEqualTo("Cargo/descuento");   // 4252
        assertThat(valor(d, linea + "/cac:AllowanceCharge/cbc:AllowanceChargeReasonCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo53");
        assertThat(valor(d, linea + "/cac:AllowanceCharge/cbc:MultiplierFactorNumeric")).isEqualTo("0.10000");
        assertThat(valor(d, linea + "/cac:AllowanceCharge/cbc:Amount")).isEqualTo("20.00");
        assertThat(valor(d, linea + "/cac:AllowanceCharge/cbc:BaseAmount")).isEqualTo("200.00");
        assertThat(valor(d, linea + "/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("32.40");
        assertThat(valor(d, linea + "/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceAmount")).isEqualTo("106.2000000000");   // regla 33
        assertThat(valor(d, linea + "/cac:Price/cbc:PriceAmount")).isEqualTo("100.0000000000");
        assertThat(valor(d, "count(/inv:Invoice/cac:InvoiceLine[2]/cac:AllowanceCharge)")).isEqualTo("0");

        String global = "/inv:Invoice/cac:AllowanceCharge";
        assertThat(valor(d, "count(" + global + ")")).isEqualTo("1");
        assertThat(valor(d, global + "/cbc:AllowanceChargeReasonCode")).isEqualTo("03");
        assertThat(valor(d, global + "/cbc:Amount")).isEqualTo("20.00");
        assertThat(valor(d, global + "/cbc:BaseAmount")).isEqualTo("280.00");                                 // 180 + 100
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("50.40");                   // 32.40 + 18.00
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:LineExtensionAmount")).isEqualTo("280.00");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount")).isEqualTo("330.40");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:AllowanceTotalAmount")).isEqualTo("20.00");   // regla 51
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("310.40");        // regla 53

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /** Línea gratuita: PriceTypeCode 02 con el valor referencial, Price 0, subtotal 9996 fuera de los totales y leyenda 1002. */
    @Test void operacionGratuitaEnElXml() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", null),
                List.of(new Item("A", "Vendido", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("B", "Bonificación", "NIU", new BigDecimal("5"), new BigDecimal("10.00"), TipoAfectacionIgv.GRAVADO_BONIFICACION)),
                FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(11, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        assertThat(valor(d, "/inv:Invoice/cbc:Note[@languageLocaleID='1002']")).isEqualTo("TRANSFERENCIA GRATUITA DE UN BIEN Y/O SERVICIO PRESTADO GRATUITAMENTE");
        String linea = "/inv:Invoice/cac:InvoiceLine[2]";
        assertThat(valor(d, linea + "/cbc:LineExtensionAmount")).isEqualTo("50.00");
        assertThat(valor(d, linea + "/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceTypeCode")).isEqualTo("02");     // 3234
        assertThat(valor(d, linea + "/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceAmount")).isEqualTo("10.0000000000");
        assertThat(valor(d, linea + "/cac:Price/cbc:PriceAmount")).isEqualTo("0.0000000000");                                      // 2640
        assertThat(valor(d, linea + "/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxableAmount")).isEqualTo("50.00");
        assertThat(valor(d, linea + "/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxAmount")).isEqualTo("9.00");                              // 3111
        assertThat(valor(d, linea + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cbc:ID")).isEqualTo("Z");
        assertThat(valor(d, linea + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cbc:TaxExemptionReasonCode")).isEqualTo("15");
        assertThat(valor(d, linea + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID")).isEqualTo("9996");
        assertThat(valor(d, linea + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:Name")).isEqualTo("GRA");
        assertThat(valor(d, linea + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:TaxTypeCode")).isEqualTo("FRE");
        assertThat(valor(d, "/inv:Invoice/cac:InvoiceLine[1]/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceTypeCode")).isEqualTo("01");

        String gra = "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='9996']";
        assertThat(valor(d, gra + "/cbc:TaxableAmount")).isEqualTo("50.00");                                                      // 3276
        assertThat(valor(d, gra + "/cbc:TaxAmount")).isEqualTo("9.00");                                                          // 3302
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("18.00");                                       // sin el de gratuitas
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:LineExtensionAmount")).isEqualTo("100.00");                  // regla 54
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("118.00");

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /** Detracción: PaymentMeans con la cuenta BN, PaymentTerms 'Detraccion' con catálogo 54, % y monto en PEN, y leyenda 2006. */
    @Test void detraccionEnElXml() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "USD", "1001",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", null),
                List.of(new Item("S", "Servicio empresarial", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, new Detraccion("022", new BigDecimal("12"), new BigDecimal("531.00"), "00-000-123456", "001"),
                FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(12, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        assertThat(valor(d, "/inv:Invoice/cbc:InvoiceTypeCode/@listID")).isEqualTo("1001");
        assertThat(valor(d, "/inv:Invoice/cbc:Note[@languageLocaleID='2006']")).contains("SPOT");
        String pm = "/inv:Invoice/cac:PaymentMeans[cbc:ID='Detraccion']";
        assertThat(valor(d, "count(" + pm + ")")).isEqualTo("1");                                                            // 3034
        assertThat(valor(d, pm + "/cbc:PaymentMeansCode")).isEqualTo("001");
        assertThat(valor(d, pm + "/cbc:PaymentMeansCode/@listName")).isEqualTo("Medio de pago");                               // 4252
        assertThat(valor(d, pm + "/cbc:PaymentMeansCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo59");
        assertThat(valor(d, pm + "/cac:PayeeFinancialAccount/cbc:ID")).isEqualTo("00-000-123456");
        String pt = "/inv:Invoice/cac:PaymentTerms[cbc:ID='Detraccion']";
        assertThat(valor(d, "count(" + pt + ")")).isEqualTo("1");                                                            // 3127
        assertThat(valor(d, pt + "/cbc:PaymentMeansID")).isEqualTo("022");
        assertThat(valor(d, pt + "/cbc:PaymentMeansID/@schemeName")).isEqualTo("Codigo de detraccion");                        // 4255
        assertThat(valor(d, pt + "/cbc:PaymentMeansID/@schemeURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo54");
        assertThat(valor(d, pt + "/cbc:PaymentPercent")).isEqualTo("12");
        assertThat(valor(d, pt + "/cbc:Amount")).isEqualTo("531.00");
        assertThat(valor(d, pt + "/cbc:Amount/@currencyID")).isEqualTo("PEN");                                                 // 3208 aunque la factura sea en USD
        // El PaymentMeans va antes de los PaymentTerms y la forma de pago sigue presente.
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[cbc:ID='FormaPago']/cbc:PaymentMeansID")).isEqualTo("Contado");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("1180.00");

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /** Retención (62, ChargeIndicator false) y percepción (51, true + PaymentTerms 'Percepcion' + leyenda 2000) como AllowanceCharge globales. */
    @Test void retencionYPercepcionEnElXml() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "2001",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", null),
                List.of(new Item("S", "Servicio", "ZZ", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, null, new RetencionIgv(null, null), new Percepcion("51", null, null, null), FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(13, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        String ret = "/inv:Invoice/cac:AllowanceCharge[cbc:AllowanceChargeReasonCode='62']";
        assertThat(valor(d, ret + "/cbc:ChargeIndicator")).isEqualTo("false");                    // 3114
        assertThat(valor(d, ret + "/cbc:MultiplierFactorNumeric")).isEqualTo("0.03000");
        assertThat(valor(d, ret + "/cbc:Amount")).isEqualTo("35.40");                             // 3263
        assertThat(valor(d, ret + "/cbc:BaseAmount")).isEqualTo("1180.00");                       // 3264
        String per = "/inv:Invoice/cac:AllowanceCharge[cbc:AllowanceChargeReasonCode='51']";
        assertThat(valor(d, per + "/cbc:ChargeIndicator")).isEqualTo("true");                     // 3114
        assertThat(valor(d, per + "/cbc:MultiplierFactorNumeric")).isEqualTo("0.02000");
        assertThat(valor(d, per + "/cbc:Amount")).isEqualTo("23.60");                             // 2798
        assertThat(valor(d, per + "/cbc:Amount/@currencyID")).isEqualTo("PEN");                   // 2792
        assertThat(valor(d, per + "/cbc:BaseAmount")).isEqualTo("1180.00");                       // 3233
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms[cbc:ID='Percepcion']/cbc:Amount")).isEqualTo("1203.60");   // 3309/3310
        assertThat(valor(d, "/inv:Invoice/cbc:Note[@languageLocaleID='2000']")).isEqualTo("COMPROBANTE DE PERCEPCIÓN");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("1180.00");
        assertThat(valor(d, "count(/inv:Invoice/cac:AllowanceCharge)")).isEqualTo("2");

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /** Regla 3290: con una base grande y descuento fijo el factor de 5 decimales no reproduce el monto, así que no se emite. */
    @Test void descuentoSinFactorCuandoNoReproduceElMonto() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE S.A.C.", null),
                List.of(new Item("A", "Maquinaria", "NIU", BigDecimal.ONE, new BigDecimal("2006000.00"), TipoAfectacionIgv.GRAVADO, Descuento.monto(new BigDecimal("1000.00"), true))),
                FormaPago.contado(), Descuento.monto(new BigDecimal("1000.00"), false), FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(11, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        assertThat(valor(d, "count(/inv:Invoice/cac:InvoiceLine[1]/cac:AllowanceCharge/cbc:MultiplierFactorNumeric)")).isEqualTo("0");
        assertThat(valor(d, "/inv:Invoice/cac:InvoiceLine[1]/cac:AllowanceCharge/cbc:Amount")).isEqualTo("1000.00");
        assertThat(valor(d, "count(/inv:Invoice/cac:AllowanceCharge/cbc:MultiplierFactorNumeric)")).isEqualTo("0");
        assertThat(valor(d, "/inv:Invoice/cac:AllowanceCharge/cbc:Amount")).isEqualTo("1000.00");
        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    @Test void sigueValidandoContraElXsdOficial() {
        Tenant t = FreemarkerUblGeneratorTest.tenant();
        String xml = new FreemarkerUblGenerator().generar(facturaConTresAfectaciones(), t)
                .replace("<ext:ExtensionContent/>", "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>");
        new JaxpXsdValidator().validar(xml, TipoDocumento.FACTURA);   // no lanza
        assertThat(xml).contains(CAT06);
    }
}
