package pe.factura.bootstrap;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.adapters.scheduler.OutboxWorker;
import pe.factura.adapters.sunat.ZipUtil;

import java.util.Base64;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@WireMockTest(httpPort = 18089)
class FacturaE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("app.sunat.beta-url", () -> "http://localhost:18089/billService");
        r.add("app.sunat.timeout-seconds", () -> "2");
    }

    @Autowired TestRestTemplate http;

    static final String CDR_OK = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
          xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
          xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
          <cbc:ID>x</cbc:ID>
          <cac:DocumentResponse><cac:Response><cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>La Factura numero F001-1, ha sido aceptada</cbc:Description></cac:Response></cac:DocumentResponse>
        </ar:ApplicationResponse>""";

    private static String soapOk(String nombre) {
        byte[] zip = ZipUtil.comprimir("R-" + nombre + ".xml", CDR_OK.getBytes());
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendBillResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><applicationResponse>"
                + Base64.getEncoder().encodeToString(zip) + "</applicationResponse></ns2:sendBillResponse></soap-env:Body></soap-env:Envelope>";
    }

    private String provisionarTenant() throws Exception {
        HttpHeaders admin = new HttpHeaders(); admin.set("X-Platform-Key", "plataforma-test"); admin.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> creado = http.postForEntity("/v1/admin/tenants",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", admin), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String apiKey = (String) ((Map<?, ?>) creado.getBody().get("datos")).get("api_key");

        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.exchange("/v1/empresa/credenciales-sol", HttpMethod.PUT, new HttpEntity<>("{\"usuario\":\"MODDATOS\",\"clave\":\"moddatos\"}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"01\",\"serie\":\"F001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        byte[] p12 = getClass().getResourceAsStream("/test-cert.p12").readAllBytes();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("archivo", new ByteArrayResource(p12) { @Override public String getFilename() { return "cert.p12"; } });
        form.add("clave", "test1234");
        HttpHeaders mh = new HttpHeaders(); mh.set("X-Api-Key", apiKey); mh.setContentType(MediaType.MULTIPART_FORM_DATA);
        assertThat(http.postForEntity("/v1/empresa/certificado", new HttpEntity<>(form, mh), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return apiKey;
    }

    static final String FACTURA = """
        {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN",
         "cliente":{"tipo_doc":"6","num_doc":"20601234567","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
         "items":[{"codigo":"P001","descripcion":"Laptop","unidad":"NIU","cantidad":1,"precio_unitario":2360.00,"tipo_afectacion_igv":"10"}]}
        """.formatted(java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")));

    @Test void facturaAceptadaDeExtremoAExtremo(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        assertThat(datos.get("estado_documento")).isEqualTo("ACEPTADO");
        assertThat(datos.get("numero")).isEqualTo(1);
        assertThat((String) datos.get("hash")).isNotBlank();
        assertThat(((Map<?, ?>) datos.get("cdr")).get("codigo")).isEqualTo("0");

        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("<fileName>20100066603-01-F001-1.zip</fileName>")));

        String id = (String) datos.get("id");
        ResponseEntity<byte[]> xml = http.exchange("/v1/facturas/" + id + "/xml", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(xml.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(xml.getBody())).contains("<ds:Signature").contains("<cbc:ID>F001-1</cbc:ID>");
        ResponseEntity<byte[]> cdr = http.exchange("/v1/facturas/" + id + "/cdr", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(cdr.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(ZipUtil.extraerPrimero(cdr.getBody(), ".xml"))).contains("ha sido aceptada");
    }

    @Autowired OutboxWorker worker;

    @Test void sunatCaidoDejaErrorEnvioYElOutboxReintenta() throws Exception {
        stubFor(post("/billService").inScenario("caida").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recuperado"));
        stubFor(post("/billService").inScenario("caida").whenScenarioStateIs("recuperado")
                .willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        assertThat(datos.get("estado_documento")).isEqualTo("ERROR_ENVIO");
        assertThat(datos.get("intentos")).isEqualTo(1);

        // El outbox programó el reintento para dentro de 2 min; lo adelantamos y ejecutamos el worker
        jdbcAdelantarOutbox();
        assertThat(worker.procesar()).isEqualTo(1);

        ResponseEntity<Map> despues = http.exchange("/v1/facturas/" + datos.get("id"), HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(((Map<?, ?>) despues.getBody().get("datos")).get("estado_documento")).isEqualTo("ACEPTADO");
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private void jdbcAdelantarOutbox() { jdbc.update("UPDATE outbox SET siguiente_intento = now() - interval '1 second'"); }

    @Test void sinApiKeyEs401() {
        ResponseEntity<String> r = http.getForEntity("/v1/facturas", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void adminSinClaveDePlataformaEs401() {
        HttpHeaders sinClave = new HttpHeaders(); sinClave.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> sinHeader = http.postForEntity("/v1/admin/tenants",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", sinClave), String.class);
        assertThat(sinHeader.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders claveErronea = new HttpHeaders(); claveErronea.set("X-Platform-Key", "clave-incorrecta"); claveErronea.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> conClaveErronea = http.postForEntity("/v1/admin/tenants",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", claveErronea), String.class);
        assertThat(conClaveErronea.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @org.junit.jupiter.api.BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant CASCADE");
    }
}
