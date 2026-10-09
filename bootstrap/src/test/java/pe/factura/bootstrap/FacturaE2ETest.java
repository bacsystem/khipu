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

import java.net.URI;
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

    static String soapOk(String nombre) {
        byte[] zip = ZipUtil.comprimir("R-" + nombre + ".xml", CDR_OK.getBytes());
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendBillResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><applicationResponse>"
                + Base64.getEncoder().encodeToString(zip) + "</applicationResponse></ns2:sendBillResponse></soap-env:Body></soap-env:Envelope>";
    }

    String provisionarTenant() throws Exception { return EmpresaDePrueba.provisionar(http); }

    static final String FACTURA = """
        {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN",
         "cliente":{"tipo_doc":"6","num_doc":"20601234565","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
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

    /**
     * #20: una boleta pasa por el mismo camino que la factura (numeración de su serie B, UBL, firma real, XSD, sendBill) y se envía sola. Un comprador sin
     * documento por encima de S/ 700 no se emite ni consume número.
     */
    @Test void boletaAceptadaDeExtremoAExtremo(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-03-B001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"03\",\"serie\":\"B001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String boleta = """
            {"serie":"B001","fecha_emision":"%s","moneda":"PEN",
             "cliente":{"tipo_doc":"-","num_doc":"-","razon_social":"CLIENTES VARIOS"},
             "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":%s,"precio_unitario":0.50,"tipo_afectacion_igv":"10"}]}
            """;
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();

        ResponseEntity<Map> caro = http.postForEntity("/v1/facturas", new HttpEntity<>(boleta.formatted(hoy, "1401"), h), Map.class);
        assertThat(caro.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(caro.getBody().get("codigo")).isEqualTo("RECEPTOR_INVALIDO");

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(boleta.formatted(hoy, "10"), h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        assertThat(datos.get("tipo")).isEqualTo("03");
        assertThat(datos.get("numero")).isEqualTo(1);
        assertThat(datos.get("estado_documento")).isEqualTo("ACEPTADO");
        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("<fileName>20100066603-03-B001-1.zip</fileName>")));

        String id = (String) datos.get("id");
        String xml = new String(http.exchange("/v1/facturas/" + id + "/xml", HttpMethod.GET, new HttpEntity<>(h), byte[].class).getBody());
        assertThat(xml).contains("<ds:Signature", "<cbc:ID>B001-1</cbc:ID>", ">03</cbc:InvoiceTypeCode>", "schemeID=\"-\"").doesNotContain("FormaPago");
        ResponseEntity<byte[]> pdf = http.exchange("/v1/facturas/" + id + "/pdf", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(pdf.getBody(), 0, 5)).isEqualTo("%PDF-");

        // Anularla: un resumen diario (RC) firmado de verdad y validado contra el XSD oficial, por sendSummary + getStatus; la boleta queda ANULADA.
        stubFor(post("/billService").withRequestBody(containing("sendSummary")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendSummaryResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><ticket>1789768174685</ticket></ns2:sendSummaryResponse></soap-env:Body></soap-env:Envelope>")));
        byte[] cdrRc = ZipUtil.comprimir("R-20100066603-RC.xml", CDR_OK.getBytes());
        stubFor(post("/billService").withRequestBody(containing("getStatus")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><status><statusCode>0</statusCode><content>"
                        + Base64.getEncoder().encodeToString(cdrRc) + "</content></status></ns2:getStatusResponse></soap-env:Body></soap-env:Envelope>")));
        ResponseEntity<Map> baja = http.postForEntity("/v1/facturas/" + id + "/baja", new HttpEntity<>("{\"motivo\":\"Se cobró dos veces\"}", h), Map.class);
        assertThat(baja.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> rc = (Map<?, ?>) baja.getBody().get("datos");
        assertThat((String) rc.get("identificador")).matches("RC-\\d{8}-1");
        assertThat(rc.get("estado")).isEqualTo("ACEPTADA");
        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("sendSummary")).withRequestBody(matching("(?s).*<fileName>20100066603-RC-\\d{8}-1\\.zip</fileName>.*")));
        Map<?, ?> anulada = (Map<?, ?>) http.exchange("/v1/facturas/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat(anulada.get("estado_documento")).isEqualTo("ANULADO");
    }

    /**
     * 274-H1: una boleta firmada hace 5 días y nunca enviada ya no va sola con sendBill (1079): al enviarla, khipu la informa en un resumen diario con su
     * línea en estado 1, firmado de verdad y validado contra el XSD, y al aceptarse la boleta queda ACEPTADO.
     */
    @Test void boletaPasadaDelEnvioIndividualSeInformaEnElResumenDiario(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").withRequestBody(containing("sendSummary")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendSummaryResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><ticket>1789768174686</ticket></ns2:sendSummaryResponse></soap-env:Body></soap-env:Envelope>")));
        stubFor(post("/billService").withRequestBody(containing("getStatus")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><status><statusCode>0</statusCode><content>"
                        + Base64.getEncoder().encodeToString(ZipUtil.comprimir("R-20100066603-RC.xml", CDR_OK.getBytes())) + "</content></status></ns2:getStatusResponse></soap-env:Body></soap-env:Envelope>")));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"03\",\"serie\":\"B001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String haceCinco = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).minusDays(5).toString();
        ResponseEntity<Map> emitida = http.postForEntity("/v1/facturas", new HttpEntity<>("""
            {"serie":"B001","fecha_emision":"%s","moneda":"PEN","enviar_automatico":false,
             "cliente":{"tipo_doc":"1","num_doc":"12345678","razon_social":"JUAN PEREZ"},
             "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":10,"precio_unitario":0.50,"tipo_afectacion_igv":"10"}]}
            """.formatted(haceCinco), h), Map.class);
        assertThat(emitida.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) ((Map<?, ?>) emitida.getBody().get("datos")).get("id");

        ResponseEntity<Map> enviada = http.postForEntity("/v1/facturas/" + id + "/enviar", new HttpEntity<>(h), Map.class);

        assertThat(enviada.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(((Map<?, ?>) enviada.getBody().get("datos")).get("estado_documento")).isEqualTo("ACEPTADO");
        verify(0, postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("sendBill")));
        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("sendSummary")).withRequestBody(matching("(?s).*<fileName>20100066603-RC-\\d{8}-1\\.zip</fileName>.*")));
        Map<String, Object> resumen = (Map<String, Object>) ((java.util.List<?>) http.exchange("/v1/facturas/" + id + "/bajas", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos")).get(0);
        assertThat(resumen).containsEntry("condicion", "ALTA").containsEntry("estado", "ACEPTADA");
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
        Map<?, ?> d = (Map<?, ?>) despues.getBody().get("datos");
        assertThat(d.get("estado_documento")).isEqualTo("ACEPTADO");
        // Historial de intentos (#4): el error y la aceptación posterior, con su motivo, en orden.
        java.util.List<Map<?, ?>> eventos = (java.util.List<Map<?, ?>>) d.get("eventos");
        assertThat(eventos.stream().map(e -> (String) e.get("estado_resultante")).toList()).containsExactly("FIRMADO", "ERROR_ENVIO", "ENVIADO", "ACEPTADO");
        assertThat((String) eventos.get(1).get("mensaje")).isNotBlank();
        assertThat((String) eventos.get(2).get("mensaje")).isEqualTo("Enviado a SUNAT (intento 2)");
        assertThat((String) eventos.get(3).get("mensaje")).contains("ha sido aceptada");
    }

    /**
     * #107: con credenciales SOL que SUNAT no acepta, el comprobante queda pendiente con un mensaje que dice qué hacer, la empresa no sigue golpeando a
     * SUNAT y, al corregir las credenciales, el envío se reanuda solo.
     */
    @Test void credencialesSolRechazadasPausanLosEnviosHastaCorregirlas() throws Exception {
        stubFor(post("/billService").willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml").withBody(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><soap-env:Fault>"
                        + "<faultcode>soap-env:Client.0102</faultcode><faultstring>Usuario o contrasena incorrectos</faultstring></soap-env:Fault></soap-env:Body></soap-env:Envelope>")));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        Map<?, ?> datos = (Map<?, ?>) http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class).getBody().get("datos");
        assertThat(datos.get("estado_documento")).isEqualTo("ERROR_ENVIO");
        assertThat((String) datos.get("ultimo_error")).contains("credenciales SOL").contains("0102").contains("Fiscal & certificado");

        Map<?, ?> empresa = (Map<?, ?>) http.exchange("/v1/empresa", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat((String) ((Map<?, ?>) empresa.get("credenciales_sol_rechazadas")).get("motivo")).startsWith("0102");

        // Pausada: aunque el reintento esté vencido, el outbox no vuelve a golpear a SUNAT con las mismas credenciales.
        jdbcAdelantarOutbox();
        int llamadas = getAllServeEvents().size();
        assertThat(worker.procesar()).isZero();
        assertThat(getAllServeEvents()).hasSize(llamadas);

        // Corregidas: la marca se levanta y el envío sale en la siguiente vuelta del worker, sin tocar la base.
        reset();
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        assertThat(http.exchange("/v1/empresa/credenciales-sol", HttpMethod.PUT, new HttpEntity<>("{\"usuario\":\"BIENBIEN\",\"clave\":\"correcta\"}", h), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(((Map<?, ?>) http.exchange("/v1/empresa", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos")).get("credenciales_sol_rechazadas")).isNull();
        assertThat(worker.procesar()).isEqualTo(1);
        Map<?, ?> d = (Map<?, ?>) http.exchange("/v1/facturas/" + datos.get("id"), HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat(d.get("estado_documento")).isEqualTo("ACEPTADO");
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

    /**
     * Tomcat decodifica %xx, descarta ";param" y resuelve ".." antes de enrutar; los filtros deben decidir
     * sobre esa misma ruta y no sobre la URI cruda. Se usa URI.create para que el cliente no codifique nada.
     */
    @Test void bypassDeRutasNoFunciona() throws Exception {
        String cuerpoTenant = "{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}";
        HttpHeaders sinClave = new HttpHeaders(); sinClave.setContentType(MediaType.APPLICATION_JSON);
        for (String ruta : new String[]{"/v1;x/admin/tenants", "/v1/%61dmin/tenants", "/v1//admin/tenants", "/v1/facturas/../admin/tenants"}) {
            ResponseEntity<String> r = http.exchange(URI.create(ruta), HttpMethod.POST, new HttpEntity<>(cuerpoTenant, sinClave), String.class);
            assertThat(r.getStatusCode()).as(ruta + " sin clave de plataforma").isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        String apiKey = provisionarTenant();
        HttpHeaders conApiKey = new HttpHeaders(); conApiKey.set("X-Api-Key", apiKey); conApiKey.setContentType(MediaType.APPLICATION_JSON);
        for (String ruta : new String[]{"/v1/%61dmin/tenants", "/v1;x/admin/tenants", "/v1/facturas/../admin/tenants"}) {
            ResponseEntity<String> r = http.exchange(URI.create(ruta), HttpMethod.POST, new HttpEntity<>(cuerpoTenant, conApiKey), String.class);
            assertThat(r.getStatusCode()).as(ruta + " con API key de tenant pero sin clave de plataforma").isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant", Integer.class)).isEqualTo(1);

        for (String ruta : new String[]{"/v1;x/facturas", "/v1/%66acturas", "/v1/admin/../facturas"}) {
            ResponseEntity<String> r = http.exchange(URI.create(ruta), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
            assertThat(r.getStatusCode()).as(ruta + " sin API key").isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @org.junit.jupiter.api.BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta CASCADE");
    }
}
