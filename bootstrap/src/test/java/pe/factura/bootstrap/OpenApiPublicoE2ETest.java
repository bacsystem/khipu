package pe.factura.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #602: con la configuración por defecto (la de producción, sin {@code OPENAPI_RUTAS_OCULTAS}) el OpenAPI publicado no documenta el
 * backoffice: ni sus rutas ni los esquemas que solo usan ellas. Las rutas de integración y del portal siguen ahí.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestRestTemplate
class OpenApiPublicoE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TestRestTemplate http;

    @Test void elOpenApiPublicadoNoDocumentaElBackoffice() throws Exception {
        JsonNode publicado = new ObjectMapper().readTree(http.getForObject("/openapi.json", String.class));

        List<String> rutas = new ArrayList<>();
        publicado.get("paths").fieldNames().forEachRemaining(rutas::add);
        assertThat(rutas).as("rutas del backoffice en el OpenAPI público").noneMatch(r -> r.startsWith("/v1/admin"));
        assertThat(rutas).contains("/v1/facturas", "/v1/auth/login", "/v1/catalogos");

        // Los esquemas que solo usa el backoffice tampoco: el documento completo (el que commitea el portal) los trae, el publicado no.
        JsonNode completo = new ObjectMapper().readTree(Files.readString(Path.of("..", "portal", "src", "lib", "api", "openapi.json")));
        Set<String> soloDelBackoffice = new TreeSet<>();
        completo.get("components").get("schemas").fieldNames().forEachRemaining(soloDelBackoffice::add);
        Set<String> publicados = new TreeSet<>();
        publicado.get("components").get("schemas").fieldNames().forEachRemaining(publicados::add);
        soloDelBackoffice.removeAll(publicados);
        assertThat(soloDelBackoffice).as("el documento completo tiene esquemas del backoffice que el público no")
                .contains("AdminLoginRequest", "AdminSesionResponse", "AdministradorResponse");
        assertThat(publicados).as("ningún esquema del backoffice en el OpenAPI público").noneMatch(s -> s.startsWith("Admin"));
        assertThat(publicado.toString()).doesNotContain("/v1/admin/");
    }
}
