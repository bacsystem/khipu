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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo del portal: registro → login → alta de empresa → aislamiento entre cuentas.
 * El flujo por API key (integradores) se verifica por separado en {@link FacturaE2ETest}.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta CASCADE");
    }

    private record Cuenta(String access, String refresh, String cuentaId) {}

    private Cuenta registrar(String nombre, String email) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro",
                new HttpEntity<>("{\"nombre\":\"%s\",\"email\":\"%s\",\"password\":\"Segura123\"}".formatted(nombre, email), h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        Map<?, ?> usuario = (Map<?, ?>) datos.get("usuario");
        return new Cuenta((String) datos.get("access"), (String) datos.get("refresh"), (String) usuario.get("cuenta_id"));
    }

    private HttpHeaders conJwt(String access) {
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(access); h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test void registroLoginYCicloDeVidaDeSesion() {
        Cuenta cuenta = registrar("Mi negocio", "ana@negocio.pe");
        assertThat(cuenta.access()).isNotBlank();

        HttpHeaders login = new HttpHeaders(); login.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/login",
                new HttpEntity<>("{\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\"}", login), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> me = http.exchange("/v1/auth/me", HttpMethod.GET, new HttpEntity<>(conJwt(cuenta.access())), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) me.getBody().get("datos")).get("email")).isEqualTo("ana@negocio.pe");
    }

    @Test void loginConCredencialesIncorrectasEs401() {
        registrar("Mi negocio", "ana@negocio.pe");
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/login",
                new HttpEntity<>("{\"email\":\"ana@negocio.pe\",\"password\":\"incorrecta\"}", h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void crearYListarEmpresasDeLaCuentaAutenticada() {
        Cuenta cuenta = registrar("Mi negocio", "ana@negocio.pe");
        HttpHeaders h = conJwt(cuenta.access());

        ResponseEntity<Map> creada = http.postForEntity("/v1/empresas",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", h), Map.class);
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String empresaId = (String) ((Map<?, ?>) creada.getBody().get("datos")).get("id");

        ResponseEntity<Map> lista = http.exchange("/v1/empresas", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> datos = (List<Map<String, Object>>) lista.getBody().get("datos");
        assertThat(datos).extracting(m -> m.get("id")).containsExactly(empresaId);
    }

    @Test void listarFacturasConJwtYEmpresaPropia() {
        Cuenta cuenta = registrar("Mi negocio", "ana@negocio.pe");
        HttpHeaders h = conJwt(cuenta.access());
        ResponseEntity<Map> creada = http.postForEntity("/v1/empresas",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", h), Map.class);
        String empresaId = (String) ((Map<?, ?>) creada.getBody().get("datos")).get("id");

        HttpHeaders conEmpresa = conJwt(cuenta.access()); conEmpresa.set("X-Empresa", empresaId);
        ResponseEntity<Map> facturas = http.exchange("/v1/facturas", HttpMethod.GET, new HttpEntity<>(conEmpresa), Map.class);
        assertThat(facturas.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) facturas.getBody().get("datos")).isEmpty();
    }

    @Test void empresaDeOtraCuentaEs403() {
        Cuenta cuentaA = registrar("Negocio A", "a@negocio.pe");
        HttpHeaders hA = conJwt(cuentaA.access());
        ResponseEntity<Map> creada = http.postForEntity("/v1/empresas",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA A\",\"entorno\":\"BETA\"}", hA), Map.class);
        String empresaDeA = (String) ((Map<?, ?>) creada.getBody().get("datos")).get("id");

        Cuenta cuentaB = registrar("Negocio B", "b@negocio.pe");
        HttpHeaders hB = conJwt(cuentaB.access()); hB.set("X-Empresa", empresaDeA);
        ResponseEntity<Map> r = http.exchange("/v1/facturas", HttpMethod.GET, new HttpEntity<>(hB), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().get("codigo")).isEqualTo("EMPRESA_AJENA");
    }

    @Test void empresaConIdInexistenteEs403() {
        Cuenta cuenta = registrar("Mi negocio", "ana@negocio.pe");
        HttpHeaders h = conJwt(cuenta.access()); h.set("X-Empresa", UUID.randomUUID().toString());
        ResponseEntity<Map> r = http.exchange("/v1/facturas", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void tokenInvalidoEs401() {
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth("token-basura"); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> r = http.exchange("/v1/empresas", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void recuperarSiempreDevuelve202() {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> r = http.postForEntity("/v1/auth/recuperar", new HttpEntity<>("{\"email\":\"no-existe@negocio.pe\"}", h), Void.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
}
