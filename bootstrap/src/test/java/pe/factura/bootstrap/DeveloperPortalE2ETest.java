package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class DeveloperPortalE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;

    @Test void openApiDeclaraApiKeySoloEnRutasDeTenant() {
        ResponseEntity<Map> r = http.getForEntity("/openapi.json", Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> components = (Map<String, Object>) r.getBody().get("components");
        Map<String, Object> securitySchemes = (Map<String, Object>) components.get("securitySchemes");
        Map<String, Object> apiKeyScheme = (Map<String, Object>) securitySchemes.get("ApiKey");
        assertThat(apiKeyScheme.get("type")).isEqualTo("apiKey");
        assertThat(apiKeyScheme.get("in")).isEqualTo("header");
        assertThat(apiKeyScheme.get("name")).isEqualTo("X-Api-Key");

        Map<String, Object> paths = (Map<String, Object>) r.getBody().get("paths");
        // Los catálogos llevan operationId explícito: sin él springdoc desambigua "listar" como "listar_2" y los clientes generados heredan ese nombre.
        assertThat(((Map<String, Object>) ((Map<String, Object>) paths.get("/v1/catalogos")).get("get")).get("operationId")).isEqualTo("listarCatalogos");
        assertThat(((Map<String, Object>) ((Map<String, Object>) paths.get("/v1/catalogos/{id}")).get("get")).get("operationId")).isEqualTo("obtenerCatalogo");
        assertThat(tieneSecurityEnGet(paths, "/v1/facturas")).isTrue();
        assertThat(tieneSecurityEnGet(paths, "/v1/series")).isTrue();
        assertThat(tieneSecurityEnGet(paths, "/v1/empresa")).isTrue();
        assertThat(tieneSecurityEnGet(paths, "/v1/empresas")).isFalse();
        assertThat(paths.containsKey("/v1/auth/login")).isTrue();
        assertThat(tieneSecurityEnPost(paths, "/v1/auth/login")).isFalse();
    }

    /**
     * #5: el portal compara sus tipos a mano con {@code portal/src/lib/api/openapi.json} (el d.ts sale de él) para que {@code tsc} avise si la API cambia el contrato. Eso solo sirve
     * si el archivo es el de la API de verdad: esta prueba falla cuando el backend cambió y el archivo no se regeneró. {@code servers} no cuenta (es el host desde el que se pidió).
     */
    @Test void elOpenApiCommiteadoDelPortalEsElDeLaApi() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ObjectNode enVivo = (ObjectNode) json.readTree(http.getForObject("/openapi.json", String.class));
        enVivo.remove("servers");
        Path archivo = Path.of("..", "portal", "src", "lib", "api", "openapi.json");
        assertThat(archivo).as("el portal commitea su copia del OpenAPI").exists();
        JsonNode commiteado = json.readTree(Files.readString(archivo));

        List<String> diferencias = new ArrayList<>();
        diferencias(enVivo, commiteado, "", diferencias);
        assertThat(diferencias)
                .as("portal/src/lib/api/openapi.json no es el OpenAPI de la API. Con un backend en marcha: `API_BASE_URL=http://localhost:8001 npm --prefix portal run generate:api`, "
                        + "corrija lo que rompa `npm --prefix portal run typecheck` (contrato-openapi.ts) y commitee los dos archivos. Primeras diferencias (API en vivo vs archivo)")
                .isEmpty();
    }

    /** Hasta diez caminos en los que los dos documentos difieren, para que el fallo diga dónde sin volcar medio megabyte. */
    private static void diferencias(JsonNode a, JsonNode b, String ruta, List<String> salida) {
        if (salida.size() >= 10 || a.equals(b)) return;
        // 1000.0 de Jackson y 1000 de JSON.stringify son el mismo número.
        if (a.isNumber() && b.isNumber() && a.decimalValue().compareTo(b.decimalValue()) == 0) return;
        if (a.isObject() && b.isObject()) {
            java.util.Set<String> claves = new java.util.TreeSet<>();
            a.fieldNames().forEachRemaining(claves::add);
            b.fieldNames().forEachRemaining(claves::add);
            for (String k : claves) {
                if (!a.has(k)) salida.add(ruta + "/" + k + " solo está en el archivo");
                else if (!b.has(k)) salida.add(ruta + "/" + k + " solo está en la API");
                else diferencias(a.get(k), b.get(k), ruta + "/" + k, salida);
            }
        } else if (a.isArray() && b.isArray() && a.size() == b.size()) {
            for (int i = 0; i < a.size(); i++) diferencias(a.get(i), b.get(i), ruta + "/" + i, salida);
        } else {
            String x = a.toString(), y = b.toString();
            salida.add(ruta + ": API " + (x.length() > 80 ? x.substring(0, 80) + "…" : x) + " / archivo " + (y.length() > 80 ? y.substring(0, 80) + "…" : y));
        }
    }

    @Test void preflightCorsRespondeConLosHeadersDelOrigenDelPortal() {
        HttpHeaders h = new HttpHeaders();
        h.set("Origin", "http://localhost:3000"); // app.portal-url en application-test.yml
        h.set("Access-Control-Request-Method", "GET");
        ResponseEntity<Void> r = http.exchange("/v1/facturas", HttpMethod.OPTIONS, new HttpEntity<>(h), Void.class);

        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(r.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:3000");
    }

    @Test void peticionSinOrigenDelPortalNoRecibeHeadersCors() {
        HttpHeaders h = new HttpHeaders();
        h.set("Origin", "https://otro-sitio.com");
        h.set("Access-Control-Request-Method", "GET");
        ResponseEntity<Void> r = http.exchange("/v1/facturas", HttpMethod.OPTIONS, new HttpEntity<>(h), Void.class);

        assertThat(r.getHeaders().getFirst("Access-Control-Allow-Origin")).isNull();
    }

    private boolean tieneSecurityEnGet(Map<String, Object> paths, String path) {
        return tieneSecurity(paths, path, "get");
    }

    private boolean tieneSecurityEnPost(Map<String, Object> paths, String path) {
        return tieneSecurity(paths, path, "post");
    }

    private boolean tieneSecurity(Map<String, Object> paths, String path, String metodo) {
        Map<String, Object> item = (Map<String, Object>) paths.get(path);
        Map<String, Object> operacion = (Map<String, Object>) item.get(metodo);
        return operacion != null && operacion.get("security") != null;
    }
}
