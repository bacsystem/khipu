package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Domicilio;
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
 * 4259 @unitCodeListAgencyName). El ID de la categoría (UN/ECE 5305) no se valida y sigue la guía UBL 2.1; el ID del
 * tributo (TaxScheme) sí: lleva los del catálogo 05, y e-beta observa (4255/4256) los de UN/ECE 5153 de la guía genérica —
 * comprobado en la homologación (#32). Cambiar un literal aquí sin cambiar la hoja es una regresión, no un ajuste.
 */
class AtributosSunatFacturaTest {
    private static final String CAT06 = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06";
    private static final String UNECE = "United Nations Economic Commission for Europe";

    static Comprobante facturaConTresAfectaciones() {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
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
                    case "cn" -> "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
                    case "dn" -> "urn:oasis:names:specification:ubl:schema:xsd:DebitNote-2";
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
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID/@schemeName          | Codigo de tributos
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID/@schemeAgencyName    | PE:SUNAT
        /inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID/@schemeURI           | urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo05
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
        assertThat(valor(d, "count(" + global + "/cac:TaxScheme/cbc:ID/@schemeID)")).isEqualTo("0");
        assertThat(valor(d, global + "/cac:TaxScheme/cbc:ID/@schemeName")).isEqualTo("Codigo de tributos");                                   // 4255
        assertThat(valor(d, global + "/cac:TaxScheme/cbc:ID/@schemeAgencyName")).isEqualTo("PE:SUNAT");                                       // 4256
        assertThat(valor(d, global + "/cac:TaxScheme/cbc:ID/@schemeURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo05");   // 4257
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
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
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
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
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
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
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
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
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
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
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

    /** ISC (2000 con TierRange, base del IGV = valor + ISC) e ICBPER (7152 con BaseUnitMeasure y PerUnitAmount, sin base) por línea y globales. */
    @Test void iscEIcbperEnElXml() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
                List.of(new Item("C", "Cerveza", "NIU", BigDecimal.ONE, new BigDecimal("159.30"), TipoAfectacionIgv.GRAVADO, null, new Isc("01", new BigDecimal("35"), null), false),
                        new Item("B", "Bolsa", "NIU", new BigDecimal("3"), new BigDecimal("0.618"), TipoAfectacionIgv.GRAVADO, null, null, true)),
                FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(14, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        String l1 = "/inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal";
        assertThat(valor(d, l1 + "/cbc:TaxAmount")).isEqualTo("59.30");                                                            // 3292
        String isc = l1 + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='2000']";
        assertThat(valor(d, isc + "/cbc:TaxableAmount")).isEqualTo("100.00");
        assertThat(valor(d, isc + "/cbc:TaxAmount")).isEqualTo("35.00");                                                          // 3108
        assertThat(valor(d, isc + "/cac:TaxCategory/cbc:Percent")).isEqualTo("35.00");
        assertThat(valor(d, isc + "/cac:TaxCategory/cbc:TierRange")).isEqualTo("01");                                              // 2373
        assertThat(valor(d, isc + "/cac:TaxCategory/cac:TaxScheme/cbc:Name")).isEqualTo("ISC");
        assertThat(valor(d, isc + "/cac:TaxCategory/cac:TaxScheme/cbc:TaxTypeCode")).isEqualTo("EXC");
        String igv1 = l1 + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']";
        assertThat(valor(d, igv1 + "/cbc:TaxableAmount")).isEqualTo("135.00");                                                     // 204
        assertThat(valor(d, igv1 + "/cbc:TaxAmount")).isEqualTo("24.30");
        assertThat(valor(d, "count(" + l1 + "/cac:TaxSubtotal[cac:TaxCategory/cbc:TierRange])")).isEqualTo("1");                   // 3210: solo en ISC

        String l2 = "/inv:Invoice/cac:InvoiceLine[2]/cac:TaxTotal";
        String bolsa = l2 + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='7152']";
        assertThat(valor(d, "count(" + bolsa + "/cbc:TaxableAmount)")).isEqualTo("0");
        assertThat(valor(d, bolsa + "/cbc:TaxAmount")).isEqualTo("1.50");                                                          // 4318
        assertThat(valor(d, bolsa + "/cbc:BaseUnitMeasure")).isEqualTo("3");                                                       // 3236
        assertThat(valor(d, bolsa + "/cbc:BaseUnitMeasure/@unitCode")).isEqualTo("NIU");                                            // 4320
        assertThat(valor(d, bolsa + "/cac:TaxCategory/cbc:PerUnitAmount")).isEqualTo("0.50");                                       // 3238
        assertThat(valor(d, "count(" + bolsa + "/cac:TaxCategory/cbc:Percent)")).isEqualTo("0");                                   // 2992 exime 7152
        assertThat(valor(d, bolsa + "/cac:TaxCategory/cac:TaxScheme/cbc:Name")).isEqualTo("ICBPER");

        String g = "/inv:Invoice/cac:TaxTotal";
        assertThat(valor(d, g + "/cbc:TaxAmount")).isEqualTo("60.85");                                                             // 3294: 24.35 + 35 + 1.50
        assertThat(valor(d, g + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']/cbc:TaxableAmount")).isEqualTo("100.30");   // 3277: sin ISC
        assertThat(valor(d, g + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']/cbc:TaxAmount")).isEqualTo("24.35");       // 3291: con ISC
        assertThat(valor(d, g + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='2000']/cbc:TaxableAmount")).isEqualTo("100.00");
        assertThat(valor(d, "count(" + g + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='7152']/cbc:TaxableAmount)")).isEqualTo("0");
        assertThat(valor(d, g + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='7152']/cbc:TaxAmount")).isEqualTo("1.50");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:LineExtensionAmount")).isEqualTo("100.30");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount")).isEqualTo("161.15");                   // 55: 100.30 + 35 + 1.50 + 24.35
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("161.15");

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /**
     * Factura final con anticipo (reglas 65–66): documento referenciado con identificador de pago, PrepaidPayment con el importe
     * pagado (IGV incluido), descuento global 04 por el valor sin IGV que reduce la base del IGV (3277, 3291) y PrepaidAmount
     * restado del importe a pagar (3280); total valor/precio de venta siguen brutos (3278, 3279).
     */
    @Test void anticipoEnElXml() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
                List.of(new Item("OBRA", "Obra completa", "NIU", BigDecimal.ONE, new BigDecimal("1180.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, null, null, null,
                List.of(new Anticipo("F001", 10, new BigDecimal("300.00"), null, LocalDate.of(2026, 9, 1))), FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(12, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        String ref = "/inv:Invoice/cac:AdditionalDocumentReference";
        assertThat(valor(d, ref + "/cbc:ID")).isEqualTo("F001-10");                                                   // 2521
        assertThat(valor(d, ref + "/cbc:DocumentTypeCode")).isEqualTo("02");                                          // 2505
        assertThat(valor(d, ref + "/cbc:DocumentTypeCode/@listName")).isEqualTo("Documento Relacionado");            // 4252
        assertThat(valor(d, ref + "/cbc:DocumentTypeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo12");
        assertThat(valor(d, ref + "/cbc:DocumentStatusCode")).isEqualTo("1");                                         // 3216
        assertThat(valor(d, ref + "/cbc:DocumentStatusCode/@listName")).isEqualTo("Anticipo");
        assertThat(valor(d, ref + "/cac:IssuerParty/cac:PartyIdentification/cbc:ID")).isEqualTo("20100066603");     // 3217
        assertThat(valor(d, ref + "/cac:IssuerParty/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("6");       // 2520

        String pago = "/inv:Invoice/cac:PrepaidPayment";
        assertThat(valor(d, pago + "/cbc:ID")).isEqualTo("1");                                                        // 3211/3213
        assertThat(valor(d, pago + "/cbc:ID/@schemeName")).isEqualTo("Anticipo");                                     // 4255
        assertThat(valor(d, pago + "/cbc:ID/@schemeAgencyName")).isEqualTo("PE:SUNAT");                               // 4256
        assertThat(valor(d, pago + "/cbc:PaidAmount")).isEqualTo("354.00");                                           // 2503: 300 + IGV
        assertThat(valor(d, pago + "/cbc:PaidAmount/@currencyID")).isEqualTo("PEN");                                  // 2071
        assertThat(valor(d, pago + "/cbc:PaidDate")).isEqualTo("2026-09-01");

        String desc = "/inv:Invoice/cac:AllowanceCharge";
        assertThat(valor(d, "count(" + desc + ")")).isEqualTo("1");
        assertThat(valor(d, desc + "/cbc:ChargeIndicator")).isEqualTo("false");                                        // 3114
        assertThat(valor(d, desc + "/cbc:AllowanceChargeReasonCode")).isEqualTo("04");
        assertThat(valor(d, desc + "/cbc:Amount")).isEqualTo("300.00");
        assertThat(valor(d, desc + "/cbc:BaseAmount")).isEqualTo("1000.00");
        assertThat(valor(d, "count(" + desc + "/cbc:MultiplierFactorNumeric)")).isEqualTo("0");

        String igv = "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']";
        assertThat(valor(d, igv + "/cbc:TaxableAmount")).isEqualTo("700.00");                                          // 3277
        assertThat(valor(d, igv + "/cbc:TaxAmount")).isEqualTo("126.00");                                              // 3291
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("126.00");
        assertThat(valor(d, "/inv:Invoice/cac:InvoiceLine[1]/cbc:LineExtensionAmount")).isEqualTo("1000.00");        // la línea no cambia
        assertThat(valor(d, "/inv:Invoice/cac:InvoiceLine[1]/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("180.00");

        String tot = "/inv:Invoice/cac:LegalMonetaryTotal";
        assertThat(valor(d, tot + "/cbc:LineExtensionAmount")).isEqualTo("1000.00");                                   // 3278
        assertThat(valor(d, tot + "/cbc:TaxInclusiveAmount")).isEqualTo("1180.00");                                    // 3279
        assertThat(valor(d, tot + "/cbc:PrepaidAmount")).isEqualTo("354.00");                                          // 2509
        assertThat(valor(d, tot + "/cbc:PayableAmount")).isEqualTo("826.00");                                          // 3280
        assertThat(valor(d, "count(" + tot + "/cbc:AllowanceTotalAmount)")).isEqualTo("0");                            // 04 no entra en 3300

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /** Domicilio fiscal del emisor (reglas 4093–4098, 4041, 3030) y hora de emisión (IssueTime, sin validación). */
    @Test void domicilioDelEmisorYHoraDeEmisionEnElXml() throws Exception {
        Tenant t = FreemarkerUblGeneratorTest.tenant().conDatosFiscales(
                new Domicilio("150122", "Av. Larco 345 Of. 12", "Urb. Aurora", null, null, null, null), "00-000-123456");
        String xml = new FreemarkerUblGenerator().generar(facturaConTresAfectaciones(), t);
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        assertThat(valor(d, "/inv:Invoice/cbc:IssueTime")).isEqualTo("10:00:00");   // 15:00Z del reloj fijo en America/Lima
        String dir = "/inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyLegalEntity/cac:RegistrationAddress";
        assertThat(valor(d, dir + "/cbc:ID")).isEqualTo("150122");                          // 4093
        assertThat(valor(d, dir + "/cbc:AddressTypeCode")).isEqualTo("0000");               // 3030
        assertThat(valor(d, dir + "/cbc:CitySubdivisionName")).isEqualTo("Urb. Aurora");   // 4095
        assertThat(valor(d, dir + "/cbc:CityName")).isEqualTo("LIMA");                      // 4096 provincia
        assertThat(valor(d, dir + "/cbc:CountrySubentity")).isEqualTo("LIMA");              // 4097 departamento
        assertThat(valor(d, dir + "/cbc:District")).isEqualTo("MIRAFLORES");                // 4098
        assertThat(valor(d, dir + "/cac:AddressLine/cbc:Line")).isEqualTo("Av. Larco 345 Of. 12");   // 4094
        assertThat(valor(d, dir + "/cac:Country/cbc:IdentificationCode")).isEqualTo("PE"); // 4041

        // Serie asignada a un anexo (#80): el servicio pasa el emisor con el domicilio del establecimiento y el XML lleva su código.
        Tenant anexo = t.conDomicilio(new Domicilio("150131", "Av. Angamos 500", null, null, null, null, "0002"));
        Document da = f.newDocumentBuilder().parse(new InputSource(new StringReader(new FreemarkerUblGenerator().generar(facturaConTresAfectaciones(), anexo))));
        assertThat(valor(da, dir + "/cbc:AddressTypeCode")).isEqualTo("0002");
        assertThat(valor(da, dir + "/cbc:ID")).isEqualTo("150131");
        assertThat(valor(da, dir + "/cac:AddressLine/cbc:Line")).isEqualTo("Av. Angamos 500");

        // Sin domicilio configurado solo va el establecimiento (obligatorio, 3030).
        Document sin = documento();
        assertThat(valor(sin, dir + "/cbc:AddressTypeCode")).isEqualTo("0000");
        assertThat(valor(sin, "count(" + dir + "/cbc:ID)")).isEqualTo("0");

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /**
     * Cargos de línea (47/48) y globales (49/46/50): ChargeIndicator true, códigos del catálogo 53, factor/monto/base, y su
     * efecto en LineExtensionAmount (38), base del IGV (3277/3291), ChargeTotalAmount (3301) y PayableAmount (3280).
     */
    @Test void cargosDeLineaYGlobalesEnElXml() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
                List.of(new Item("A", "Con flete gravado", "NIU", new BigDecimal("2"), new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO, null, null, false,
                                List.of(Cargo.porcentaje("47", new BigDecimal("10")), Cargo.monto("48", new BigDecimal("5.00")))),
                        new Item("B", "Sin cargos", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(Cargo.monto("49", new BigDecimal("30.00")), Cargo.porcentaje("46", new BigDecimal("10")), Cargo.monto("50", new BigDecimal("7.00"))),
                null, null, null, List.of(), FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(11, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        String linea = "/inv:Invoice/cac:InvoiceLine[1]";
        assertThat(valor(d, "count(" + linea + "/cac:AllowanceCharge)")).isEqualTo("2");
        assertThat(valor(d, linea + "/cbc:LineExtensionAmount")).isEqualTo("220.00");                          // 200 + 20 (regla 38)
        assertThat(valor(d, linea + "/cac:AllowanceCharge[1]/cbc:ChargeIndicator")).isEqualTo("true");         // 3114
        assertThat(valor(d, linea + "/cac:AllowanceCharge[1]/cbc:AllowanceChargeReasonCode")).isEqualTo("47");
        assertThat(valor(d, linea + "/cac:AllowanceCharge[1]/cbc:AllowanceChargeReasonCode/@listName")).isEqualTo("Cargo/descuento");
        assertThat(valor(d, linea + "/cac:AllowanceCharge[1]/cbc:MultiplierFactorNumeric")).isEqualTo("0.10000");
        assertThat(valor(d, linea + "/cac:AllowanceCharge[1]/cbc:Amount")).isEqualTo("20.00");
        assertThat(valor(d, linea + "/cac:AllowanceCharge[1]/cbc:BaseAmount")).isEqualTo("200.00");
        assertThat(valor(d, linea + "/cac:AllowanceCharge[2]/cbc:AllowanceChargeReasonCode")).isEqualTo("48");
        assertThat(valor(d, linea + "/cac:AllowanceCharge[2]/cbc:Amount")).isEqualTo("5.00");
        assertThat(valor(d, linea + "/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("39.60");                        // 220 × 18 %
        assertThat(valor(d, linea + "/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxableAmount")).isEqualTo("220.00");
        assertThat(valor(d, linea + "/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceAmount")).isEqualTo("132.3000000000");   // 3270: (220 + 39.60 + 5) / 2
        assertThat(valor(d, linea + "/cac:Price/cbc:PriceAmount")).isEqualTo("100.0000000000");
        assertThat(valor(d, "count(/inv:Invoice/cac:InvoiceLine[2]/cac:AllowanceCharge)")).isEqualTo("0");

        String global = "/inv:Invoice/cac:AllowanceCharge";
        assertThat(valor(d, "count(" + global + ")")).isEqualTo("3");
        assertThat(valor(d, global + "[1]/cbc:ChargeIndicator")).isEqualTo("true");
        assertThat(valor(d, global + "[1]/cbc:AllowanceChargeReasonCode")).isEqualTo("49");
        assertThat(valor(d, global + "[1]/cbc:Amount")).isEqualTo("30.00");
        assertThat(valor(d, global + "[1]/cbc:BaseAmount")).isEqualTo("320.00");                                // 220 + 100 (base gravada bruta)
        assertThat(valor(d, global + "[2]/cbc:AllowanceChargeReasonCode")).isEqualTo("46");
        assertThat(valor(d, global + "[2]/cbc:MultiplierFactorNumeric")).isEqualTo("0.10000");
        assertThat(valor(d, global + "[2]/cbc:Amount")).isEqualTo("32.00");
        assertThat(valor(d, global + "[3]/cbc:AllowanceChargeReasonCode")).isEqualTo("50");
        assertThat(valor(d, global + "[3]/cbc:Amount")).isEqualTo("7.00");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']/cbc:TaxableAmount")).isEqualTo("350.00");   // 3277: 320 + 30
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("63.00");                     // 3291: 350 × 18 %
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:LineExtensionAmount")).isEqualTo("350.00");   // 3278
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount")).isEqualTo("413.00");    // 3279
        assertThat(valor(d, "count(/inv:Invoice/cac:LegalMonetaryTotal/cbc:AllowanceTotalAmount)")).isEqualTo("0");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:ChargeTotalAmount")).isEqualTo("44.00");      // 3301: 5 + 32 + 7
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("457.00");         // 3280: 413 + 44

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /** Orden de compra (59), guías (22, catálogo 01 con atributos) y otros documentos (23, catálogo 12) en su sitio del XSD, antes de Signature. */
    @Test void documentosRelacionadosEnElXml() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
                List.of(new Item("A", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO)),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(),
                new Referencias("OC-2026-0457", List.of(new GuiaRelacionada("09", "T001-123"), new GuiaRelacionada("31", "V001-7")),
                        List.of(new DocumentoRelacionado("05", "SCOP-8841203"), new DocumentoRelacionado("99", "CONTRATO-12"))),
                FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(12, "20100066603");
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        assertThat(valor(d, "/inv:Invoice/cac:OrderReference/cbc:ID")).isEqualTo("OC-2026-0457");
        assertThat(valor(d, "count(/inv:Invoice/cac:DespatchDocumentReference)")).isEqualTo("2");
        assertThat(valor(d, "/inv:Invoice/cac:DespatchDocumentReference[1]/cbc:ID")).isEqualTo("T001-123");
        assertThat(valor(d, "/inv:Invoice/cac:DespatchDocumentReference[1]/cbc:DocumentTypeCode")).isEqualTo("09");
        assertThat(valor(d, "/inv:Invoice/cac:DespatchDocumentReference[1]/cbc:DocumentTypeCode/@listAgencyName")).isEqualTo("PE:SUNAT");      // 4251
        assertThat(valor(d, "/inv:Invoice/cac:DespatchDocumentReference[1]/cbc:DocumentTypeCode/@listName")).isEqualTo("Tipo de Documento");   // 4252
        assertThat(valor(d, "/inv:Invoice/cac:DespatchDocumentReference[1]/cbc:DocumentTypeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");   // 4253
        assertThat(valor(d, "/inv:Invoice/cac:DespatchDocumentReference[2]/cbc:DocumentTypeCode")).isEqualTo("31");
        assertThat(valor(d, "count(/inv:Invoice/cac:AdditionalDocumentReference)")).isEqualTo("2");
        assertThat(valor(d, "/inv:Invoice/cac:AdditionalDocumentReference[1]/cbc:ID")).isEqualTo("SCOP-8841203");
        assertThat(valor(d, "/inv:Invoice/cac:AdditionalDocumentReference[1]/cbc:DocumentTypeCode")).isEqualTo("05");
        assertThat(valor(d, "/inv:Invoice/cac:AdditionalDocumentReference[1]/cbc:DocumentTypeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo12");
        assertThat(valor(d, "count(/inv:Invoice/cac:AdditionalDocumentReference[1]/cbc:DocumentTypeCode/@listName)")).isEqualTo("0");   // ambiguo en la hoja: se omite
        assertThat(valor(d, "/inv:Invoice/cac:AdditionalDocumentReference[2]/cbc:DocumentTypeCode")).isEqualTo("99");

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /** Sin referencias no se emite ningún bloque (una factura sin orden de compra ni guías sigue igual que antes). */
    @Test void sinReferenciasNoHayBloques() throws Exception {
        Comprobante c = facturaConTresAfectaciones();
        String xml = new FreemarkerUblGenerator().generar(c, FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        assertThat(valor(d, "count(/inv:Invoice/cac:OrderReference | /inv:Invoice/cac:DespatchDocumentReference | /inv:Invoice/cac:AdditionalDocumentReference)")).isEqualTo("0");
    }

    /** Campos opcionales: DueDate (8), nombre comercial (11), GTIN (29) y código de producto SUNAT (28) por ítem, PayableRoundingAmount (56) y monto en letras del total redondeado. */
    @Test void camposOpcionalesEnElXml() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), LocalDate.of(2026, 10, 13), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
                List.of(new Item("A", "Diésel", "GLL", BigDecimal.ONE, new BigDecimal("118.37"), TipoAfectacionIgv.GRAVADO, null, null, false, List.of(), new CodigoProductoSunat("15101505"), new Gtin("GTIN-13", "7750182000123"))),
                FormaPago.contado(), null, List.of(), null, null, null, List.of(), null, new BigDecimal("-0.37"), FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(13, "20100066603");
        Tenant t = FreemarkerUblGeneratorTest.tenant().conDatosFiscales(null, null, "Andina Store");
        String xml = new FreemarkerUblGenerator().generar(c, t);
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        assertThat(valor(d, "/inv:Invoice/cbc:DueDate")).isEqualTo("2026-10-13");
        assertThat(valor(d, "/inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyName/cbc:Name")).isEqualTo("Andina Store");
        assertThat(valor(d, "/inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo(t.razonSocial());
        String item = "/inv:Invoice/cac:InvoiceLine[1]/cac:Item";
        assertThat(valor(d, item + "/cac:StandardItemIdentification/cbc:ID")).isEqualTo("7750182000123");
        assertThat(valor(d, item + "/cac:StandardItemIdentification/cbc:ID/@schemeID")).isEqualTo("GTIN-13");                // 4333, 4335
        assertThat(valor(d, item + "/cac:CommodityClassification/cbc:ItemClassificationCode")).isEqualTo("15101505");
        assertThat(valor(d, item + "/cac:CommodityClassification/cbc:ItemClassificationCode/@listID")).isEqualTo("UNSPSC");     // 4254
        assertThat(valor(d, item + "/cac:CommodityClassification/cbc:ItemClassificationCode/@listAgencyName")).isEqualTo("GS1 US");
        assertThat(valor(d, item + "/cac:CommodityClassification/cbc:ItemClassificationCode/@listName")).isEqualTo("Item Classification");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount")).isEqualTo("118.37");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableRoundingAmount")).isEqualTo("-0.37");             // 3303
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("118.00");                     // 3280
        assertThat(valor(d, "/inv:Invoice/cbc:Note[@languageLocaleID='1000']")).startsWith("CIENTO DIECIOCHO CON 00/100");

        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>",
                "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), TipoDocumento.FACTURA);
    }

    /** Sin los opcionales no aparecen sus tags (ni PartyName del emisor, que SUNAT observaría vacío: 4092). */
    @Test void sinCamposOpcionalesNoHayTags() throws Exception {
        String xml = new FreemarkerUblGenerator().generar(facturaConTresAfectaciones(), FreemarkerUblGeneratorTest.tenant());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document d = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        assertThat(valor(d, "count(/inv:Invoice/cbc:DueDate | /inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyName"
                + " | /inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableRoundingAmount | //cac:Item/cac:StandardItemIdentification | //cac:Item/cac:CommodityClassification)")).isEqualTo("0");
    }

    /** Regla 3290: con una base grande y descuento fijo el factor de 5 decimales no reproduce el monto, así que no se emite. */
    @Test void descuentoSinFactorCuandoNoReproduceElMonto() throws Exception {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234565", "CLIENTE S.A.C.", null),
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
