package pe.factura.bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #498 de punta a punta, con la base real: el plan Gratis (1 RUC, 1 usuario, 1 API key) deja de ser un texto de la pantalla. La API key, la invitación, la reactivación y la
 * empresa de más se rechazan con 409 {@code CUPO_DEL_PLAN} y un mensaje que dice qué hacer; revocar, desactivar o revocar la invitación liberan el lugar; un plan sin tope no
 * limita; y una cuenta que ya excede su tope sigue entrando y viendo lo suyo.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestRestTemplate
class CupoDelPlanE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    static final List<String> CORREOS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class CorreoDePrueba {
        @Bean @Primary CorreoSender correoQueGuarda() {
            return new CorreoSender() {
                public void enviar(String para, String asunto, String cuerpo) { CORREOS.add(para + "\n" + cuerpo); }
                public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpo); }
            };
        }
    }

    String empresa;
    String admin;

    @BeforeEach void preparar() {
        CORREOS.clear();
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, invitacion, usuario, cuenta, intento_de_acceso CASCADE");
        limites("1", "1", "1");
        admin = registrarVerificado("ana@torres.pe");
        ResponseEntity<Map> creada = http.postForEntity("/v1/empresas",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"FERRETERIA TORRES S.A.C.\",\"entorno\":\"BETA\"}", jwt(admin, null)), Map.class);
        assertThat(creada.getStatusCode()).as("el primer RUC cabe en el plan Gratis: %s", creada.getBody()).isEqualTo(HttpStatus.CREATED);
        empresa = (String) datos(creada).get("id");
    }

    /** El plan es de la base y los demás tests de la clase lo comparten: se deja como nace. */
    @AfterEach void restaurar() { limites("1", "1", "1"); }

    // --- Ayudas -----------------------------------------------------------------------------------------------------------------------

    /** Los topes del plan Gratis, como los pone un administrador: {@code "null"} es «sin tope». */
    private void limites(String rucs, String usuarios, String apiKeys) {
        jdbc.update("UPDATE plan SET rucs = " + rucs + ", usuarios = " + usuarios + ", api_keys = " + apiKeys + " WHERE nombre = 'Gratis'");
    }

    private static Map<String, Object> datos(ResponseEntity<Map> r) { return (Map<String, Object>) r.getBody().get("datos"); }

    private HttpHeaders json() {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders jwt(String access, String empresaId) {
        HttpHeaders h = json(); h.setBearerAuth(access);
        if (empresaId != null) h.set("X-Empresa", empresaId);
        return h;
    }

    private String token(String email, String ruta) {
        String correo = CORREOS.stream().filter(c -> c.startsWith(email + "\n") && c.contains(ruta)).reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no llegó " + ruta + " a " + email + ": " + CORREOS));
        Matcher m = Pattern.compile(Pattern.quote(ruta) + "([A-Za-z0-9_-]+)").matcher(correo);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private String registrarVerificado(String email) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro",
                new HttpEntity<>("{\"nombre\":\"Ferretería Torres\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\",\"acepta_terminos\":true}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/v1/auth/verificar", new HttpEntity<>("{\"token\":\"%s\"}".formatted(token(email, "/verificar/")), json()), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> entrada = http.postForEntity("/v1/auth/login", new HttpEntity<>("{\"email\":\"%s\",\"password\":\"Segura123\"}".formatted(email), json()), Map.class);
        assertThat(entrada.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) datos(entrada).get("access");
    }

    private ResponseEntity<Map> pedir(HttpMethod metodo, String ruta, String cuerpo) {
        return http.exchange(ruta, metodo, new HttpEntity<>(cuerpo, jwt(admin, empresa)), Map.class);
    }

    private ResponseEntity<Map> invitar(String email) {
        return http.postForEntity("/v1/cuenta/invitaciones", new HttpEntity<>("{\"email\":\"%s\",\"rol\":\"EMISOR\"}".formatted(email), jwt(admin, null)), Map.class);
    }

    /** Invita y acepta con el enlace del correo: el usuario queda creado y activo. */
    private String sumar(String email) {
        assertThat(invitar(email).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/v1/auth/invitacion/aceptar", new HttpEntity<>("{\"token\":\"%s\",\"password\":\"Clave1234\"}".formatted(token(email, "/invitacion/")), json()), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return jdbc.queryForObject("SELECT id::text FROM usuario WHERE email = ?", String.class, email);
    }

    private void revocarInvitacion(String email) {
        String id = jdbc.queryForObject("SELECT id::text FROM invitacion WHERE email = ? AND estado = 'PENDIENTE'", String.class, email);
        assertThat(http.exchange("/v1/cuenta/invitaciones/" + id, HttpMethod.DELETE, new HttpEntity<>(jwt(admin, null)), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private void cupoLleno(ResponseEntity<Map> r, String... mensaje) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("codigo", "CUPO_DEL_PLAN");
        for (String parte : mensaje) assertThat(r.getBody().get("mensaje").toString()).contains(parte);
    }

    private Map<String, Object> miCuenta() {
        return datos(http.exchange("/v1/cuenta", HttpMethod.GET, new HttpEntity<>(jwt(admin, null)), Map.class));
    }

    // --- API keys ---------------------------------------------------------------------------------------------------------------------

    @Test void laSegundaApiKeyDelPlanGratisSeRechazaYRevocarLaPrimeraLiberaElLugar() {
        ResponseEntity<Map> primera = pedir(HttpMethod.POST, "/v1/empresa/api-keys", null);
        assertThat(primera.getStatusCode()).as("%s", primera.getBody()).isEqualTo(HttpStatus.CREATED);

        cupoLleno(pedir(HttpMethod.POST, "/v1/empresa/api-keys", null), "Gratis", "1 API key", "Revoca");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_key WHERE activa", Integer.class)).as("el rechazo no dejó una key creada").isEqualTo(1);

        String id = jdbc.queryForObject("SELECT id::text FROM api_key WHERE activa", String.class);
        assertThat(http.exchange("/v1/empresa/api-keys/" + id, HttpMethod.DELETE, new HttpEntity<>(jwt(admin, empresa)), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(pedir(HttpMethod.POST, "/v1/empresa/api-keys", null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test void unPlanSinTopeDeApiKeysNoLimita() {
        limites("1", "1", "NULL");
        for (int i = 0; i < 3; i++) assertThat(pedir(HttpMethod.POST, "/v1/empresa/api-keys", null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // --- RUCs -------------------------------------------------------------------------------------------------------------------------

    @Test void unaEmpresaMasQueLosRucsDelPlanSeRechazaYSubirElTopeLaDeja() {
        String otra = "{\"ruc\":\"20131312955\",\"razon_social\":\"OTRA EMPRESA S.A.C.\",\"entorno\":\"BETA\"}";

        cupoLleno(http.postForEntity("/v1/empresas", new HttpEntity<>(otra, jwt(admin, null)), Map.class), "Gratis", "1 RUC", "cambio de plan");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant", Integer.class)).as("la empresa rechazada no quedó creada").isEqualTo(1);

        limites("2", "1", "1");
        assertThat(http.postForEntity("/v1/empresas", new HttpEntity<>(otra, jwt(admin, null)), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // --- Usuarios ---------------------------------------------------------------------------------------------------------------------

    @Test void conUnSoloUsuarioEnElPlanNoSePuedeInvitarYElCorreoNoSale() {
        cupoLleno(invitar("caro@estudio.pe"), "Gratis", "1 usuario", "invitación pendiente");

        assertThat(CORREOS.stream().filter(c -> c.startsWith("caro@estudio.pe\n"))).as("el rechazo no manda el correo").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invitacion", Integer.class)).isZero();
    }

    /** La invitación aparta su lugar al mandarse, no al aceptarse: si no, se podrían mandar más invitaciones que lugares y la última aceptada rompería el tope. */
    @Test void laInvitacionPendienteOcupaLugarYRevocarlaLoLibera() {
        limites("1", "2", "1");
        assertThat(invitar("caro@estudio.pe").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        cupoLleno(invitar("dani@estudio.pe"), "2 usuarios");

        revocarInvitacion("caro@estudio.pe");
        assertThat(invitar("dani@estudio.pe").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test void reactivarAUnUsuarioNecesitaLugarYUnaInvitacionPendienteLoTiene() {
        limites("1", "2", "1");
        String caroId = sumar("caro@estudio.pe");
        assertThat(pedir(HttpMethod.POST, "/v1/cuenta/usuarios/" + caroId + "/desactivar", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(invitar("dani@estudio.pe").getStatusCode()).as("desactivar liberó el lugar de caro").isEqualTo(HttpStatus.CREATED);

        cupoLleno(pedir(HttpMethod.POST, "/v1/cuenta/usuarios/" + caroId + "/reactivar", null), "2 usuarios");
        assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?::uuid", Boolean.class, caroId)).isFalse();

        revocarInvitacion("dani@estudio.pe");
        assertThat(pedir(HttpMethod.POST, "/v1/cuenta/usuarios/" + caroId + "/reactivar", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Bajarle el plan a una cuenta con 2 usuarios en un plan de 1 no la rompe: sigue entrando y viendo lo suyo, solo no puede sumar más. */
    @Test void unaCuentaQueYaExcedeSuTopeSigueFuncionandoPeroNoSumaMas() {
        limites("1", "2", "1");
        sumar("caro@estudio.pe");
        limites("1", "1", "1");

        assertThat(miCuenta()).extracting(m -> ((Map<String, Object>) m.get("uso")).get("usuarios")).isEqualTo(2);
        assertThat(http.postForEntity("/v1/auth/login", new HttpEntity<>("{\"email\":\"caro@estudio.pe\",\"password\":\"Clave1234\"}", json()), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        cupoLleno(invitar("dani@estudio.pe"), "incluye 1 usuario", "ya tienes 2 usuarios");
    }

    // --- Lo que la cuenta ve ----------------------------------------------------------------------------------------------------------

    @Test void miCuentaDiceCuantoOcupaDeCadaTope() {
        assertThat(pedir(HttpMethod.POST, "/v1/empresa/api-keys", null).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> uso = (Map<String, Object>) miCuenta().get("uso");

        assertThat(uso).containsEntry("api_keys", 1).containsEntry("usuarios", 1).containsEntry("rucs", 1);
    }
}
