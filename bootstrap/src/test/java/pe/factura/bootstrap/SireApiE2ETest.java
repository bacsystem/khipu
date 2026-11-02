package pe.factura.bootstrap;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * #322, fase 2, de extremo a extremo: la API, la base real (Testcontainers), las credenciales de la empresa guardadas por HTTP y un SIRE simulado con WireMock que
 * responde lo que dice el manual del servicio API del RVIE v31 (token del grant password, pedir la propuesta, consultar el ticket, descargar el archivo). No es SUNAT:
 * no hay credenciales API reales (deploy/README.md §15).
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestRestTemplate
class SireApiE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");
    @RegisterExtension static WireMockExtension sunat = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("app.sunat-api.seguridad-beta-url", () -> sunat.baseUrl() + "/api-seguridad");
        r.add("app.sunat-api.sire-beta-url", () -> sunat.baseUrl() + "/api-sire");
        r.add("app.sunat-api.sire-habilitada", () -> "true");
        r.add("app.sunat-api.sire-timeout-seconds", () -> "2");
    }

    /** Uno por prueba: el token queda en la caché del cliente OAuth (por credencial) y la prueba que mira el pedido del token necesita uno nuevo. */
    final String CLIENT_ID = "e2e-sire-" + UUID.randomUUID();
    static final String TICKET = "202600000123";
    static final String PERIODO = "202609";
    static final String RUC = "20100066603";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta CASCADE");
    }

    HttpHeaders empresa(boolean conCredencialesApi) throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        if (conCredencialesApi)
            assertThat(http.exchange("/v1/empresa/credenciales-api-sunat", HttpMethod.PUT,
                    new HttpEntity<>("{\"client_id\":\"" + CLIENT_ID + "\",\"client_secret\":\"secreto-de-prueba\"}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return h;
    }

    void tokenOk() {
        sunat.stubFor(post("/api-seguridad/v1/clientessol/" + CLIENT_ID + "/oauth2/token/").willReturn(okJson("{\"access_token\":\"tk-sire\",\"token_type\":\"JWT\",\"expires_in\":3600}")));
    }

    static String registro(String estado, String descripcion, String archivos) {
        return "{\"paginacion\":{\"page\":1,\"perPage\":20,\"totalRegistros\":1},\"registros\":[{\"perTributario\":\"" + PERIODO + "\",\"numTicket\":\"" + TICKET + "\",\"codProceso\":\"13\","
                + "\"codEstadoProceso\":\"" + estado + "\",\"desEstadoProceso\":\"" + descripcion + "\",\"archivoReporte\":" + archivos + "}]}";
    }

    /** Una fila de 40 campos como la reconstruye el lector de la conciliación: lo que se compara y nada más. */
    static String fila(String tipo, String serie, String numero, String fecha, String total) {
        String[] f = new String[40];
        Arrays.fill(f, "");
        f[0] = RUC; f[2] = PERIODO; f[4] = fecha; f[6] = tipo; f[7] = serie; f[8] = numero;
        f[14] = "100.00"; f[16] = "18.00"; f[18] = "0.00"; f[19] = "0.00"; f[25] = total; f[26] = "PEN"; f[34] = "1";
        return String.join("|", f) + "|";
    }

    static byte[] zip(String... filas) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry(RUC + "-CPF-" + PERIODO + "-01.txt"));
            z.write(String.join("\r\n", filas).getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return out.toByteArray();
    }

    @Test void pedirLaPropuestaVerSuEstadoYConciliarConLoQueSunatPreparo() throws Exception {
        HttpHeaders h = empresa(true);
        tokenOk();
        sunat.stubFor(get(urlPathEqualTo("/api-sire/v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/" + PERIODO + "/exportapropuesta"))
                .willReturn(okJson("{\"numTicket\":\"" + TICKET + "\"}")));

        ResponseEntity<Map> pedida = http.exchange("/v1/sire/rvie/propuesta?periodo=" + PERIODO, HttpMethod.POST, new HttpEntity<>(null, h), Map.class);
        assertThat(pedida.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Map<String, Object> datos = (Map<String, Object>) pedida.getBody().get("datos");
        assertThat(datos).containsEntry("ticket", TICKET).containsEntry("periodo", PERIODO).containsEntry("estado", "EN_PROCESO");
        // El token se pidió con el grant password, el usuario SOL de la empresa y el ámbito del SIRE.
        sunat.verify(postRequestedFor(urlEqualTo("/api-seguridad/v1/clientessol/" + CLIENT_ID + "/oauth2/token/"))
                .withRequestBody(containing("grant_type=password")).withRequestBody(containing("scope=https%3A%2F%2Fapi-sire.sunat.gob.pe")).withRequestBody(containing("username=" + RUC + "MODDATOS")));

        String rutaTickets = "/api-sire/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets";
        sunat.stubFor(get(urlPathEqualTo(rutaTickets)).willReturn(okJson(registro("05", "En proceso", "[]"))));
        Map<String, Object> enProceso = (Map<String, Object>) http.exchange("/v1/sire/rvie/propuesta/" + TICKET + "?periodo=" + PERIODO, HttpMethod.GET, new HttpEntity<>(null, h), Map.class)
                .getBody().get("datos");
        assertThat(enProceso).containsEntry("estado", "EN_PROCESO").containsEntry("codigo_sunat", "05").containsEntry("archivos", 0);

        // Todavía en proceso: conciliar es un 409 y no baja nada.
        ResponseEntity<Map> noLista = http.exchange("/v1/sire/rvie/propuesta/" + TICKET + "/conciliacion?periodo=" + PERIODO, HttpMethod.GET, new HttpEntity<>(null, h), Map.class);
        assertThat(noLista.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(noLista.getBody()).containsEntry("codigo", "PROPUESTA_NO_LISTA");
        sunat.verify(0, getRequestedFor(urlPathEqualTo("/api-sire/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte")));

        // Terminó: un archivo con dos comprobantes que khipu no emitió, así que ambos están solo en SUNAT.
        sunat.stubFor(get(urlPathEqualTo(rutaTickets)).willReturn(okJson(registro("06", "Terminado", "[{\"codTipoAchivoReporte\":\"01\",\"nomArchivoReporte\":\"" + RUC + "-CPF-" + PERIODO + "-01.zip\"}]"))));
        sunat.stubFor(get(urlPathEqualTo("/api-sire/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte"))
                .willReturn(aResponse().withStatus(200).withBody(zip(fila("01", "F001", "1", "01/09/2026", "118.00"), fila("01", "F001", "2", "02/09/2026", "236.00")))));

        Map<String, Object> lista = (Map<String, Object>) http.exchange("/v1/sire/rvie/propuesta/" + TICKET + "?periodo=" + PERIODO, HttpMethod.GET, new HttpEntity<>(null, h), Map.class)
                .getBody().get("datos");
        assertThat(lista).containsEntry("estado", "TERMINADO").containsEntry("archivos", 1);

        ResponseEntity<Map> conciliada = http.exchange("/v1/sire/rvie/propuesta/" + TICKET + "/conciliacion?periodo=" + PERIODO, HttpMethod.GET, new HttpEntity<>(null, h), Map.class);
        assertThat(conciliada.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> c = (Map<String, Object>) conciliada.getBody().get("datos");
        assertThat(c).containsEntry("cuadra", false).containsEntry("registros_sunat", 2).containsEntry("total_solo_en_sunat", 2).containsEntry("total_solo_en_khipu", 0);
        assertThat((java.util.List<String>) c.get("solo_en_sunat")).containsExactly("01-F001-1", "01-F001-2");
        sunat.verify(getRequestedFor(urlPathEqualTo("/api-sire/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte"))
                .withQueryParam("codLibro", equalTo("140000")).withQueryParam("numTicket", equalTo(TICKET)).withHeader("Authorization", equalTo("Bearer tk-sire")));
    }

    @Test void sinCredencialesApiSunatLaEmpresaRecibeUn422ConLoQueFalta() throws Exception {
        HttpHeaders h = empresa(false);
        ResponseEntity<Map> r = http.exchange("/v1/sire/rvie/propuesta?periodo=" + PERIODO, HttpMethod.POST, new HttpEntity<>(null, h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(r.getBody()).containsEntry("codigo", "CREDENCIALES_API_SUNAT_NO_CARGADAS");
        sunat.verify(0, postRequestedFor(urlPathMatching("/api-seguridad/.*")));
    }

    @Test void siSunatNoAceptaLasCredencialesLaRespuestaLoDiceSinFiltrarElSecreto() throws Exception {
        HttpHeaders h = empresa(true);
        sunat.stubFor(post("/api-seguridad/v1/clientessol/" + CLIENT_ID + "/oauth2/token/").willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unauthorized_client\"}")));
        ResponseEntity<Map> r = http.exchange("/v1/sire/rvie/propuesta?periodo=" + PERIODO, HttpMethod.POST, new HttpEntity<>(null, h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(r.getBody()).containsEntry("codigo", "CREDENCIALES_SIRE_RECHAZADAS");
        assertThat(String.valueOf(r.getBody().get("mensaje"))).contains("unauthorized_client").doesNotContain("secreto-de-prueba").doesNotContain("moddatos");
    }

    @Test void unPeriodoSinComprobantesEnSunatEsUn404() throws Exception {
        HttpHeaders h = empresa(true);
        tokenOk();
        sunat.stubFor(get(urlPathEqualTo("/api-sire/v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/" + PERIODO + "/exportapropuesta"))
                .willReturn(aResponse().withStatus(422).withBody("{\"cod\":\"422\",\"msg\":\"Unprocessable Entity\",\"errors\":[{\"cod\":\"1070\",\"msg\":\"No se ha encontrado información de comprobantes de pago en Periodo Seleccionado\"}]}")));
        ResponseEntity<Map> r = http.exchange("/v1/sire/rvie/propuesta?periodo=" + PERIODO, HttpMethod.POST, new HttpEntity<>(null, h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody()).containsEntry("codigo", "NO_ENCONTRADO");
    }

    @Test void unaCaidaDeSunatEsUn503() throws Exception {
        HttpHeaders h = empresa(true);
        tokenOk();
        sunat.stubFor(get(urlPathEqualTo("/api-sire/v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/" + PERIODO + "/exportapropuesta"))
                .willReturn(aResponse().withStatus(503).withBody("{\"cod\":\"503\",\"msg\":\"Service Unavailable\"}")));
        ResponseEntity<Map> r = http.exchange("/v1/sire/rvie/propuesta?periodo=" + PERIODO, HttpMethod.POST, new HttpEntity<>(null, h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).containsEntry("codigo", "SUNAT_NO_DISPONIBLE");
    }

    @Test void sinApiKeyNiSesionNoSePuedeUsarLaRuta() {
        ResponseEntity<Map> r = http.exchange("/v1/sire/rvie/propuesta?periodo=" + PERIODO, HttpMethod.POST, new HttpEntity<>(null, new HttpHeaders()), Map.class);
        assertThat(r.getStatusCode().value()).isIn(401, 403);
    }
}
