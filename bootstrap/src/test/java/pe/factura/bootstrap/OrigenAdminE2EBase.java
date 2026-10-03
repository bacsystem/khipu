package pe.factura.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base de los e2e de la IP de origen del administrador (#208): HTTP real, así que el {@code RemoteIpValve} de Tomcat participa de
 * verdad (con MockMvc no existe) y Postgres real para leer la IP que la bitácora guardó. Las subclases fijan {@code app.trusted-proxies}.
 * El cliente de prueba llega desde loopback: es «el proxy» cuando una subclase lo declara de confianza.
 */
@Testcontainers
abstract class OrigenAdminE2EBase {
    static final String TENANT = "{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}";
    /** Cómo Tomcat muestra loopback según el cliente resuelva IPv4 o IPv6. */
    static final List<String> LOOPBACK = List.of("127.0.0.1", "0:0:0:0:0:0:0:1");

    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private ResponseEntity<Map> origen(HttpHeaders h) {
        return http.exchange("/v1/admin/origen", HttpMethod.GET, new HttpEntity<>(h), Map.class);
    }

    /** Aislamiento, como el listado de cuentas (#180): solo la clave de plataforma o un administrador; nadie más, con o sin proxy configurado. */
    @Test void elEndpointDeCalibracionSoloLoLeeQuienEsAdministrador() {
        ResponseEntity<Map> registro = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}", json()), Map.class);
        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String jwtDeCliente = (String) ((Map<?, ?>) registro.getBody().get("datos")).get("access");
        ResponseEntity<Map> tenant = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(TENANT, conClaveDePlataforma(null)), Map.class);
        String apiKey = (String) ((Map<?, ?>) tenant.getBody().get("datos")).get("api_key");

        HttpHeaders conBearerDeCliente = json(); conBearerDeCliente.setBearerAuth(jwtDeCliente);
        HttpHeaders conApiKey = json(); conApiKey.set("X-Api-Key", apiKey);
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");
        // Una cabecera de proxy no sustituye a una credencial.
        HttpHeaders soloConXForwardedFor = json(); soloConXForwardedFor.set("X-Forwarded-For", "203.0.113.7");

        assertThat(origen(json()).getStatusCode()).as("sin credencial").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(origen(soloConXForwardedFor).getStatusCode()).as("solo X-Forwarded-For").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(origen(conBearerDeCliente).getStatusCode()).as("JWT de un cliente del portal").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(origen(conApiKey).getStatusCode()).as("API key de una empresa").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(origen(conClaveErronea).getStatusCode()).as("clave de plataforma errónea").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(origen(conClaveDePlataforma(null)).getStatusCode()).as("la clave de plataforma correcta, para contrastar").isEqualTo(HttpStatus.OK);
    }

    HttpHeaders conClaveDePlataforma(String xForwardedFor) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Platform-Key", "plataforma-test");
        h.setContentType(MediaType.APPLICATION_JSON);
        if (xForwardedFor != null) h.set("X-Forwarded-For", xForwardedFor);
        return h;
    }

    /** Hace una acción auditada con esa cabecera y devuelve la IP que quedó en la bitácora. */
    String ipRegistradaTras(String xForwardedFor) {
        ResponseEntity<Map> r = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(TENANT, conClaveDePlataforma(xForwardedFor)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return jdbc.queryForObject("SELECT ip FROM auditoria_admin", String.class);
    }

    String ipSegunElEndpointDeCalibracion(String xForwardedFor) {
        ResponseEntity<Map> r = http.exchange("/v1/admin/origen", HttpMethod.GET, new HttpEntity<>(conClaveDePlataforma(xForwardedFor)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) ((Map<?, ?>) r.getBody().get("datos")).get("ip");
    }
}
