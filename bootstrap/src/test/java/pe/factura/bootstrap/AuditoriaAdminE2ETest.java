package pe.factura.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bitácora de auditoría del administrador (#178) de extremo a extremo: HTTP real, filtros reales y Postgres real.
 * Verifica que quien actúa queda registrado según cómo entró (clave de plataforma o sesión de administrador).
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuditoriaAdminE2ETest {
    static final String TENANT = "{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}";

    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, administrador, auditoria_admin CASCADE");
    }

    private HttpHeaders conClaveDePlataforma() {
        HttpHeaders h = new HttpHeaders(); h.set("X-Platform-Key", "plataforma-test"); h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders conSesion(String token) {
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(token); h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private List<Map<String, Object>> bitacora() {
        return jdbc.queryForList("SELECT * FROM auditoria_admin ORDER BY ocurrido_en");
    }

    @Test void crearUnTenantConLaClaveDePlataformaQuedaALaVozDeLaClave() {
        ResponseEntity<Map> r = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(TENANT, conClaveDePlataforma()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");

        List<Map<String, Object>> filas = bitacora();
        assertThat(filas).hasSize(1);
        Map<String, Object> fila = filas.get(0);
        assertThat(fila.get("actor_tipo")).isEqualTo("CLAVE_PLATAFORMA");
        assertThat(fila.get("administrador_id")).isNull();
        assertThat(fila.get("accion")).isEqualTo("CREAR_TENANT");
        assertThat(fila.get("tenant_id").toString()).isEqualTo(datos.get("tenant_id"));
        assertThat((String) fila.get("ip")).isNotBlank();
        assertThat((String) fila.get("detalle")).contains("20100066603").doesNotContain((String) datos.get("api_key"));
    }

    @Test void unAdministradorConSesionQuedaRegistradoConSuPropioId() {
        ResponseEntity<Map> alta = http.postForEntity("/v1/admin/administradores",
                new HttpEntity<>("{\"email\":\"ana@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> sesion = SesionAdminDePrueba.entrar(http, "ana@khipu.pe", "Segura123");
        String token = (String) sesion.get("access_token");
        String administradorId = (String) ((Map<?, ?>) sesion.get("administrador")).get("id");

        ResponseEntity<Map> r = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(TENANT, conSesion(token)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<Map<String, Object>> filas = bitacora();
        // El primer login configura el segundo factor (#177) y también queda en la bitácora.
        assertThat(filas).extracting(f -> f.get("accion")).containsExactly("CREAR_ADMINISTRADOR", "CONFIGURAR_SEGUNDO_FACTOR", "CREAR_TENANT");
        assertThat(filas.get(0).get("actor_tipo")).isEqualTo("CLAVE_PLATAFORMA");
        assertThat(filas.get(2).get("actor_tipo")).isEqualTo("ADMINISTRADOR");
        assertThat(filas.get(2).get("administrador_id").toString()).isEqualTo(administradorId);
    }

    @Test void unaAccionRechazadaONoAutenticadaNoDejaRegistro() {
        http.postForEntity("/v1/admin/tenants", new HttpEntity<>(TENANT, conClaveDePlataforma()), Map.class);
        assertThat(bitacora()).hasSize(1);

        ResponseEntity<String> duplicado = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(TENANT, conClaveDePlataforma()), String.class);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        HttpHeaders sinCredencial = new HttpHeaders(); sinCredencial.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> noAutenticado = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(TENANT, sinCredencial), String.class);
        assertThat(noAutenticado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(bitacora()).hasSize(1);
    }
}
