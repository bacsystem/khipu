package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Domicilio;
import pe.factura.domain.tenant.EmisorImpreso;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.factura.adapters.ubl.AtributosSunatFacturaTest.valor;

/**
 * Retención (20) y percepción (40), #40: el Retention / Perception UBL 2.0 de las hojas Retenciones1_0 y Percepciones1_0 —agente, proveedor o cliente, régimen y tasa, totales y
 * cada documento con su pago, lo retenido o percibido, el neto y el tipo de cambio— validado contra los XSD UBLPE-Retention-1.0 y UBLPE-Perception-1.0 de SUNAT.
 */
class AgenteUblTest {
    static final Receptor PROVEEDOR = new Receptor("6", "20131312955", "PROVEEDOR DEL NORTE S.A.C.", "Av. Grau 123, Piura");
    static final Receptor CLIENTE = new Receptor("1", "45678912", "JUAN PEREZ QUISPE", null);

    static ComprobanteDeAgente.Documento enSoles(String numero, String importe) {
        return new ComprobanteDeAgente.Documento("01", numero, LocalDate.of(2026, 9, 1), new BigDecimal(importe), "PEN", 1, LocalDate.of(2026, 9, 12), new BigDecimal(importe), null, null);
    }

    static ComprobanteDeAgente.Documento enDolares() {
        return new ComprobanteDeAgente.Documento("01", "E001-45", LocalDate.of(2026, 8, 20), new BigDecimal("1000.00"), "USD", 2, LocalDate.of(2026, 9, 10), new BigDecimal("500.00"),
                new BigDecimal("3.756"), LocalDate.of(2026, 9, 9));
    }

    static Comprobante retencion(BigDecimal redondeo, ComprobanteDeAgente.Documento... docs) {
        Comprobante c = Comprobante.deAgente(UUID.randomUUID(), "R001", LocalDate.of(2026, 9, 13), PROVEEDOR, new ComprobanteDeAgente(TipoDocumento.RETENCION, "01", null, List.of(docs)))
                .redondeo(redondeo).crear("20100066603", FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(7, "20100066603");
        return c;
    }

    static Comprobante percepcion() {
        Comprobante c = Comprobante.deAgente(UUID.randomUUID(), "P001", LocalDate.of(2026, 9, 13), CLIENTE,
                new ComprobanteDeAgente(TipoDocumento.PERCEPCION, "03", null, List.of(new ComprobanteDeAgente.Documento("03", "B001-15", LocalDate.of(2026, 9, 2), new BigDecimal("590.00"),
                        "PEN", 1, LocalDate.of(2026, 9, 13), new BigDecimal("590.00"), null, null)))).crear("20100066603", FreemarkerUblGeneratorTest.CLOCK);
        c.asignarNumero(3, "20100066603");
        return c;
    }

    static Tenant conDomicilio() {
        return FreemarkerUblGeneratorTest.tenant().conDomicilio(new Domicilio("150101", "Av. Abancay 123", "Urb. Centro", null, null, null, null));
    }

    private static String xml(Comprobante c, Tenant t) {
        String xml = new FreemarkerUblGenerator().generar(c, t);
        new JaxpXsdValidator().validar(firmaFalsa(xml), c.tipo());
        return xml;
    }

    @Test void laRetencionEsUnRetentionUbl20ValidoContraSuXsd() throws Exception {
        Document d = NotaUblTest.parsear(xml(retencion(null, enSoles("F001-123", "1180.00"), enDolares()), conDomicilio()));
        assertThat(valor(d, "/ret:Retention/cbc:UBLVersionID")).isEqualTo("2.0");
        assertThat(valor(d, "/ret:Retention/cbc:CustomizationID")).isEqualTo("1.0");
        assertThat(valor(d, "/ret:Retention/cbc:ID")).isEqualTo("R001-7");
        assertThat(valor(d, "/ret:Retention/cbc:IssueDate")).isEqualTo("2026-09-13");

        String agente = "/ret:Retention/cac:AgentParty";
        assertThat(valor(d, agente + "/cac:PartyIdentification/cbc:ID")).isEqualTo("20100066603");
        assertThat(valor(d, agente + "/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("6");
        assertThat(valor(d, agente + "/cac:PostalAddress/cbc:ID")).isEqualTo("150101");
        assertThat(valor(d, agente + "/cac:PostalAddress/cbc:StreetName")).isEqualTo("Av. Abancay 123");
        assertThat(valor(d, agente + "/cac:PostalAddress/cbc:CitySubdivisionName")).isEqualTo("Urb. Centro");
        assertThat(valor(d, agente + "/cac:PostalAddress/cbc:District")).isEqualTo("LIMA");
        assertThat(valor(d, agente + "/cac:PostalAddress/cac:Country/cbc:IdentificationCode")).isEqualTo("PE");
        assertThat(valor(d, agente + "/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("EMPRESA DE PRUEBA S.A.C.");

        String proveedor = "/ret:Retention/cac:ReceiverParty";
        assertThat(valor(d, proveedor + "/cac:PartyIdentification/cbc:ID")).isEqualTo("20131312955");
        assertThat(valor(d, proveedor + "/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("6");
        assertThat(valor(d, proveedor + "/cac:PostalAddress/cbc:StreetName")).isEqualTo("Av. Grau 123, Piura");
        assertThat(valor(d, proveedor + "/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("PROVEEDOR DEL NORTE S.A.C.");

        assertThat(valor(d, "/ret:Retention/sac:SUNATRetentionSystemCode")).isEqualTo("01");
        assertThat(valor(d, "/ret:Retention/sac:SUNATRetentionPercent")).isEqualTo("3.00");
        // 35.40 (3 % de 1180.00) + 56.34 (3 % de 500 × 3.756 = 1878.00): 91.74 retenido; 1144.60 + 1821.66 = 2966.26 pagado.
        assertThat(valor(d, "/ret:Retention/cbc:TotalInvoiceAmount")).isEqualTo("91.74");
        assertThat(valor(d, "/ret:Retention/cbc:TotalInvoiceAmount/@currencyID")).isEqualTo("PEN");
        assertThat(valor(d, "count(/ret:Retention/cbc:PayableRoundingAmount)")).as("sin redondeo: e-beta no lee el XML que lo lleva (0306)").isEqualTo("0");
        assertThat(valor(d, "/ret:Retention/sac:SUNATTotalPaid")).isEqualTo("2966.26");
        assertThat(valor(d, "/ret:Retention/sac:SUNATTotalPaid/@currencyID")).isEqualTo("PEN");

        String soles = "/ret:Retention/sac:SUNATRetentionDocumentReference[1]";
        assertThat(valor(d, soles + "/cbc:ID")).isEqualTo("F001-123");
        assertThat(valor(d, soles + "/cbc:ID/@schemeID")).isEqualTo("01");
        assertThat(valor(d, soles + "/cbc:IssueDate")).isEqualTo("2026-09-01");
        assertThat(valor(d, soles + "/cbc:TotalInvoiceAmount")).isEqualTo("1180.00");
        assertThat(valor(d, soles + "/cac:Payment/cbc:ID")).isEqualTo("1");
        assertThat(valor(d, soles + "/cac:Payment/cbc:PaidAmount")).isEqualTo("1180.00");
        assertThat(valor(d, soles + "/cac:Payment/cbc:PaidDate")).isEqualTo("2026-09-12");
        assertThat(valor(d, soles + "/sac:SUNATRetentionInformation/sac:SUNATRetentionAmount")).isEqualTo("35.40");
        assertThat(valor(d, soles + "/sac:SUNATRetentionInformation/sac:SUNATRetentionDate")).isEqualTo("2026-09-12");
        assertThat(valor(d, soles + "/sac:SUNATRetentionInformation/sac:SUNATNetTotalPaid")).isEqualTo("1144.60");
        assertThat(valor(d, "count(" + soles + "/sac:SUNATRetentionInformation/cac:ExchangeRate)")).as("en soles no lleva tipo de cambio").isEqualTo("0");

        String dolares = "/ret:Retention/sac:SUNATRetentionDocumentReference[2]";
        assertThat(valor(d, dolares + "/cbc:TotalInvoiceAmount/@currencyID")).isEqualTo("USD");
        assertThat(valor(d, dolares + "/cac:Payment/cbc:ID")).isEqualTo("2");
        assertThat(valor(d, dolares + "/cac:Payment/cbc:PaidAmount")).isEqualTo("500.00");
        assertThat(valor(d, dolares + "/cac:Payment/cbc:PaidAmount/@currencyID")).as("2622: la del documento").isEqualTo("USD");
        assertThat(valor(d, dolares + "/sac:SUNATRetentionInformation/sac:SUNATRetentionAmount")).isEqualTo("56.34");
        assertThat(valor(d, dolares + "/sac:SUNATRetentionInformation/sac:SUNATRetentionAmount/@currencyID")).isEqualTo("PEN");
        assertThat(valor(d, dolares + "/sac:SUNATRetentionInformation/sac:SUNATNetTotalPaid")).isEqualTo("1821.66");
        assertThat(valor(d, dolares + "/sac:SUNATRetentionInformation/cac:ExchangeRate/cbc:SourceCurrencyCode")).isEqualTo("USD");
        assertThat(valor(d, dolares + "/sac:SUNATRetentionInformation/cac:ExchangeRate/cbc:TargetCurrencyCode")).isEqualTo("PEN");
        assertThat(valor(d, dolares + "/sac:SUNATRetentionInformation/cac:ExchangeRate/cbc:CalculationRate")).isEqualTo("3.756");
        assertThat(valor(d, dolares + "/sac:SUNATRetentionInformation/cac:ExchangeRate/cbc:Date")).isEqualTo("2026-09-09");
    }

    @Test void sinDomicilioTambienEsValida() throws Exception {
        Document d = NotaUblTest.parsear(xml(retencion(null, enSoles("F001-1", "100.00")), FreemarkerUblGeneratorTest.tenant()));
        assertThat(valor(d, "count(/ret:Retention/cac:AgentParty/cac:PostalAddress)")).isEqualTo("0");
        assertThat(valor(d, "/ret:Retention/sac:SUNATTotalPaid")).isEqualTo("97.00");
    }

    @Test void laPercepcionEsUnPerceptionUbl20ValidoContraSuXsd() throws Exception {
        Document d = NotaUblTest.parsear(xml(percepcion(), conDomicilio()));
        assertThat(valor(d, "/per:Perception/cbc:ID")).isEqualTo("P001-3");
        assertThat(valor(d, "/per:Perception/cac:ReceiverParty/cac:PartyIdentification/cbc:ID/@schemeID")).as("el cliente con DNI").isEqualTo("1");
        assertThat(valor(d, "/per:Perception/sac:SUNATPerceptionSystemCode")).isEqualTo("03");
        assertThat(valor(d, "/per:Perception/sac:SUNATPerceptionPercent")).isEqualTo("0.50");
        assertThat(valor(d, "/per:Perception/cbc:TotalInvoiceAmount")).as("0.5 % de 590.00").isEqualTo("2.95");
        assertThat(valor(d, "/per:Perception/sac:SUNATTotalCashed")).isEqualTo("592.95");
        String doc = "/per:Perception/sac:SUNATPerceptionDocumentReference";
        assertThat(valor(d, doc + "/cbc:ID/@schemeID")).isEqualTo("03");
        assertThat(valor(d, doc + "/sac:SUNATPerceptionInformation/sac:SUNATPerceptionAmount")).isEqualTo("2.95");
        assertThat(valor(d, doc + "/sac:SUNATPerceptionInformation/sac:SUNATNetTotalCashed")).isEqualTo("592.95");
        assertThat(valor(d, "count(//sac:SUNATRetentionInformation)")).isEqualTo("0");
    }

    /** El XSD es el de su tipo: una retención no pasa como percepción. */
    @Test void cadaUnoSeValidaConSuPropioXsd() {
        String xml = new FreemarkerUblGenerator().generar(retencion(null, enSoles("F001-1", "100.00")), FreemarkerUblGeneratorTest.tenant());
        assertThatThrownBy(() -> new JaxpXsdValidator().validar(firmaFalsa(xml), TipoDocumento.PERCEPCION)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("XSD_INVALIDO");
    }

    /** El PDF imprime al emisor tal como quedó en el XML firmado: en estos documentos es el agente (cac:AgentParty), con su domicilio en cac:PostalAddress. */
    @Test void elEmisorFirmadoSeLeeDelAgente() {
        String xml = new FreemarkerUblGenerator().generar(retencion(null, enSoles("F001-1", "100.00")), conDomicilio());
        EmisorImpreso e = new XmlEmisorFirmado().leer(xml.getBytes(StandardCharsets.UTF_8)).orElseThrow();
        assertThat(e.ruc()).isEqualTo("20100066603");
        assertThat(e.razonSocial()).isEqualTo("EMPRESA DE PRUEBA S.A.C.");
        assertThat(e.domicilio().ubigeo()).isEqualTo("150101");
        assertThat(e.domicilio().direccion()).isEqualTo("Av. Abancay 123");
    }

    private static String firmaFalsa(String xml) {
        return xml.replace("<ext:ExtensionContent/>", "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>");
    }
}
