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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.factura.bootstrap.SesionAdminDePrueba.TOTP;
import static pe.factura.bootstrap.SesionAdminDePrueba.post;

/**
 * Segundo factor del administrador (#177) de extremo a extremo: HTTP real, filtros reales y Postgres real. Lo que más importa: con
 * la contraseña sola no se opera el backoffice, ni siquiera llevando el desafío como si fuera una sesión.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class SegundoFactorAdminE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void unAdministrador() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Platform-Key", "plataforma-test");
        assertThat(http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"ana@khipu.pe\",\"password\":\"Segura123\"}", h), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test void laContrasenaSolaNoAbreElBackoffice() {
        Map<String, Object> d = datos(login());
        assertThat(d).containsEntry("paso", "CONFIGURAR_SEGUNDO_FACTOR").doesNotContainKey("access_token");
        String desafio = (String) d.get("desafio");

        assertThat(conBearer("/v1/admin/cuentas", desafio).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(conBearer("/v1/admin/auth/me", desafio).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void elPrimerLoginConfiguraYLosSiguientesVerifican() {
        Map<String, Object> primera = SesionAdminDePrueba.entrar(http, "ana@khipu.pe", "Segura123");
        assertThat((List<String>) primera.get("codigos_recuperacion")).hasSize(10);
        assertThat(primera.get("expira_en")).isEqualTo(1800);
        assertThat(conBearer("/v1/admin/auth/me", (String) primera.get("access_token")).getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> d = datos(login());
        assertThat(d).containsEntry("paso", "VERIFICAR_SEGUNDO_FACTOR");
        String desafio = (String) d.get("desafio");
        ResponseEntity<Map> reconfigurar = post(http, "/v1/admin/auth/segundo-factor/configurar", "{\"desafio\":\"%s\"}".formatted(desafio));
        assertThat(reconfigurar.getStatusCode()).as("la contraseña sola no cambia el segundo factor").isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> sesion = verificar(desafio, codigoSiguiente());
        assertThat(sesion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(datos(sesion)).doesNotContainKey("codigos_recuperacion");
        assertThat(conBearer("/v1/admin/auth/me", (String) datos(sesion).get("access_token")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void unCodigoNoSirveDosVeces() {
        SesionAdminDePrueba.entrar(http, "ana@khipu.pe", "Segura123");
        String codigo = codigoSiguiente();
        assertThat(verificar(desafio(), codigo).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> otraVez = verificar(desafio(), codigo);
        assertThat(otraVez.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(otraVez.getBody()).containsEntry("codigo", "CODIGO_INVALIDO");
    }

    @Test void unCodigoDeRecuperacionEntraUnaSolaVez() {
        List<String> codigos = (List<String>) SesionAdminDePrueba.entrar(http, "ana@khipu.pe", "Segura123").get("codigos_recuperacion");

        assertThat(verificar(desafio(), codigos.get(0)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verificar(desafio(), codigos.get(0)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bitacora()).extracting(f -> f.get("detalle")).contains("segundo_factor=codigo_recuperacion");
    }

    /** Cinco códigos equivocados bloquean 15 minutos: después ni el correcto entra. Los fallos sobreviven al error de cada petición. */
    @Test void cincoFallosBloquean() {
        SesionAdminDePrueba.entrar(http, "ana@khipu.pe", "Segura123");
        for (int i = 0; i < 5; i++) assertThat(verificar(desafio(), "000000").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> bloqueado = verificar(desafio(), codigoSiguiente());
        assertThat(bloqueado.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(bloqueado.getBody()).containsEntry("codigo", "DEMASIADOS_INTENTOS");
    }

    @Test void laBitacoraRegistraLaConfiguracionYCadaInicioDeSesion() {
        Map<String, Object> primera = SesionAdminDePrueba.entrar(http, "ana@khipu.pe", "Segura123");
        verificar(desafio(), codigoSiguiente());
        String id = (String) ((Map<?, ?>) primera.get("administrador")).get("id");

        List<Map<String, Object>> filas = bitacora();
        assertThat(filas).extracting(f -> f.get("accion")).containsExactly("CREAR_ADMINISTRADOR", "CONFIGURAR_SEGUNDO_FACTOR", "INICIAR_SESION");
        for (Map<String, Object> f : filas.subList(1, 3)) {
            assertThat(f.get("actor_tipo")).isEqualTo("ADMINISTRADOR");
            assertThat(f.get("administrador_id").toString()).isEqualTo(id);
            assertThat((String) f.get("ip")).isNotBlank();
        }
        assertThat(filas.get(2).get("detalle")).isEqualTo("segundo_factor=app");
    }

    /** Con la base sola no se pueden generar códigos: el secreto se guarda cifrado con MASTER_KEY. */
    @Test void elSecretoNoQuedaEnClaroEnLaBase() {
        String desafio = (String) datos(login()).get("desafio");
        String secreto = (String) datos(post(http, "/v1/admin/auth/segundo-factor/configurar", "{\"desafio\":\"%s\"}".formatted(desafio))).get("secreto");

        byte[] guardado = jdbc.queryForObject("SELECT secreto_cifrado FROM administrador_segundo_factor", byte[].class);
        assertThat(new String(guardado, StandardCharsets.ISO_8859_1)).doesNotContain(secreto);
        assertThat(guardado).isNotEqualTo(secreto.getBytes(StandardCharsets.US_ASCII));
    }

    // --- Apoyo ---------------------------------------------------------------------------------------------------------------

    private ResponseEntity<Map> login() {
        return post(http, "/v1/admin/auth/login", "{\"email\":\"ana@khipu.pe\",\"password\":\"Segura123\"}");
    }

    private String desafio() { return (String) datos(login()).get("desafio"); }

    private ResponseEntity<Map> verificar(String desafio, String codigo) {
        return post(http, "/v1/admin/auth/segundo-factor/verificar", "{\"desafio\":\"%s\",\"codigo\":\"%s\"}".formatted(desafio, codigo));
    }

    /**
     * El código del paso siguiente: la confirmación ya consumió el paso actual (un código no vale dos veces), y la ventana acepta un
     * paso hacia adelante, como un teléfono con el reloj un poco adelantado.
     */
    private String codigoSiguiente() { return TOTP.codigo(secretoEnClaro(), Instant.now().getEpochSecond() / 30 + 1); }

    @Autowired pe.factura.application.port.out.SecretCipher cifrador;

    private String secretoEnClaro() {
        byte[] cifrado = jdbc.queryForObject("SELECT secreto_cifrado FROM administrador_segundo_factor", byte[].class);
        return new String(cifrador.descifrar(cifrado), StandardCharsets.UTF_8);
    }

    private ResponseEntity<Map> conBearer(String ruta, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return http.exchange(ruta, HttpMethod.GET, new HttpEntity<>(h), Map.class);
    }

    private List<Map<String, Object>> bitacora() { return jdbc.queryForList("SELECT * FROM auditoria_admin ORDER BY ocurrido_en, id"); }

    private static Map<String, Object> datos(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("datos");
    }
}
