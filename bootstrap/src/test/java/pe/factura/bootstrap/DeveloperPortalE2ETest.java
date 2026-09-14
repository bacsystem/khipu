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
        assertThat(tieneSecurityEnGet(paths, "/v1/facturas")).isTrue();
        assertThat(tieneSecurityEnGet(paths, "/v1/series")).isTrue();
        assertThat(tieneSecurityEnGet(paths, "/v1/empresa")).isTrue();
        assertThat(tieneSecurityEnGet(paths, "/v1/empresas")).isFalse();
        assertThat(paths.containsKey("/v1/auth/login")).isTrue();
        assertThat(tieneSecurityEnPost(paths, "/v1/auth/login")).isFalse();
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
