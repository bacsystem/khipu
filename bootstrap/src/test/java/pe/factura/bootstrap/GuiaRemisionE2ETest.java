package pe.factura.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.adapters.scheduler.OutboxWorker;
import pe.factura.adapters.sunat.ZipUtil;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * #39 de extremo a extremo: la API, la base real (Testcontainers), la firma real y un SUNAT simulado con WireMock que responde lo que dice «manual_url_gre_rest»
 * (token del grant password, envío con ticket, consulta del ticket con CDR). No es SUNAT: no hay credenciales API reales ni API GRE de pruebas (deploy/README.md §14).
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestRestTemplate
class GuiaRemisionE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");
    @RegisterExtension static WireMockExtension sunat = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("app.sunat-api.seguridad-beta-url", () -> sunat.baseUrl() + "/api-seguridad");
        r.add("app.sunat-api.gre-beta-url", () -> sunat.baseUrl() + "/api-cpe");
        // H6: la beta de la GRE pide el token a su propio servicio (aquí, el mismo WireMock), no al api-seguridad real. H1: el envío se enciende en esta prueba.
        r.add("app.sunat-api.gre-seguridad-beta-url", () -> sunat.baseUrl() + "/api-seguridad");
        r.add("app.sunat-api.gre-habilitada", () -> "true");
        r.add("app.sunat-api.timeout-seconds", () -> "2");
        r.add("app.sunat-api.gre-timeout-seconds", () -> "2");
    }

    /** Uno por prueba: el token queda en la caché del cliente OAuth (por credencial) y la prueba que mira el pedido del token necesita uno nuevo. */
    final String CLIENT_ID = "e2e-gre-" + UUID.randomUUID();
    static final String TICKET = "7f7b00ab-4fee-4ae2-9e04-fbd16ca18677";
    static final String HOY = LocalDate.now(ZoneId.of("America/Lima")).toString();

    @Autowired TestRestTemplate http;
    @Autowired OutboxWorker worker;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json = new ObjectMapper();

    static final String CDR_OK = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
          xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
          <cbc:ID>1</cbc:ID>
          <cac:DocumentResponse><cac:Response><cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>La Guia numero T001-1, ha sido aceptada</cbc:Description></cac:Response>
          <cac:DocumentReference><cbc:ID>T001-1</cbc:ID><cbc:DocumentDescription>https://e-factura.sunat.gob.pe/v1/contribuyente/gre/comprobantes/descargaqr?hashqr=abc</cbc:DocumentDescription></cac:DocumentReference>
          </cac:DocumentResponse>
        </ar:ApplicationResponse>""";

    static String guia(String hoy) {
        return """
            {"serie":"T001","fecha_emision":"%s","destinatario":{"tipo_doc":"6","num_doc":"20601234565","nombre":"COMERCIAL ANDINA S.A.C."},
             "motivo":"01","modalidad":"02",
             "traslado":{"fecha_inicio":"%s","peso_bruto":25.5,"partida":{"ubigeo":"150101","direccion":"Av. Argentina 1234, Lima"},
               "llegada":{"ubigeo":"040101","direccion":"Calle Mercaderes 200, Arequipa"},
               "vehiculos":[{"placa":"ABC-123"}],"conductores":[{"tipo_doc":"1","num_doc":"45678912","nombres":"JUAN","apellidos":"QUISPE","licencia":"Q45678912"}]},
             "items":[{"codigo":"LAP-15","descripcion":"Laptop 15 pulgadas","unidad":"NIU","cantidad":2}],
             "documentos_relacionados":[{"tipo":"01","numero":"F001-123","ruc_emisor":"20100066603"}]}
            """.formatted(hoy, hoy);
    }

    @org.junit.jupiter.api.BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, guia_remision CASCADE");
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta CASCADE");
    }

    HttpHeaders empresa(boolean conCredencialesApi) throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"09\",\"serie\":\"T001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"31\",\"serie\":\"V001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        if (conCredencialesApi)
            assertThat(http.exchange("/v1/empresa/credenciales-api-sunat", HttpMethod.PUT,
                    new HttpEntity<>("{\"client_id\":\"" + CLIENT_ID + "\",\"client_secret\":\"secreto-de-prueba\"}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return h;
    }

    void tokenOk() {
        sunat.stubFor(post("/api-seguridad/v1/clientessol/" + CLIENT_ID + "/oauth2/token/").willReturn(okJson("{\"access_token\":\"tk-gre\",\"token_type\":\"JWT\",\"expires_in\":3600}")));
    }

    @Test void laGuiaRemitenteSeEnviaYSuTicketTraeElCdr() throws Exception {
        sunat.resetAll();
        tokenOk();
        sunat.stubFor(post("/api-cpe/v1/contribuyente/gem/comprobantes/20100066603-09-T001-1").willReturn(okJson("{\"numTicket\":\"" + TICKET + "\",\"fecRecepcion\":\"2026-10-20T10:00:01\"}")));
        String cdr = Base64.getEncoder().encodeToString(ZipUtil.comprimir("R-20100066603-09-T001-1.xml", CDR_OK.getBytes()));
        sunat.stubFor(get("/api-cpe/v1/contribuyente/gem/comprobantes/envios/" + TICKET).inScenario("ticket").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("{\"codRespuesta\":\"98\"}")).willSetStateTo("procesado"));
        sunat.stubFor(get("/api-cpe/v1/contribuyente/gem/comprobantes/envios/" + TICKET).inScenario("ticket").whenScenarioStateIs("procesado")
                .willReturn(okJson("{\"codRespuesta\":\"0\",\"arcCdr\":\"" + cdr + "\",\"indCdrGenerado\":\"1\"}")));
        HttpHeaders h = empresa(true);

        ResponseEntity<Map> r = http.postForEntity("/v1/guias-remision", new HttpEntity<>(guia(HOY), h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        assertThat(datos.get("estado")).as("la primera consulta, en el acto, dice 98").isEqualTo("ENVIADO_PENDIENTE_TICKET");
        assertThat(datos.get("numero")).isEqualTo(1);
        assertThat(datos.get("ticket")).isEqualTo(TICKET);
        assertThat(datos.get("reintento_automatico")).isEqualTo(true);

        // Lo que llegó a «SUNAT»: el token del grant password y el ZIP del XML firmado, con su hash.
        LoggedRequest token = sunat.findAll(postRequestedFor(urlPathMatching("/api-seguridad/.*"))).get(0);
        assertThat(java.net.URLDecoder.decode(token.getBodyAsString(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("grant_type=password").contains("username=20100066603MODDATOS").contains("password=moddatos").contains("scope=https://api-cpe.sunat.gob.pe");
        LoggedRequest envio = sunat.findAll(postRequestedFor(urlPathMatching("/api-cpe/.*"))).get(0);
        assertThat(envio.getHeader("Authorization")).isEqualTo("Bearer tk-gre");
        JsonNode archivo = json.readTree(envio.getBodyAsString()).path("archivo");
        assertThat(archivo.path("nomArchivo").asText()).isEqualTo("20100066603-09-T001-1.zip");
        byte[] zip = Base64.getDecoder().decode(archivo.path("arcGreZip").asText());
        assertThat(archivo.path("hashZip").asText()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip)));
        String xml = new String(ZipUtil.extraerPrimero(zip, ".xml"));
        assertThat(xml).contains("<DespatchAdvice").contains("<ds:Signature").contains("<cbc:ID>T001-1</cbc:ID>").contains(">09</cbc:DespatchAdviceTypeCode>")
                .contains("<cbc:ID>ABC123</cbc:ID>");

        String id = (String) datos.get("id");
        ResponseEntity<Map> consultada = http.postForEntity("/v1/guias-remision/" + id + "/ticket", new HttpEntity<>(h), Map.class);
        assertThat(consultada.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> aceptada = (Map<?, ?>) consultada.getBody().get("datos");
        assertThat(aceptada.get("estado")).isEqualTo("ACEPTADO");
        assertThat(((Map<?, ?>) aceptada.get("cdr")).get("codigo")).isEqualTo("0");
        assertThat(aceptada.get("enlace_consulta")).isEqualTo("https://e-factura.sunat.gob.pe/v1/contribuyente/gre/comprobantes/descargaqr?hashqr=abc");
        assertThat(aceptada.get("reintento_automatico")).isEqualTo(false);
        // Resuelta, la tarea de consulta del outbox ya no está.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE agregado_id = ?::uuid", Long.class, id)).isZero();

        assertThat(new String(http.exchange("/v1/guias-remision/" + id + "/xml", HttpMethod.GET, new HttpEntity<>(h), byte[].class).getBody())).isEqualTo(xml);
        assertThat(new String(ZipUtil.extraerPrimero(http.exchange("/v1/guias-remision/" + id + "/cdr", HttpMethod.GET, new HttpEntity<>(h), byte[].class).getBody(), ".xml")))
                .contains("ha sido aceptada");
        ResponseEntity<byte[]> pdf = http.exchange("/v1/guias-remision/" + id + "/pdf", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(pdf.getBody(), 0, 5)).isEqualTo("%PDF-");

        ResponseEntity<Map> lista = http.exchange("/v1/guias-remision?tipo=09", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(lista.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        // Ya resuelta, no hay ticket que consultar.
        assertThat(http.postForEntity("/v1/guias-remision/" + id + "/ticket", new HttpEntity<>(h), Map.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Sin credencial, la misma puerta que el resto de la API.
        HttpHeaders sin = new HttpHeaders(); sin.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/guias-remision", new HttpEntity<>(guia(HOY), sin), Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void siSunatNoRespondeElOutboxLaReenviaYConsultaSuTicket() throws Exception {
        sunat.resetAll();
        tokenOk();
        sunat.stubFor(post("/api-cpe/v1/contribuyente/gem/comprobantes/20100066603-09-T001-1").willReturn(aResponse().withStatus(503).withBody("{\"cod\":\"503\",\"msg\":\"Service Unavailable\"}")));
        HttpHeaders h = empresa(true);
        Map<?, ?> enError = (Map<?, ?>) http.postForEntity("/v1/guias-remision", new HttpEntity<>(guia(HOY), h), Map.class).getBody().get("datos");
        assertThat(enError.get("estado")).isEqualTo("ERROR_ENVIO");
        assertThat(enError.get("reintento_automatico")).isEqualTo(true);
        String id = (String) enError.get("id");
        assertThat(jdbc.queryForList("SELECT accion FROM outbox WHERE agregado_id = ?::uuid", String.class, id)).containsExactly("GRE_ENVIAR");

        // SUNAT vuelve: el outbox (con la tarea ya vencida) reenvía la misma guía, y después consulta el ticket hasta el CDR.
        sunat.stubFor(post("/api-cpe/v1/contribuyente/gem/comprobantes/20100066603-09-T001-1").willReturn(okJson("{\"numTicket\":\"" + TICKET + "\"}")));
        sunat.stubFor(get("/api-cpe/v1/contribuyente/gem/comprobantes/envios/" + TICKET).willReturn(okJson("{\"codRespuesta\":\"98\"}")));
        jdbc.update("UPDATE outbox SET siguiente_intento = now() - interval '1 minute' WHERE agregado_id = ?::uuid", id);
        worker.procesar();
        Map<?, ?> pendiente = (Map<?, ?>) http.exchange("/v1/guias-remision/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat(pendiente.get("estado")).isEqualTo("ENVIADO_PENDIENTE_TICKET");
        assertThat(pendiente.get("intentos")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT accion FROM outbox WHERE agregado_id = ?::uuid", String.class, id)).containsExactly("GRE_TICKET");

        sunat.stubFor(get("/api-cpe/v1/contribuyente/gem/comprobantes/envios/" + TICKET).willReturn(okJson("{\"codRespuesta\":\"99\",\"error\":{\"numError\":\"2555\",\"desError\":\"Destinatario no debe ser igual al remitente\"},\"indCdrGenerado\":\"0\"}")));
        jdbc.update("UPDATE outbox SET siguiente_intento = now() - interval '1 minute' WHERE agregado_id = ?::uuid", id);
        worker.procesar();
        Map<?, ?> rechazada = (Map<?, ?>) http.exchange("/v1/guias-remision/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat(rechazada.get("estado")).isEqualTo("RECHAZADO");
        assertThat(((Map<?, ?>) rechazada.get("cdr")).get("codigo")).isEqualTo("2555");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE agregado_id = ?::uuid", Long.class, id)).isZero();
        // Dos consultas: la del acto al reenviar (98) y la del outbox (99).
        sunat.verify(2, getRequestedFor(urlEqualTo("/api-cpe/v1/contribuyente/gem/comprobantes/envios/" + TICKET)).withHeader("Authorization", equalTo("Bearer tk-gre")));
    }

    @Test void sinCredencialesApiNoSeNumeraYSinEnviarSeEmiteParaRevisarElXml() throws Exception {
        sunat.resetAll();
        HttpHeaders h = empresa(false);
        ResponseEntity<Map> sinApi = http.postForEntity("/v1/guias-remision", new HttpEntity<>(guia(HOY), h), Map.class);
        assertThat(sinApi.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(sinApi.getBody().get("codigo")).isEqualTo("CREDENCIALES_API_SUNAT_NO_CARGADAS");
        // Una regla de SUNAT tampoco consume número.
        ResponseEntity<Map> publico = http.postForEntity("/v1/guias-remision", new HttpEntity<>(guia(HOY).replace("\"modalidad\":\"02\"", "\"modalidad\":\"01\"").replace("\"items\":", "\"enviar_automatico\":false,\"items\":"), h), Map.class);
        assertThat(publico.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) publico.getBody().get("mensaje")).startsWith("2534 - ");

        ResponseEntity<Map> firmada = http.postForEntity("/v1/guias-remision", new HttpEntity<>(guia(HOY).replace("\"items\":", "\"enviar_automatico\":false,\"items\":"), h), Map.class);
        assertThat(firmada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) firmada.getBody().get("datos");
        assertThat(datos.get("estado")).isEqualTo("FIRMADO");
        assertThat(datos.get("numero")).as("lo rechazado no consumió número").isEqualTo(1);
        // La transportista, también sin enviar: su XML firmado pasa el XSD y lleva al remitente.
        String transportista = """
            {"serie":"V001","destinatario":{"tipo_doc":"6","num_doc":"20601234565","nombre":"COMERCIAL ANDINA S.A.C."},
             "remitente":{"tipo_doc":"6","num_doc":"20555555556","nombre":"DISTRIBUIDORA NORTE S.A."},"registro_mtc":"1512345CNG",
             "traslado":{"fecha_inicio":"%s","peso_bruto":100,"partida":{"ubigeo":"150101","direccion":"Av. Argentina 1234"},"llegada":{"ubigeo":"040101","direccion":"Calle Mercaderes 200"},
               "vehiculos":[{"placa":"ABC123"}],"conductores":[{"tipo_doc":"1","num_doc":"45678912","nombres":"JUAN","apellidos":"QUISPE","licencia":"Q45678912"}]},
             "items":[{"descripcion":"Cajas","unidad":"BX","cantidad":10}],"enviar_automatico":false}
            """.formatted(HOY);
        Map<?, ?> v = (Map<?, ?>) http.postForEntity("/v1/guias-remision", new HttpEntity<>(transportista, h), Map.class).getBody().get("datos");
        assertThat(v.get("tipo")).isEqualTo("31");
        assertThat(v.get("estado")).isEqualTo("FIRMADO");
        String xml = new String(http.exchange("/v1/guias-remision/" + v.get("id") + "/xml", HttpMethod.GET, new HttpEntity<>(h), byte[].class).getBody());
        assertThat(xml).contains(">31</cbc:DespatchAdviceTypeCode>").contains("<cac:DespatchParty>").contains("20555555556").contains("<ds:Signature");
        // Enviarla después sin credenciales API la deja en error, sin reintento automático, y sin llamar a SUNAT.
        Map<?, ?> enviada = (Map<?, ?>) http.postForEntity("/v1/guias-remision/" + v.get("id") + "/enviar", new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat(enviada.get("estado")).isEqualTo("ERROR_ENVIO");
        assertThat(enviada.get("reintento_automatico")).isEqualTo(false);
        assertThat((String) enviada.get("ultimo_error")).startsWith("CREDENCIALES_API_SUNAT_NO_CARGADAS");
        assertThat(sunat.getAllServeEvents()).isEmpty();
        assertThat(List.of(UUID.fromString((String) v.get("id")))).hasSize(1);
    }
}
