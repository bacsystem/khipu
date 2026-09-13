# 06 · Cliente SOAP SUNAT (`sendBill`) y CDR

Índice del plan: [README](README.md). Módulo `adapters/out-sunat-soap`. Referencia: `docs/sunat/notes.md` §2, §3, §7 y `docs/sunat/ref/manual_programador_see_contribuyente_v2.1.pdf` §2.4–2.6.

### Task 14: `ZipUtil` y `XmlCdrParser`

**Files:**
- Create: `adapters/out-sunat-soap/src/main/java/pe/factura/adapters/sunat/ZipUtil.java`, `XmlCdrParser.java`
- Create: `adapters/out-sunat-soap/src/test/resources/cdr/R-aceptado.xml`, `R-observado.xml`, `R-rechazado.xml`
- Test: `adapters/out-sunat-soap/src/test/java/pe/factura/adapters/sunat/ZipUtilTest.java`, `XmlCdrParserTest.java`

**Interfaces:**
- Produces: `ZipUtil.comprimir(String nombreEntrada, byte[] contenido) -> byte[]` (ZIP con un único archivo); `ZipUtil.extraerPrimero(byte[] zip, String sufijo) -> byte[]` (primer archivo cuyo nombre termina en `sufijo`, ignorando carpetas como `dummy/`); `new XmlCdrParser()` implementa `CdrParser`.

- [ ] **Step 1: Fixtures de CDR** (estructura real `ApplicationResponse` UBL 2.0 de SUNAT, recortada a lo relevante)

`R-aceptado.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
    xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
    xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
  <cbc:UBLVersionID>2.0</cbc:UBLVersionID>
  <cbc:CustomizationID>1.0</cbc:CustomizationID>
  <cbc:ID>20100066603-01-F001-1</cbc:ID>
  <cbc:IssueDate>2026-09-13</cbc:IssueDate>
  <cbc:IssueTime>10:15:00</cbc:IssueTime>
  <cbc:ResponseDate>2026-09-13</cbc:ResponseDate>
  <cbc:ResponseTime>10:15:01</cbc:ResponseTime>
  <cac:SenderParty><cac:PartyIdentification><cbc:ID>20131312955</cbc:ID></cac:PartyIdentification></cac:SenderParty>
  <cac:ReceiverParty><cac:PartyIdentification><cbc:ID>20100066603</cbc:ID></cac:PartyIdentification></cac:ReceiverParty>
  <cac:DocumentResponse>
    <cac:Response>
      <cbc:ReferenceID>F001-1</cbc:ReferenceID>
      <cbc:ResponseCode>0</cbc:ResponseCode>
      <cbc:Description>La Factura numero F001-1, ha sido aceptada</cbc:Description>
    </cac:Response>
    <cac:DocumentReference><cbc:ID>F001-1</cbc:ID></cac:DocumentReference>
    <cac:RecipientParty><cac:PartyIdentification><cbc:ID>20100066603</cbc:ID></cac:PartyIdentification></cac:RecipientParty>
  </cac:DocumentResponse>
</ar:ApplicationResponse>
```

`R-observado.xml`: igual que el anterior pero con dos notas antes de `cac:SenderParty`:
```xml
  <cbc:Note>4252 - El dato ingresado como atributo @listName es incorrecto.</cbc:Note>
  <cbc:Note>4255 - El campo no cumple con el formato establecido.</cbc:Note>
```

`R-rechazado.xml`: igual que el aceptado pero con:
```xml
      <cbc:ResponseCode>2324</cbc:ResponseCode>
      <cbc:Description>El comprobante fue registrado previamente con otros datos</cbc:Description>
```

- [ ] **Step 2: Tests**

`ZipUtilTest.java`:
```java
package pe.factura.adapters.sunat;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.*;

class ZipUtilTest {
    @Test void comprimeConUnaSolaEntrada() throws Exception {
        byte[] zip = ZipUtil.comprimir("20100066603-01-F001-1.xml", "<x/>".getBytes());
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e = in.getNextEntry();
            assertThat(e.getName()).isEqualTo("20100066603-01-F001-1.xml");
            assertThat(new String(in.readAllBytes())).isEqualTo("<x/>");
            assertThat(in.getNextEntry()).isNull();
        }
    }
    @Test void extraeIgnorandoCarpetas() {
        byte[] zip = ZipUtil.comprimir("R-20100066603-01-F001-1.xml", "<cdr/>".getBytes());
        assertThat(new String(ZipUtil.extraerPrimero(zip, ".xml"))).isEqualTo("<cdr/>");
        assertThatThrownBy(() -> ZipUtil.extraerPrimero(zip, ".pdf")).isInstanceOf(IllegalStateException.class);
    }
}
```

`XmlCdrParserTest.java`:
```java
package pe.factura.adapters.sunat;

import org.junit.jupiter.api.Test;
import pe.factura.domain.documento.Cdr;
import static org.assertj.core.api.Assertions.assertThat;

class XmlCdrParserTest {
    private byte[] zipDe(String recurso) throws Exception {
        byte[] xml = getClass().getResourceAsStream("/cdr/" + recurso).readAllBytes();
        return ZipUtil.comprimir(recurso, xml);
    }
    @Test void aceptado() throws Exception {
        Cdr c = new XmlCdrParser().parsear(zipDe("R-aceptado.xml"));
        assertThat(c.codigo()).isEqualTo("0");
        assertThat(c.descripcion()).contains("ha sido aceptada");
        assertThat(c.observaciones()).isEmpty();
        assertThat(c.esAceptado()).isTrue();
    }
    @Test void observado() throws Exception {
        Cdr c = new XmlCdrParser().parsear(zipDe("R-observado.xml"));
        assertThat(c.codigo()).isEqualTo("0");
        assertThat(c.observaciones()).containsExactly(
                "4252 - El dato ingresado como atributo @listName es incorrecto.",
                "4255 - El campo no cumple con el formato establecido.");
    }
    @Test void rechazado() throws Exception {
        Cdr c = new XmlCdrParser().parsear(zipDe("R-rechazado.xml"));
        assertThat(c.codigo()).isEqualTo("2324");
        assertThat(c.esRechazo()).isTrue();
    }
}
```

- [ ] **Step 3: Ver fallar** — `./gradlew :adapters:out-sunat-soap:test` → FAIL de compilación.

- [ ] **Step 4: Implementación**

`ZipUtil.java`:
```java
package pe.factura.adapters.sunat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ZipUtil {
    private ZipUtil() {}

    public static byte[] comprimir(String nombreEntrada, byte[] contenido) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bos)) {
            zip.putNextEntry(new ZipEntry(nombreEntrada));
            zip.write(contenido);
            zip.closeEntry();
            zip.finish();
            return bos.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("No se pudo comprimir " + nombreEntrada, e); }
    }

    public static byte[] extraerPrimero(byte[] zip, String sufijo) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().toLowerCase().endsWith(sufijo.toLowerCase())) return in.readAllBytes();
            }
        } catch (IOException e) { throw new IllegalStateException("ZIP corrupto", e); }
        throw new IllegalStateException("El ZIP no contiene un archivo " + sufijo);
    }
}
```

`XmlCdrParser.java`:
```java
package pe.factura.adapters.sunat;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import pe.factura.application.port.out.CdrParser;
import pe.factura.domain.documento.Cdr;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public class XmlCdrParser implements CdrParser {
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    @Override public Cdr parsear(byte[] cdrZip) {
        byte[] xml = ZipUtil.extraerPrimero(cdrZip, ".xml");
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

            NodeList responses = doc.getElementsByTagNameNS(CAC, "Response");
            if (responses.getLength() == 0) throw new IllegalStateException("CDR sin cac:Response");
            org.w3c.dom.Element resp = (org.w3c.dom.Element) responses.item(0);
            String codigo = texto(resp, "ResponseCode");
            String descripcion = texto(resp, "Description");

            List<String> notas = new ArrayList<>();
            NodeList notes = doc.getDocumentElement().getElementsByTagNameNS(CBC, "Note");
            for (int i = 0; i < notes.getLength(); i++)
                if (notes.item(i).getParentNode() == doc.getDocumentElement()) notas.add(notes.item(i).getTextContent().trim());

            return new Cdr(codigo, descripcion, List.copyOf(notas));
        } catch (IllegalStateException e) { throw e;
        } catch (Exception e) { throw new IllegalStateException("CDR ilegible: " + e.getMessage(), e); }
    }

    private static String texto(org.w3c.dom.Element padre, String local) {
        NodeList nl = padre.getElementsByTagNameNS(CBC, local);
        return nl.getLength() == 0 ? "" : nl.item(0).getTextContent().trim();
    }
}
```

- [ ] **Step 5: Ver pasar** — `./gradlew :adapters:out-sunat-soap:test` → PASS.
- [ ] **Step 6: Commit** — `git add adapters/out-sunat-soap && git commit -m "feat(sunat): utilidades ZIP y parser de CDR ApplicationResponse"`

---

### Task 15: `SoapBillingGateway` (`sendBill`) con WireMock

**Files:**
- Create: `adapters/out-sunat-soap/src/main/java/pe/factura/adapters/sunat/SunatUrls.java`, `SoapEnvelope.java`, `SoapBillingGateway.java`
- Test: `adapters/out-sunat-soap/src/test/java/pe/factura/adapters/sunat/SoapBillingGatewayTest.java`

**Interfaces:**
- Produces: `record SunatUrls(String beta, String produccion) { String para(Entorno) }`; `new SoapBillingGateway(SunatUrls urls, Duration timeout)` implementa `SunatBillingGateway`. Overload de prueba: `new SoapBillingGateway(SunatUrls, Duration, HttpClient)`.
- Mapeo de errores: HTTP 5xx / timeout / IOException → `SunatTransientException("0000"|"0109", …)`; SOAPFault con código `< 2000` → `SunatTransientException(codigo, faultstring)`; SOAPFault con código `>= 2000` → `SunatRechazoException(codigo, faultstring)`; respuesta sin `applicationResponse` → `SunatTransientException("0000", "respuesta inesperada")`.

- [ ] **Step 1: Test**

```java
package pe.factura.adapters.sunat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.tenant.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@WireMockTest
class SoapBillingGatewayTest {
    Tenant tenant = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA,
            new CredencialesSol("MODDATOS", "moddatos"), new CertificadoDigital(new byte[0], "", LocalDate.of(2030, 1, 1)));

    private SoapBillingGateway gateway(WireMockRuntimeInfo wm) {
        return new SoapBillingGateway(new SunatUrls(wm.getHttpBaseUrl() + "/billService", wm.getHttpBaseUrl() + "/prod"), Duration.ofSeconds(2));
    }

    private static String respuestaOk(byte[] cdrZip) {
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body>"
                + "<ns2:sendBillResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><applicationResponse>"
                + Base64.getEncoder().encodeToString(cdrZip) + "</applicationResponse></ns2:sendBillResponse></soap-env:Body></soap-env:Envelope>";
    }
    private static String fault(String code, String msg) {
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><soap-env:Fault>"
                + "<faultcode>soap-env:Client." + code + "</faultcode><faultstring>" + msg + "</faultstring></soap-env:Fault></soap-env:Body></soap-env:Envelope>";
    }

    @Test void enviaZipConWsSecurityYDevuelveCdr(WireMockRuntimeInfo wm) {
        byte[] cdr = ZipUtil.comprimir("R-20100066603-01-F001-1.xml", "<cdr/>".getBytes());
        stubFor(post("/billService").willReturn(okXml(respuestaOk(cdr))));

        byte[] r = gateway(wm).sendBill(tenant, "20100066603-01-F001-1", "<Invoice/>".getBytes());

        assertThat(r).isEqualTo(cdr);
        verify(postRequestedFor(urlEqualTo("/billService"))
                .withHeader("Content-Type", containing("text/xml"))
                .withRequestBody(containing("<wsse:Username>20100066603MODDATOS</wsse:Username>"))
                .withRequestBody(containing("<wsse:Password>moddatos</wsse:Password>"))
                .withRequestBody(containing("<fileName>20100066603-01-F001-1.zip</fileName>"))
                .withRequestBody(matching("(?s).*<contentFile>[A-Za-z0-9+/=]+</contentFile>.*")));
    }

    @Test void faultMenorA2000EsTransitorio(WireMockRuntimeInfo wm) {
        stubFor(post("/billService").willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml").withBody(fault("0109", "El sistema no puede responder en este momento"))));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0]))
                .isInstanceOf(SunatTransientException.class).hasMessageContaining("no puede responder")
                .extracting("codigo").isEqualTo("0109");
    }

    @Test void faultMayorOIgualA2000EsRechazo(WireMockRuntimeInfo wm) {
        stubFor(post("/billService").willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml").withBody(fault("2324", "registrado previamente"))));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0]))
                .isInstanceOf(SunatRechazoException.class).extracting("codigo").isEqualTo("2324");
    }

    @Test void faultCodeSinPrefijoTambienSeParsea(WireMockRuntimeInfo wm) {
        String f = fault("x", "y").replace("soap-env:Client.x", "1033").replace("y", "ya fue registrado");
        stubFor(post("/billService").willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml").withBody(f)));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0])).extracting("codigo").isEqualTo("1033");
    }

    @Test void timeoutEsTransitorio(WireMockRuntimeInfo wm) {
        stubFor(post("/billService").willReturn(okXml("<x/>").withFixedDelay(3000)));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0])).isInstanceOf(SunatTransientException.class);
    }

    @Test void http503EsTransitorio(WireMockRuntimeInfo wm) {
        stubFor(post("/billService").willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0])).isInstanceOf(SunatTransientException.class);
    }

    @Test void urlPorEntorno() {
        SunatUrls u = new SunatUrls("http://beta", "http://prod");
        assertThat(u.para(Entorno.BETA)).isEqualTo("http://beta");
        assertThat(u.para(Entorno.PRODUCCION)).isEqualTo("http://prod");
    }
}
```

- [ ] **Step 2: Ver fallar** — `./gradlew :adapters:out-sunat-soap:test --tests '*SoapBillingGatewayTest*'` → FAIL de compilación.

- [ ] **Step 3: Implementación**

`SunatUrls.java`:
```java
package pe.factura.adapters.sunat;

import pe.factura.domain.tenant.Entorno;

public record SunatUrls(String beta, String produccion) {
    public String para(Entorno e) { return e == Entorno.PRODUCCION ? produccion : beta; }
}
```

`SoapEnvelope.java`:
```java
package pe.factura.adapters.sunat;

import java.util.Base64;

final class SoapEnvelope {
    private SoapEnvelope() {}

    static String sendBill(String usuario, String clave, String nombreZip, byte[] zip) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://service.sunat.gob.pe" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <soapenv:Header><wsse:Security><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password></wsse:UsernameToken></wsse:Security></soapenv:Header>
            <soapenv:Body><ser:sendBill><fileName>%s</fileName><contentFile>%s</contentFile></ser:sendBill></soapenv:Body>
            </soapenv:Envelope>""".formatted(esc(usuario), esc(clave), esc(nombreZip), Base64.getEncoder().encodeToString(zip));
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Extrae el texto del primer elemento con ese nombre local (sin importar prefijo). Devuelve null si no existe. */
    static String textoDe(String xml, String nombreLocal) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(?:[\\w-]+:)?" + nombreLocal + "[^>]*>(.*?)</(?:[\\w-]+:)?" + nombreLocal + ">", java.util.regex.Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    /** "soap-env:Client.1033" → "1033"; "1033" → "1033"; "soap-env:Server" → "0000". */
    static String codigoDeFault(String faultcode) {
        if (faultcode == null) return "0000";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})\\s*$").matcher(faultcode.trim());
        return m.find() ? m.group(1) : "0000";
    }
}
```

`SoapBillingGateway.java`:
```java
package pe.factura.adapters.sunat;

import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.tenant.Tenant;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public class SoapBillingGateway implements SunatBillingGateway {
    private final SunatUrls urls;
    private final Duration timeout;
    private final HttpClient http;

    public SoapBillingGateway(SunatUrls urls, Duration timeout) {
        this(urls, timeout, HttpClient.newBuilder().connectTimeout(timeout).build());
    }
    public SoapBillingGateway(SunatUrls urls, Duration timeout, HttpClient http) { this.urls = urls; this.timeout = timeout; this.http = http; }

    @Override public byte[] sendBill(Tenant tenant, String nombreArchivo, byte[] xmlFirmado) {
        byte[] zip = ZipUtil.comprimir(nombreArchivo + ".xml", xmlFirmado);
        String nombreZip = nombreArchivo + ".zip";
        String cuerpo = SoapEnvelope.sendBill(tenant.sol().usernameToken(tenant.ruc()), tenant.sol().clave(), nombreZip, zip);

        HttpRequest req = HttpRequest.newBuilder(URI.create(urls.para(tenant.entorno())))
                .timeout(timeout)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "urn:sendBill")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new SunatTransientException("0109", "Tiempo de espera agotado llamando a SUNAT", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new SunatTransientException("0000", "Error de red llamando a SUNAT: " + e.getMessage(), e);
        }

        String body = resp.body() == null ? "" : resp.body();
        String faultcode = SoapEnvelope.textoDe(body, "faultcode");
        if (faultcode != null) {
            String codigo = SoapEnvelope.codigoDeFault(faultcode);
            String msg = SoapEnvelope.textoDe(body, "faultstring");
            if (Integer.parseInt(codigo) >= 2000) throw new SunatRechazoException(codigo, msg == null ? "" : msg);
            throw new SunatTransientException(codigo, msg == null ? "SOAPFault " + faultcode : msg);
        }
        if (resp.statusCode() >= 500) throw new SunatTransientException("0000", "SUNAT respondió HTTP " + resp.statusCode());
        if (resp.statusCode() >= 400) throw new SunatTransientException("0000", "SUNAT respondió HTTP " + resp.statusCode() + " (revisar credenciales/URL)");

        String b64 = SoapEnvelope.textoDe(body, "applicationResponse");
        if (b64 == null || b64.isBlank()) throw new SunatTransientException("0000", "Respuesta inesperada de SUNAT sin applicationResponse");
        return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
    }
}
```

- [ ] **Step 4: Ver pasar** — `./gradlew :adapters:out-sunat-soap:test` → PASS.

- [ ] **Step 5: Commit** — `git add adapters/out-sunat-soap && git commit -m "feat(sunat): cliente SOAP sendBill con WS-Security y mapeo de faults"`
