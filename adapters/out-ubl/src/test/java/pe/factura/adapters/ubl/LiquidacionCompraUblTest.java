package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.adapters.ubl.AtributosSunatFacturaTest.valor;

/**
 * Liquidación de compra (04, #327): el SelfBilledInvoice de la hoja LiquidacionCompra2_0 —partes invertidas, vendedor con su domicilio y condición, lugar de la operación,
 * retención de renta (3000) por línea y global, importe neto— validado contra el XSD de UBL 2.1 que SUNAT publica.
 */
class LiquidacionCompraUblTest {
    static final Receptor VENDEDOR = new Receptor("1", "45678912", "JUAN QUISPE MAMANI", "Caserío Santa Rosa s/n");
    static final LiquidacionCompra DATOS = new LiquidacionCompra("060101", "02", new LiquidacionCompra.Ubicacion("Fundo Santa Rosa, km 12 carretera a Bambamarca", "060801", "03"), null);

    static Item linea(String descripcion, String cantidad, String precio, TipoAfectacionIgv afectacion) {
        return new Item("CAF-01", descripcion, "KGM", new BigDecimal(cantidad), new BigDecimal(precio), afectacion, null, null, false, List.of(), CodigoProductoSunat.de("50201706"), null);
    }

    static Comprobante liquidacion(List<Item> items) {
        Comprobante c = Comprobante.liquidacionCompra(UUID.randomUUID(), "L001", LocalDate.of(2026, 9, 13), "PEN", null, VENDEDOR, DATOS, items).crear(FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(4, "20100066603");
        return c;
    }

    static Tenant conDomicilio() { return FreemarkerUblGeneratorTest.tenant().conDomicilio(new Domicilio("060101", "Jr. Del Comercio 450", null, null, null, null, null)); }

    private static Document generar(Comprobante c, Tenant t) throws Exception {
        String xml = new FreemarkerUblGenerator().generar(c, t);
        new JaxpXsdValidator().validar(firmaFalsa(xml), TipoDocumento.LIQUIDACION_COMPRA);
        return NotaUblTest.parsear(xml);
    }

    @Test void cabeceraYPartesInvertidas() throws Exception {
        Document d = generar(liquidacion(List.of(linea("Café pergamino", "10", "118", TipoAfectacionIgv.GRAVADO))), conDomicilio());
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:UBLVersionID")).isEqualTo("2.1");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:CustomizationID")).isEqualTo("2.0");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:ID")).isEqualTo("L001-4");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:InvoiceTypeCode")).isEqualTo("04");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:InvoiceTypeCode/@listID")).isEqualTo("0501");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:InvoiceTypeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:Note[@languageLocaleID='1000']")).isEqualTo("NOVECIENTOS OCHENTA Y CINCO CON 00/100 SOLES");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:DocumentCurrencyCode")).isEqualTo("PEN");
        // Sin vencimiento ni forma de pago: el SelfBilledInvoice no tiene DueDate y la hoja no tiene PaymentTerms.
        assertThat(valor(d, "count(/lc:SelfBilledInvoice/cbc:DueDate | /lc:SelfBilledInvoice/cac:PaymentTerms | /lc:SelfBilledInvoice/cac:AllowanceCharge)")).isEqualTo("0");

        String comprador = "/lc:SelfBilledInvoice/cac:AccountingCustomerParty/cac:Party";
        assertThat(valor(d, comprador + "/cac:PartyIdentification/cbc:ID")).isEqualTo("20100066603");
        assertThat(valor(d, comprador + "/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("6");
        assertThat(valor(d, comprador + "/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("EMPRESA DE PRUEBA S.A.C.");
        assertThat(valor(d, comprador + "/cac:PartyLegalEntity/cac:RegistrationAddress/cbc:ID")).isEqualTo("060101");
        assertThat(valor(d, comprador + "/cac:PartyLegalEntity/cac:RegistrationAddress/cac:AddressLine/cbc:Line")).isEqualTo("Jr. Del Comercio 450");
        assertThat(valor(d, "count(" + comprador + "/cac:PartyLegalEntity/cac:RegistrationAddress/cbc:AddressTypeCode)")).as("sin el código de establecimiento: la hoja no lo tiene en el comprador").isEqualTo("0");

        String vendedor = "/lc:SelfBilledInvoice/cac:AccountingSupplierParty/cac:Party";
        assertThat(valor(d, vendedor + "/cac:PartyIdentification/cbc:ID")).isEqualTo("45678912");
        assertThat(valor(d, vendedor + "/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("1");
        assertThat(valor(d, vendedor + "/cac:PartyIdentification/cbc:ID/@schemeURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06");
        assertThat(valor(d, vendedor + "/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("JUAN QUISPE MAMANI");
        assertThat(valor(d, vendedor + "/cac:PartyLegalEntity/cac:RegistrationAddress/cbc:ID")).isEqualTo("060101");
        assertThat(valor(d, vendedor + "/cac:PartyLegalEntity/cac:RegistrationAddress/cbc:ID/@schemeName")).isEqualTo("Ubigeos");
        assertThat(valor(d, vendedor + "/cac:PartyLegalEntity/cac:RegistrationAddress/cbc:AddressTypeCode")).isEqualTo("02");
        assertThat(valor(d, vendedor + "/cac:PartyLegalEntity/cac:RegistrationAddress/cac:AddressLine/cbc:Line")).isEqualTo("Caserío Santa Rosa s/n");
        assertThat(valor(d, vendedor + "/cac:PartyLegalEntity/cac:RegistrationAddress/cac:Country/cbc:IdentificationCode")).isEqualTo("PE");

        String lugar = "/lc:SelfBilledInvoice/cac:DeliveryTerms/cac:DeliveryLocation";
        assertThat(valor(d, lugar + "/cbc:LocationTypeCode")).isEqualTo("03");
        assertThat(valor(d, lugar + "/cac:Address/cbc:ID")).isEqualTo("060801");
        assertThat(valor(d, lugar + "/cac:Address/cac:AddressLine/cbc:Line")).isEqualTo("Fundo Santa Rosa, km 12 carretera a Bambamarca");
    }

    @Test void tributosRetencionDeRentaYNeto() throws Exception {
        Document d = generar(liquidacion(List.of(linea("Café pergamino", "10", "118", TipoAfectacionIgv.GRAVADO), linea("Papa", "3", "50", TipoAfectacionIgv.EXONERADO))),
                FreemarkerUblGeneratorTest.tenant());
        String total = "/lc:SelfBilledInvoice/cac:TaxTotal";
        assertThat(valor(d, "count(" + total + ")")).as("un solo TaxTotal global (3024)").isEqualTo("1");
        assertThat(valor(d, total + "/cbc:TaxAmount")).as("solo 1000 y 9999, sin la renta (4301)").isEqualTo("180.00");
        assertThat(valor(d, total + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']/cbc:TaxableAmount")).isEqualTo("1000.00");
        assertThat(valor(d, total + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='9997']/cbc:TaxableAmount")).isEqualTo("150.00");
        String renta = total + "/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='3000']";
        assertThat(valor(d, renta + "/cbc:TaxableAmount")).as("4345").isEqualTo("1150.00");
        assertThat(valor(d, renta + "/cbc:TaxAmount")).as("4346: 15.00 + 2.25").isEqualTo("17.25");
        assertThat(valor(d, renta + "/cac:TaxCategory/cac:TaxScheme/cbc:Name")).isEqualTo("IR");
        assertThat(valor(d, renta + "/cac:TaxCategory/cac:TaxScheme/cbc:TaxTypeCode")).isEqualTo("TOX");

        String monetario = "/lc:SelfBilledInvoice/cac:LegalMonetaryTotal";
        assertThat(valor(d, monetario + "/cbc:LineExtensionAmount")).isEqualTo("1150.00");
        assertThat(valor(d, monetario + "/cbc:TaxInclusiveAmount")).as("4310").isEqualTo("1330.00");
        assertThat(valor(d, monetario + "/cbc:PayableAmount")).as("4312: 1330.00 − 180.00 − 17.25").isEqualTo("1132.75");

        String l1 = "/lc:SelfBilledInvoice/cac:InvoiceLine[1]";
        assertThat(valor(d, l1 + "/cac:TaxTotal/cbc:TaxAmount")).as("4293: solo 1000 y 9999").isEqualTo("180.00");
        assertThat(valor(d, "count(" + l1 + "/cac:TaxTotal)")).isEqualTo("1");
        String rentaL1 = l1 + "/cac:TaxTotal/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='3000']";
        assertThat(valor(d, rentaL1 + "/cbc:TaxableAmount")).as("4294").isEqualTo("1000.00");
        assertThat(valor(d, rentaL1 + "/cbc:TaxAmount")).as("2464").isEqualTo("15.00");
        assertThat(valor(d, rentaL1 + "/cac:TaxCategory/cbc:Percent")).as("4237").isEqualTo("1.50");
        assertThat(valor(d, "count(" + rentaL1 + "/cac:TaxCategory/cbc:TaxExemptionReasonCode)")).as("3050").isEqualTo("0");
        assertThat(valor(d, l1 + "/cac:TaxTotal/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='1000']/cac:TaxCategory/cbc:TaxExemptionReasonCode")).isEqualTo("10");
        assertThat(valor(d, l1 + "/cac:Item/cac:CommodityClassification/cbc:ItemClassificationCode")).as("3506").isEqualTo("50201706");
        assertThat(valor(d, l1 + "/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceAmount")).as("2028").isEqualTo("118.0000000000");
        assertThat(valor(d, l1 + "/cac:Price/cbc:PriceAmount")).isEqualTo("100.0000000000");
        String l2 = "/lc:SelfBilledInvoice/cac:InvoiceLine[2]";
        assertThat(valor(d, l2 + "/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("0.00");
        assertThat(valor(d, l2 + "/cac:TaxTotal/cac:TaxSubtotal[cac:TaxCategory/cac:TaxScheme/cbc:ID='3000']/cbc:TaxAmount")).isEqualTo("2.25");
    }

    @Test void enDolaresConRedondeoGuiasYOtrosDocumentos() throws Exception {
        Comprobante c = Comprobante.liquidacionCompra(UUID.randomUUID(), "L001", LocalDate.of(2026, 9, 13), "USD", "0502", VENDEDOR, DATOS,
                        List.of(linea("Cacao", "100", "2.36", TipoAfectacionIgv.GRAVADO)))
                .redondeo(new BigDecimal("0.50"))
                .referencias(new Referencias(null, List.of(new GuiaRelacionada("09", "T001-123")), List.of(new DocumentoRelacionado("99", "ACTA-7"))))
                .crear(FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(5, "20100066603");
        Document d = generar(c, FreemarkerUblGeneratorTest.tenant());
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:InvoiceTypeCode/@listID")).isEqualTo("0502");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cac:LegalMonetaryTotal/cbc:PayableAmount/@currencyID")).isEqualTo("USD");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cac:LegalMonetaryTotal/cbc:PayableRoundingAmount")).isEqualTo("0.50");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("197.50");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cac:DespatchDocumentReference/cbc:ID")).isEqualTo("T001-123");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cac:AdditionalDocumentReference/cbc:DocumentTypeCode")).isEqualTo("99");
        assertThat(valor(d, "/lc:SelfBilledInvoice/cbc:Note[@languageLocaleID='1000']")).isEqualTo("CIENTO NOVENTA Y SIETE CON 50/100 DOLARES AMERICANOS");
    }

    @Test void elEmisorDelXmlFirmadoEsElCompradorNoElVendedor() {
        byte[] xml = new FreemarkerUblGenerator().generar(liquidacion(List.of(linea("Café", "1", "118", TipoAfectacionIgv.GRAVADO))), conDomicilio()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var e = new XmlEmisorFirmado().leer(xml).orElseThrow();
        assertThat(e.ruc()).isEqualTo("20100066603");
        assertThat(e.razonSocial()).isEqualTo("EMPRESA DE PRUEBA S.A.C.");
        assertThat(e.domicilio().ubigeo()).isEqualTo("060101");
        assertThat(e.domicilio().direccion()).isEqualTo("Jr. Del Comercio 450");
    }

    private static String firmaFalsa(String xml) {
        return xml.replace("<ext:ExtensionContent/>", "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>");
    }
}
