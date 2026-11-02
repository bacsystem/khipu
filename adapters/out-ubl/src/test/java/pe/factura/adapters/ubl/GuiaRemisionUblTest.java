package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.Tenant;

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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #39: la guía de remisión como DespatchAdvice UBL 2.1, validada contra el XSD real, con los nodos que nombran las reglas de SUNAT. */
class GuiaRemisionUblTest {
    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-20T15:04:05Z"), ZoneId.of("America/Lima"));
    static final LocalDate HOY = LocalDate.of(2026, 10, 20);
    static final String EMISOR = "20100066603";

    static Tenant emisor() { return FreemarkerUblGeneratorTest.tenant(); }

    static Traslado traslado(List<Vehiculo> v, List<Conductor> c, Set<IndicadorDeTraslado> ind) {
        return new Traslado(HOY.plusDays(1), new BigDecimal("1250.5"), "KGM", null, new PuntoDeTraslado("150101", "Av. Argentina 1234, Lima", "0001", null),
                PuntoDeTraslado.de("040101", "Calle Mercaderes 200, Arequipa"), v, c, ind);
    }
    static final List<ItemDeGuia> ITEMS = List.of(new ItemDeGuia("P001", "Laptop 15 pulgadas", "NIU", new BigDecimal("2"), "43211503"),
            new ItemDeGuia(null, "Café pergamino & tostado", "KGM", new BigDecimal("12.375"), null));

    static GuiaRemision privada() {
        GuiaRemision g = GuiaRemision.crear(UUID.randomUUID(), EMISOR, "T001", HOY, new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."),
                new DatosDeGuia.DeRemitente("01", null, ModalidadTraslado.PRIVADO, null),
                traslado(List.of(new Vehiculo("ABC-123"), new Vehiculo("XYZ987")), List.of(new Conductor("1", "45678912", "JUAN", "QUISPE MAMANI", "Q45678912"),
                        new Conductor("4", "CE001234", "ANA", "RUIZ", "B12345678")), Set.of(IndicadorDeTraslado.RETORNO_VEHICULO_VACIO)),
                ITEMS, List.of(new DocumentoDeTraslado("01", "F001-123", EMISOR)), "Frágil", RELOJ);
        g.asignarNumero(15, EMISOR);
        return g;
    }

    static GuiaRemision publica() {
        GuiaRemision g = GuiaRemision.crear(UUID.randomUUID(), EMISOR, "T001", HOY, new ParteDeGuia("1", "45678912", "MARÍA LÓPEZ"),
                new DatosDeGuia.DeRemitente("13", "Traslado a feria", ModalidadTraslado.PUBLICO, new TransportistaContratado("20480072872", "TRANSPORTES DEL SUR S.A.C.", "1512345CNG")),
                traslado(List.of(), List.of(), Set.of()), ITEMS, List.of(), null, RELOJ);
        g.asignarNumero(16, EMISOR);
        return g;
    }

    static GuiaRemision deTransportista() {
        GuiaRemision g = GuiaRemision.crear(UUID.randomUUID(), EMISOR, "V001", HOY, new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."),
                new DatosDeGuia.DeTransportista(new ParteDeGuia("6", "20555555556", "DISTRIBUIDORA NORTE S.A."), "1512345CNG"),
                traslado(List.of(new Vehiculo("ABC123")), List.of(new Conductor("1", "45678912", "JUAN", "QUISPE", "Q45678912")), Set.of(IndicadorDeTraslado.TRANSBORDO_PROGRAMADO)),
                ITEMS, List.of(new DocumentoDeTraslado("09", "T001-15", "20555555556")), null, RELOJ);
        g.asignarNumero(3, EMISOR);
        return g;
    }

    /** El XSD exige contenido en ExtensionContent, que pone la firma: aquí un marcador (out-ubl no puede depender de out-signing). */
    static void validarXsd(String xml, TipoDocumento tipo) {
        new JaxpXsdValidator().validar(xml.replace("<ext:ExtensionContent/>", "<ext:ExtensionContent><x:firma xmlns:x=\"urn:test:placeholder\"/></ext:ExtensionContent>"), tipo);
    }

    @Test void laGuiaRemitenteEnTransportePrivadoEsUnDespatchAdviceValido() throws Exception {
        String xml = new FreemarkerGuiaUblGenerator().generar(privada(), emisor());
        validarXsd(xml, TipoDocumento.GUIA_REMITENTE);
        Document d = parsear(xml);
        assertThat(v(d, "/da:DespatchAdvice/cbc:UBLVersionID")).isEqualTo("2.1");
        assertThat(v(d, "/da:DespatchAdvice/cbc:CustomizationID")).isEqualTo("2.0");
        assertThat(v(d, "/da:DespatchAdvice/cbc:ID")).isEqualTo("T001-15");
        assertThat(v(d, "/da:DespatchAdvice/cbc:IssueDate")).isEqualTo("2026-10-20");
        assertThat(v(d, "/da:DespatchAdvice/cbc:IssueTime")).isEqualTo("10:04:05");
        assertThat(v(d, "/da:DespatchAdvice/cbc:DespatchAdviceTypeCode")).isEqualTo("09");
        assertThat(v(d, "/da:DespatchAdvice/cbc:DespatchAdviceTypeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");
        assertThat(v(d, "/da:DespatchAdvice/cbc:Note")).isEqualTo("Frágil");
        assertThat(v(d, "/da:DespatchAdvice/cac:AdditionalDocumentReference/cbc:ID")).isEqualTo("F001-123");
        assertThat(v(d, "/da:DespatchAdvice/cac:AdditionalDocumentReference/cbc:DocumentTypeCode")).isEqualTo("01");
        assertThat(v(d, "/da:DespatchAdvice/cac:AdditionalDocumentReference/cbc:DocumentTypeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo61");
        assertThat(v(d, "/da:DespatchAdvice/cac:AdditionalDocumentReference/cac:IssuerParty/cac:PartyIdentification/cbc:ID")).isEqualTo(EMISOR);
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo(EMISOR);
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("6");
        assertThat(v(d, "count(/da:DespatchAdvice/cac:DespatchSupplierParty//cbc:CompanyID)")).as("el MTC es del transportista").isEqualTo("0");
        assertThat(v(d, "/da:DespatchAdvice/cac:DeliveryCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo("20601234565");
        assertThat(v(d, "/da:DespatchAdvice/cac:DeliveryCustomerParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("COMERCIAL ANDINA S.A.C.");
        String s = "/da:DespatchAdvice/cac:Shipment";
        assertThat(v(d, s + "/cbc:ID")).isEqualTo("SUNAT_Envio");
        assertThat(v(d, s + "/cbc:HandlingCode")).isEqualTo("01");
        assertThat(v(d, s + "/cbc:HandlingCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo20");
        assertThat(v(d, s + "/cbc:HandlingInstructions")).isEqualTo("Venta");
        assertThat(v(d, s + "/cbc:GrossWeightMeasure")).isEqualTo("1250.5");
        assertThat(v(d, s + "/cbc:GrossWeightMeasure/@unitCode")).isEqualTo("KGM");
        assertThat(v(d, s + "/cbc:SpecialInstructions")).isEqualTo("SUNAT_Envio_IndicadorRetornoVehiculoVacio");
        assertThat(v(d, s + "/cac:ShipmentStage/cbc:TransportModeCode")).isEqualTo("02");
        assertThat(v(d, s + "/cac:ShipmentStage/cbc:TransportModeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo18");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:TransitPeriod/cbc:StartDate")).isEqualTo("2026-10-21");
        assertThat(v(d, "count(" + s + "/cac:ShipmentStage/cac:CarrierParty)")).isEqualTo("0");
        assertThat(v(d, "count(" + s + "/cac:ShipmentStage/cac:DriverPerson)")).isEqualTo("2");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:DriverPerson[1]/cbc:ID")).isEqualTo("45678912");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:DriverPerson[1]/cbc:ID/@schemeID")).isEqualTo("1");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:DriverPerson[1]/cbc:FirstName")).isEqualTo("JUAN");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:DriverPerson[1]/cbc:FamilyName")).isEqualTo("QUISPE MAMANI");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:DriverPerson[1]/cbc:JobTitle")).isEqualTo("Principal");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:DriverPerson[1]/cac:IdentityDocumentReference/cbc:ID")).isEqualTo("Q45678912");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:DriverPerson[2]/cbc:JobTitle")).isEqualTo("Secundario");
        assertThat(v(d, s + "/cac:Delivery/cac:DeliveryAddress/cbc:ID")).isEqualTo("040101");
        assertThat(v(d, s + "/cac:Delivery/cac:DeliveryAddress/cbc:ID/@schemeAgencyName")).isEqualTo("PE:INEI");
        assertThat(v(d, s + "/cac:Delivery/cac:DeliveryAddress/cac:AddressLine/cbc:Line")).isEqualTo("Calle Mercaderes 200, Arequipa");
        assertThat(v(d, "count(" + s + "/cac:Delivery/cac:DeliveryAddress/cbc:AddressTypeCode)")).isEqualTo("0");
        assertThat(v(d, s + "/cac:Delivery/cac:Despatch/cac:DespatchAddress/cbc:ID")).isEqualTo("150101");
        assertThat(v(d, s + "/cac:Delivery/cac:Despatch/cac:DespatchAddress/cbc:AddressTypeCode")).isEqualTo("0001");
        assertThat(v(d, s + "/cac:Delivery/cac:Despatch/cac:DespatchAddress/cbc:AddressTypeCode/@listID")).as("3410: el RUC del establecimiento").isEqualTo(EMISOR);
        assertThat(v(d, "count(" + s + "/cac:Delivery/cac:Despatch/cac:DespatchParty)")).isEqualTo("0");
        assertThat(v(d, s + "/cac:TransportHandlingUnit/cac:TransportEquipment/cbc:ID")).isEqualTo("ABC123");
        assertThat(v(d, s + "/cac:TransportHandlingUnit/cac:TransportEquipment/cac:AttachedTransportEquipment/cbc:ID")).isEqualTo("XYZ987");
        assertThat(v(d, "count(/da:DespatchAdvice/cac:DespatchLine)")).isEqualTo("2");
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchLine[1]/cbc:ID")).isEqualTo("1");
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchLine[1]/cbc:DeliveredQuantity")).isEqualTo("2");
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchLine[1]/cbc:DeliveredQuantity/@unitCode")).isEqualTo("NIU");
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchLine[1]/cac:OrderLineReference/cbc:LineID")).isEqualTo("1");
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchLine[1]/cac:Item/cac:SellersItemIdentification/cbc:ID")).isEqualTo("P001");
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchLine[1]/cac:Item/cac:CommodityClassification/cbc:ItemClassificationCode")).isEqualTo("43211503");
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchLine[2]/cbc:DeliveredQuantity")).isEqualTo("12.375");
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchLine[2]/cac:Item/cbc:Description")).as("escapado").isEqualTo("Café pergamino & tostado");
        assertThat(v(d, "count(/da:DespatchAdvice/cac:DespatchLine[2]/cac:Item/cac:SellersItemIdentification)")).isEqualTo("0");
    }

    @Test void enTransportePublicoVaLaEmpresaDeTransporteSinVehiculosNiConductores() throws Exception {
        String xml = new FreemarkerGuiaUblGenerator().generar(publica(), emisor());
        validarXsd(xml, TipoDocumento.GUIA_REMITENTE);
        Document d = parsear(xml);
        String s = "/da:DespatchAdvice/cac:Shipment";
        assertThat(v(d, s + "/cbc:HandlingCode")).isEqualTo("13");
        assertThat(v(d, s + "/cbc:HandlingInstructions")).isEqualTo("Traslado a feria");
        assertThat(v(d, s + "/cac:ShipmentStage/cbc:TransportModeCode")).isEqualTo("01");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:CarrierParty/cac:PartyIdentification/cbc:ID")).isEqualTo("20480072872");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:CarrierParty/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("6");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:CarrierParty/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("TRANSPORTES DEL SUR S.A.C.");
        assertThat(v(d, s + "/cac:ShipmentStage/cac:CarrierParty/cac:PartyLegalEntity/cbc:CompanyID")).isEqualTo("1512345CNG");
        assertThat(v(d, "count(" + s + "/cac:ShipmentStage/cac:DriverPerson)")).isEqualTo("0");
        assertThat(v(d, "count(" + s + "/cac:TransportHandlingUnit)")).isEqualTo("0");
        assertThat(v(d, "count(" + s + "/cbc:SpecialInstructions)")).isEqualTo("0");
        assertThat(v(d, "/da:DespatchAdvice/cac:DeliveryCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("1");
        assertThat(v(d, "count(/da:DespatchAdvice/cbc:Note)")).isEqualTo("0");
    }

    @Test void laGuiaTransportistaLlevaElRemitenteYElMtcDelEmisorSinMotivoNiModalidad() throws Exception {
        String xml = new FreemarkerGuiaUblGenerator().generar(deTransportista(), emisor());
        validarXsd(xml, TipoDocumento.GUIA_TRANSPORTISTA);
        Document d = parsear(xml);
        String s = "/da:DespatchAdvice/cac:Shipment";
        assertThat(v(d, "/da:DespatchAdvice/cbc:ID")).isEqualTo("V001-3");
        assertThat(v(d, "/da:DespatchAdvice/cbc:DespatchAdviceTypeCode")).isEqualTo("31");
        assertThat(v(d, "/da:DespatchAdvice/cac:DespatchSupplierParty/cac:Party/cac:PartyLegalEntity/cbc:CompanyID")).isEqualTo("1512345CNG");
        assertThat(v(d, "count(" + s + "/cbc:HandlingCode | " + s + "/cbc:HandlingInstructions | " + s + "/cac:ShipmentStage/cbc:TransportModeCode)")).isEqualTo("0");
        assertThat(v(d, s + "/cac:Delivery/cac:Despatch/cac:DespatchParty/cac:PartyIdentification/cbc:ID")).isEqualTo("20555555556");
        assertThat(v(d, s + "/cac:Delivery/cac:Despatch/cac:DespatchParty/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("DISTRIBUIDORA NORTE S.A.");
        assertThat(v(d, s + "/cac:Delivery/cac:Despatch/cac:DespatchAddress/cbc:AddressTypeCode/@listID")).as("el establecimiento es del remitente").isEqualTo("20555555556");
        assertThat(v(d, s + "/cbc:SpecialInstructions")).isEqualTo("SUNAT_Envio_IndicadorTransbordoProgramado");
        assertThat(v(d, "/da:DespatchAdvice/cac:AdditionalDocumentReference/cbc:DocumentTypeCode")).isEqualTo("09");
        assertThat(v(d, "count(" + s + "/cac:TransportHandlingUnit/cac:TransportEquipment/cac:AttachedTransportEquipment)")).isEqualTo("0");
    }

    @Test void unaGuiaSinNumeroNoSeGenera() {
        GuiaRemision g = GuiaRemision.crear(UUID.randomUUID(), EMISOR, "T001", HOY, new ParteDeGuia("6", "20601234565", "COMERCIAL ANDINA S.A.C."),
                new DatosDeGuia.DeRemitente("01", null, ModalidadTraslado.PRIVADO, null), traslado(List.of(new Vehiculo("ABC123")),
                        List.of(new Conductor("1", "45678912", "JUAN", "QUISPE", "Q45678912")), Set.of()), ITEMS, List.of(), null, RELOJ);
        assertThatThrownBy(() -> new FreemarkerGuiaUblGenerator().generar(g, emisor())).isInstanceOf(IllegalStateException.class);
    }

    @Test void elXsdDeLaGuiaRechazaUnDespatchAdviceMalFormado() {
        assertThatThrownBy(() -> new JaxpXsdValidator().validar("<?xml version=\"1.0\"?><DespatchAdvice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2\"><Basura/></DespatchAdvice>",
                TipoDocumento.GUIA_TRANSPORTISTA)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("XSD_INVALIDO");
        // El XML de una factura no pasa por el esquema de la guía.
        String factura = new FreemarkerUblGenerator().generar(FreemarkerUblGeneratorTest.factura(), emisor());
        assertThatThrownBy(() -> validarXsd(factura, TipoDocumento.GUIA_REMITENTE)).isInstanceOf(DomainException.class);
    }

    static Document parsear(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    static String v(Document d, String expr) throws Exception {
        XPath xp = XPathFactory.newInstance().newXPath();
        xp.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String p) {
                return switch (p) {
                    case "cbc" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
                    case "cac" -> "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
                    case "da" -> "urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2";
                    default -> XMLConstants.NULL_NS_URI;
                };
            }
            public String getPrefix(String uri) { return null; }
            public Iterator<String> getPrefixes(String uri) { return null; }
        });
        return xp.evaluate(expr, d);
    }
}
