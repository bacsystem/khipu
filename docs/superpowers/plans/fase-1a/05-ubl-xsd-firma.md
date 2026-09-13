# 05 · UBL, XSD y firma digital

Índice del plan: [README](README.md). Módulos `adapters/out-ubl`, `adapters/out-signing`. Referencia SUNAT: `docs/sunat/notes.md` §5 y §6; guía `docs/sunat/ref/guia_xml_factura_ubl2.1.pdf`.

### Task 12: Plantilla `Invoice` UBL 2.1 y validador XSD

**Files:**
- Create: `adapters/out-ubl/src/main/resources/templates/invoice.ftl`
- Create: `adapters/out-ubl/src/main/resources/xsd/2.1/maindoc/UBL-Invoice-2.1.xsd` y `adapters/out-ubl/src/main/resources/xsd/2.1/common/*.xsd` (copiados del ZIP oficial)
- Create: `adapters/out-ubl/src/main/java/pe/factura/adapters/ubl/FreemarkerUblGenerator.java`, `JaxpXsdValidator.java`
- Test: `adapters/out-ubl/src/test/java/pe/factura/adapters/ubl/FreemarkerUblGeneratorTest.java`, `JaxpXsdValidatorTest.java`

**Interfaces:**
- Produces: `new FreemarkerUblGenerator()` implementa `UblGenerator`; `new JaxpXsdValidator()` implementa `XsdValidator` (carga los XSD del classpath una vez).
- Consumes: `Comprobante`, `Tenant`, `Totales`, `ItemCalculado`, `MontoEnLetras`.

- [ ] **Step 1: Copiar los XSD oficiales**

```bash
cd /Users/christian/Documents/workspace/dev/clients/owner/claude/factura
mkdir -p adapters/out-ubl/src/main/resources/xsd
unzip -q docs/sunat/ref/sunat_xsd_ubl_2.0_2.1.zip "Archivos XSD/2.1/*" -d /tmp/sunat-xsd
cp -R "/tmp/sunat-xsd/Archivos XSD/2.1" adapters/out-ubl/src/main/resources/xsd/2.1
ls adapters/out-ubl/src/main/resources/xsd/2.1/maindoc/UBL-Invoice-2.1.xsd adapters/out-ubl/src/main/resources/xsd/2.1/common | head
```
Expected: se listan `UBL-Invoice-2.1.xsd` y ~14 archivos `common/`. Los `maindoc` distintos de Invoice/CreditNote/DebitNote/ApplicationResponse/DespatchAdvice pueden borrarse para reducir tamaño.

- [ ] **Step 2: Tests**

`FreemarkerUblGeneratorTest.java`:
```java
package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.*;
import pe.factura.domain.tenant.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FreemarkerUblGeneratorTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static Tenant tenant() {
        return new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA DE PRUEBA S.A.C.", Entorno.BETA, null,
                new CertificadoDigital(new byte[0], "", LocalDate.of(2030, 1, 1)));
    }
    static Comprobante factura() {
        Comprobante c = Comprobante.crearFactura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "CLIENTE & CIA S.A.C.", "AV. LIMA 123"),
                List.of(new Item("P001", "Laptop <15\">", "NIU", BigDecimal.ONE, new BigDecimal("2360.00"), TipoAfectacionIgv.GRAVADO),
                        new Item("P002", "Libro", "NIU", new BigDecimal("2"), new BigDecimal("50.00"), TipoAfectacionIgv.EXONERADO)), CLOCK);
        c.asignarNumero(1, "20100066603");
        return c;
    }

    @Test void generaInvoiceConDatosClave() {
        String xml = new FreemarkerUblGenerator().generar(factura(), tenant());
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"");
        assertThat(xml).contains("<cbc:UBLVersionID>2.1</cbc:UBLVersionID>")
                .contains("<cbc:CustomizationID>2.0</cbc:CustomizationID>")
                .contains("<cbc:ID>F001-1</cbc:ID>")
                .contains("<cbc:IssueDate>2026-09-13</cbc:IssueDate>")
                .contains("<cbc:InvoiceTypeCode listID=\"0101\"")
                .contains("<cbc:Note languageLocaleID=\"1000\">DOS MIL CUATROCIENTOS SESENTA CON 00/100 SOLES</cbc:Note>")
                .contains("<cbc:ID schemeID=\"6\">20100066603</cbc:ID>")
                .contains("<cbc:ID schemeID=\"6\">20601234567</cbc:ID>")
                .contains("CLIENTE &amp; CIA S.A.C.")
                .contains("Laptop &lt;15&quot;&gt;")
                .contains("<cbc:TaxableAmount currencyID=\"PEN\">2000.00</cbc:TaxableAmount>")
                .contains("<cbc:TaxAmount currencyID=\"PEN\">360.00</cbc:TaxAmount>")
                .contains("<cbc:TaxableAmount currencyID=\"PEN\">100.00</cbc:TaxableAmount>")
                .contains("<cbc:ID>9997</cbc:ID>")
                .contains("<cbc:PayableAmount currencyID=\"PEN\">2460.00</cbc:PayableAmount>")
                .contains("<ext:ExtensionContent/>")
                .contains("<cac:InvoiceLine>");
        assertThat(xml.split("<cac:InvoiceLine>")).hasSize(3);
    }

    @Test void esValidoContraXsd() {
        String xml = new FreemarkerUblGenerator().generar(factura(), tenant());
        new JaxpXsdValidator().validar(xml, TipoDocumento.FACTURA);   // no lanza
    }
}
```

`JaxpXsdValidatorTest.java`:
```java
package pe.factura.adapters.ubl;

import org.junit.jupiter.api.Test;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JaxpXsdValidatorTest {
    @Test void xmlInvalidoLanzaConDetalle() {
        String xml = "<?xml version=\"1.0\"?><Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"><Basura/></Invoice>";
        assertThatThrownBy(() -> new JaxpXsdValidator().validar(xml, TipoDocumento.FACTURA))
                .isInstanceOf(DomainException.class).hasMessageContaining("Basura").extracting("codigo").isEqualTo("XSD_INVALIDO");
    }
}
```

- [ ] **Step 3: Ver fallar** — `./gradlew :adapters:out-ubl:test` → FAIL de compilación.

- [ ] **Step 4: Plantilla**

`templates/invoice.ftl` (Freemarker con `output_format="XML"` → escapa `&`, `<`, `>`, `"` automáticamente):
```xml
<#ftl output_format="XML" strip_whitespace=true>
<#setting number_format="0.00">
<#setting locale="en_US">
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
         xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
         xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2">
  <ext:UBLExtensions>
    <ext:UBLExtension>
      <ext:ExtensionContent/>
    </ext:UBLExtension>
  </ext:UBLExtensions>
  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>
  <cbc:CustomizationID>2.0</cbc:CustomizationID>
  <cbc:ID>${c.serie()}-${c.numero()?c}</cbc:ID>
  <cbc:IssueDate>${fechaEmision}</cbc:IssueDate>
  <cbc:InvoiceTypeCode listID="${c.tipoOperacion()}" listAgencyName="PE:SUNAT" listName="Tipo de Documento" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01">${c.tipo().codigo()}</cbc:InvoiceTypeCode>
  <cbc:Note languageLocaleID="1000">${montoEnLetras}</cbc:Note>
  <cbc:DocumentCurrencyCode listID="ISO 4217 Alpha" listName="Currency" listAgencyName="United Nations Economic Commission for Europe">${c.moneda()}</cbc:DocumentCurrencyCode>
  <cac:Signature>
    <cbc:ID>signatureFACTURA</cbc:ID>
    <cac:SignatoryParty>
      <cac:PartyIdentification><cbc:ID>${t.ruc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyName><cbc:Name>${t.razonSocial()}</cbc:Name></cac:PartyName>
    </cac:SignatoryParty>
    <cac:DigitalSignatureAttachment>
      <cac:ExternalReference><cbc:URI>#signatureFACTURA</cbc:URI></cac:ExternalReference>
    </cac:DigitalSignatureAttachment>
  </cac:Signature>
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cac:PartyIdentification><cbc:ID schemeID="6" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${t.ruc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${t.razonSocial()}</cbc:RegistrationName>
        <cac:RegistrationAddress><cbc:AddressTypeCode>0000</cbc:AddressTypeCode></cac:RegistrationAddress>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty>
    <cac:Party>
      <cac:PartyIdentification><cbc:ID schemeID="${c.receptor().tipoDoc()}" schemeName="Documento de Identidad" schemeAgencyName="PE:SUNAT" schemeURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo06">${c.receptor().numDoc()}</cbc:ID></cac:PartyIdentification>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>${c.receptor().razonSocial()}</cbc:RegistrationName>
        <#if c.receptor().direccion()??>
        <cac:RegistrationAddress><cac:AddressLine><cbc:Line>${c.receptor().direccion()}</cbc:Line></cac:AddressLine></cac:RegistrationAddress>
        </#if>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingCustomerParty>
  <cac:PaymentTerms>
    <cbc:ID>FormaPago</cbc:ID>
    <cbc:PaymentMeansID>Contado</cbc:PaymentMeansID>
  </cac:PaymentTerms>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="${c.moneda()}">${tot.igv()}</cbc:TaxAmount>
    <#if (tot.gravado() > 0)>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="${c.moneda()}">${tot.gravado()}</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="${c.moneda()}">${tot.igv()}</cbc:TaxAmount>
      <cac:TaxCategory><cac:TaxScheme><cbc:ID schemeID="UN/ECE 5153" schemeAgencyID="6">1000</cbc:ID><cbc:Name>IGV</cbc:Name><cbc:TaxTypeCode>VAT</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory>
    </cac:TaxSubtotal>
    </#if>
    <#if (tot.exonerado() > 0)>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="${c.moneda()}">${tot.exonerado()}</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="${c.moneda()}">0.00</cbc:TaxAmount>
      <cac:TaxCategory><cac:TaxScheme><cbc:ID schemeID="UN/ECE 5153" schemeAgencyID="6">9997</cbc:ID><cbc:Name>EXO</cbc:Name><cbc:TaxTypeCode>VAT</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory>
    </cac:TaxSubtotal>
    </#if>
    <#if (tot.inafecto() > 0)>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="${c.moneda()}">${tot.inafecto()}</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="${c.moneda()}">0.00</cbc:TaxAmount>
      <cac:TaxCategory><cac:TaxScheme><cbc:ID schemeID="UN/ECE 5153" schemeAgencyID="6">9998</cbc:ID><cbc:Name>INA</cbc:Name><cbc:TaxTypeCode>FRE</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory>
    </cac:TaxSubtotal>
    </#if>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
    <cbc:LineExtensionAmount currencyID="${c.moneda()}">${tot.gravado() + tot.exonerado() + tot.inafecto()}</cbc:LineExtensionAmount>
    <cbc:TaxInclusiveAmount currencyID="${c.moneda()}">${tot.total()}</cbc:TaxInclusiveAmount>
    <cbc:PayableAmount currencyID="${c.moneda()}">${tot.total()}</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>
  <#list tot.items() as it>
  <cac:InvoiceLine>
    <cbc:ID>${it?index + 1}</cbc:ID>
    <cbc:InvoicedQuantity unitCode="${it.item().unidad()}" unitCodeListID="UN/ECE rec 20" unitCodeListAgencyName="United Nations Economic Commission for Europe">${it.item().cantidad()?string["0.####"]}</cbc:InvoicedQuantity>
    <cbc:LineExtensionAmount currencyID="${c.moneda()}">${it.valorVenta()}</cbc:LineExtensionAmount>
    <cac:PricingReference>
      <cac:AlternativeConditionPrice>
        <cbc:PriceAmount currencyID="${c.moneda()}">${it.item().precioUnitario()}</cbc:PriceAmount>
        <cbc:PriceTypeCode listName="Tipo de Precio" listAgencyName="PE:SUNAT" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16">01</cbc:PriceTypeCode>
      </cac:AlternativeConditionPrice>
    </cac:PricingReference>
    <cac:TaxTotal>
      <cbc:TaxAmount currencyID="${c.moneda()}">${it.igv()}</cbc:TaxAmount>
      <cac:TaxSubtotal>
        <cbc:TaxableAmount currencyID="${c.moneda()}">${it.valorVenta()}</cbc:TaxableAmount>
        <cbc:TaxAmount currencyID="${c.moneda()}">${it.igv()}</cbc:TaxAmount>
        <cac:TaxCategory>
          <cbc:Percent>${it.porcentajeIgv()}</cbc:Percent>
          <cbc:TaxExemptionReasonCode listAgencyName="PE:SUNAT" listName="Afectacion del IGV" listURI="urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07">${it.item().afectacion().codigo()}</cbc:TaxExemptionReasonCode>
          <cac:TaxScheme>
            <cbc:ID schemeID="UN/ECE 5153" schemeAgencyID="6">${it.item().afectacion().tributoId()}</cbc:ID>
            <cbc:Name>${it.item().afectacion().tributoNombre()}</cbc:Name>
            <cbc:TaxTypeCode>${it.item().afectacion().tributoTipo()}</cbc:TaxTypeCode>
          </cac:TaxScheme>
        </cac:TaxCategory>
      </cac:TaxSubtotal>
    </cac:TaxTotal>
    <cac:Item>
      <cbc:Description>${it.item().descripcion()}</cbc:Description>
      <#if it.item().codigo()??><cac:SellersItemIdentification><cbc:ID>${it.item().codigo()}</cbc:ID></cac:SellersItemIdentification></#if>
    </cac:Item>
    <cac:Price><cbc:PriceAmount currencyID="${c.moneda()}">${it.valorUnitario()?string["0.0000000000"]}</cbc:PriceAmount></cac:Price>
  </cac:InvoiceLine>
  </#list>
</Invoice>
```

- [ ] **Step 5: Generador y validador**

`FreemarkerUblGenerator.java`:
```java
package pe.factura.adapters.ubl;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import pe.factura.application.port.out.UblGenerator;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.MontoEnLetras;
import pe.factura.domain.tenant.Tenant;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class FreemarkerUblGenerator implements UblGenerator {
    private final Configuration cfg;

    public FreemarkerUblGenerator() {
        cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
    }

    @Override public String generar(Comprobante c, Tenant t) {
        try {
            Template tpl = cfg.getTemplate("invoice.ftl");
            Map<String, Object> modelo = new HashMap<>();
            modelo.put("c", c);
            modelo.put("t", t);
            modelo.put("tot", c.totales());
            modelo.put("fechaEmision", c.fechaEmision().toString());
            modelo.put("montoEnLetras", MontoEnLetras.de(c.totales().total(), c.moneda()));
            StringWriter out = new StringWriter();
            tpl.process(modelo, out);
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Error generando UBL", e); }
    }
}
```

`JaxpXsdValidator.java`:
```java
package pe.factura.adapters.ubl;

import org.xml.sax.SAXParseException;
import pe.factura.application.port.out.XsdValidator;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.TipoDocumento;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

public class JaxpXsdValidator implements XsdValidator {
    private final Map<TipoDocumento, Schema> esquemas = new EnumMap<>(TipoDocumento.class);

    public JaxpXsdValidator() {
        esquemas.put(TipoDocumento.FACTURA, cargar("xsd/2.1/maindoc/UBL-Invoice-2.1.xsd"));
        esquemas.put(TipoDocumento.BOLETA, esquemas.get(TipoDocumento.FACTURA));
    }

    private Schema cargar(String recurso) {
        try {
            URL url = getClass().getClassLoader().getResource(recurso);
            if (url == null) throw new IllegalStateException("No se encontró " + recurso);
            SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            return f.newSchema(url);   // los imports relativos (../common/*.xsd) se resuelven contra la URL
        } catch (Exception e) { throw new IllegalStateException("No se pudo cargar el XSD " + recurso, e); }
    }

    @Override public void validar(String xml, TipoDocumento tipo) {
        Schema s = esquemas.get(tipo);
        if (s == null) throw new IllegalArgumentException("Sin XSD para " + tipo);
        try {
            Validator v = s.newValidator();
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            v.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            throw new DomainException("XSD_INVALIDO", "XML inválido (línea " + e.getLineNumber() + "): " + e.getMessage());
        } catch (Exception e) {
            throw new DomainException("XSD_INVALIDO", "XML inválido: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 6: Ver pasar** — `./gradlew :adapters:out-ubl:test` → PASS. Si `esValidoContraXsd` falla por orden de elementos, ajustar la plantilla siguiendo el mensaje del validador (el orden canónico está en `UBL-Invoice-2.1.xsd`).

- [ ] **Step 7: Commit** — `git add adapters/out-ubl && git commit -m "feat(ubl): plantilla Invoice UBL 2.1 y validación contra XSD oficiales SUNAT"`

---

### Task 13: Firmador XML-DSig

**Files:**
- Create: `adapters/out-signing/src/main/java/pe/factura/adapters/signing/XmlDsigSigner.java`
- Create: `adapters/out-signing/src/test/resources/test-cert.p12` (generado con `keytool`)
- Test: `adapters/out-signing/src/test/java/pe/factura/adapters/signing/XmlDsigSignerTest.java`

**Interfaces:**
- Produces: `new XmlDsigSigner()` (RSA-SHA256 / SHA-256, configurable con `new XmlDsigSigner(true)` para SHA-1) implementa `XmlSigner`. Coloca `<ds:Signature Id="signatureFACTURA">` dentro del `ext:ExtensionContent` vacío. `hash` = `DigestValue` de la única `Reference`.

- [ ] **Step 1: Certificado de pruebas**

```bash
mkdir -p adapters/out-signing/src/test/resources
keytool -genkeypair -alias factura -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=EMPRESA DE PRUEBA S.A.C., OU=20100066603, O=EMPRESA DE PRUEBA S.A.C., L=LIMA, ST=LIMA, C=PE" \
  -storetype PKCS12 -keystore adapters/out-signing/src/test/resources/test-cert.p12 -storepass test1234 -keypass test1234
```

- [ ] **Step 2: Test**

```java
package pe.factura.adapters.signing;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.domain.tenant.CertificadoDigital;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class XmlDsigSignerTest {
    static final String XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                 xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                 xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2">
          <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent/></ext:UBLExtension></ext:UBLExtensions>
          <cbc:ID>F001-1</cbc:ID>
        </Invoice>
        """;

    static CertificadoDigital cert() throws Exception {
        byte[] p12 = XmlDsigSignerTest.class.getResourceAsStream("/test-cert.p12").readAllBytes();
        return new CertificadoDigital(p12, "test1234", LocalDate.of(2036, 1, 1));
    }

    @Test void firmaDentroDeExtensionContentYDevuelveHash() throws Exception {
        FirmaResultado r = new XmlDsigSigner().firmar(XML, cert());
        assertThat(r.xmlFirmado()).contains("<ext:ExtensionContent><ds:Signature").contains("Id=\"signatureFACTURA\"")
                .contains("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256").contains("<ds:X509Certificate>");
        assertThat(r.hash()).isNotBlank().hasSizeGreaterThan(20);
        assertThat(r.xmlFirmado()).contains("<ds:DigestValue>" + r.hash() + "</ds:DigestValue>");
    }

    @Test void laFirmaEsVerificable() throws Exception {
        FirmaResultado r = new XmlDsigSigner().firmar(XML, cert());
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance(); dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(r.xmlFirmado().getBytes(StandardCharsets.UTF_8)));
        NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        KeyStore ks = KeyStore.getInstance("PKCS12"); ks.load(new ByteArrayInputStream(cert().pkcs12()), "test1234".toCharArray());
        DOMValidateContext ctx = new DOMValidateContext(ks.getCertificate(ks.aliases().nextElement()).getPublicKey(), nl.item(0));
        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx).validate(ctx)).isTrue();
    }

    @Test void alterarElDocumentoInvalidaLaFirma() throws Exception {
        FirmaResultado r = new XmlDsigSigner().firmar(XML, cert());
        String alterado = r.xmlFirmado().replace("F001-1", "F001-2");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance(); dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(alterado.getBytes(StandardCharsets.UTF_8)));
        NodeList nl = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        KeyStore ks = KeyStore.getInstance("PKCS12"); ks.load(new ByteArrayInputStream(cert().pkcs12()), "test1234".toCharArray());
        DOMValidateContext ctx = new DOMValidateContext(ks.getCertificate(ks.aliases().nextElement()).getPublicKey(), nl.item(0));
        assertThat(XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx).validate(ctx)).isFalse();
    }
}
```

- [ ] **Step 3: Ver fallar** — `./gradlew :adapters:out-signing:test` → FAIL de compilación.

- [ ] **Step 4: Implementación**

```java
package pe.factura.adapters.signing;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import pe.factura.application.port.out.FirmaResultado;
import pe.factura.application.port.out.XmlSigner;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.CertificadoDigital;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

public class XmlDsigSigner implements XmlSigner {
    private static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private final boolean sha1;

    public XmlDsigSigner() { this(false); }
    /** sha1=true replica los ejemplos históricos de SUNAT (rsa-sha1); por defecto rsa-sha256. */
    public XmlDsigSigner(boolean sha1) { this.sha1 = sha1; }

    @Override public FirmaResultado firmar(String xml, CertificadoDigital cert) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(cert.pkcs12()), cert.clave().toCharArray());
            String alias = ks.aliases().nextElement();
            PrivateKey key = (PrivateKey) ks.getKey(alias, cert.clave().toCharArray());
            X509Certificate x509 = (X509Certificate) ks.getCertificate(alias);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList contents = doc.getElementsByTagNameNS(NS_EXT, "ExtensionContent");
            if (contents.getLength() == 0) throw new DomainException("XML_SIN_EXTENSION", "El XML no tiene ext:ExtensionContent para la firma");
            Element contenedor = (Element) contents.item(contents.getLength() - 1);

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            String digestAlg = sha1 ? DigestMethod.SHA1 : DigestMethod.SHA256;
            String signAlg = sha1 ? SignatureMethod.RSA_SHA1 : SignatureMethod.RSA_SHA256;
            Reference ref = fac.newReference("", fac.newDigestMethod(digestAlg, null),
                    List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null, null);
            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(signAlg, null), List.of(ref));
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            KeyInfo ki = kif.newKeyInfo(List.of(kif.newX509Data(List.of(x509))));

            DOMSignContext ctx = new DOMSignContext(key, contenedor);
            ctx.setDefaultNamespacePrefix("ds");
            XMLSignature firma = fac.newXMLSignature(si, ki, null, "signatureFACTURA", null);
            firma.sign(ctx);

            String hash = Base64.getEncoder().encodeToString(ref.getDigestValue());
            return new FirmaResultado(serializar(doc), hash);
        } catch (DomainException e) { throw e;
        } catch (Exception e) { throw new DomainException("FIRMA_FALLIDA", "No se pudo firmar el XML: " + e.getMessage()); }
    }

    private static String serializar(Document doc) throws Exception {
        var tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tf.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter out = new StringWriter();
        tf.transform(new DOMSource(doc), new StreamResult(out));
        return out.toString();
    }
}
```

- [ ] **Step 5: Ver pasar** — `./gradlew :adapters:out-signing:test` → PASS.

- [ ] **Step 6: Commit** — `git add adapters/out-signing && git commit -m "feat(signing): firma XML-DSig enveloped en ExtensionContent con hash del documento"`
