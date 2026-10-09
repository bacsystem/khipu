package pe.factura.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** Los correos que salieron: el enlace de verificación (#22) se toma de aquí, como lo haría el usuario. */
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

    @BeforeEach void limpiar() {
        CORREOS.clear();
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta, intento_de_acceso CASCADE");
    }

    /** El token del último enlace de verificación que llegó a {@code email}. */
    private String enlaceDeVerificacion(String email) {
        String correo = CORREOS.stream().filter(c -> c.startsWith(email + "\n") && c.contains("/verificar/")).reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no llegó un enlace de verificación a " + email + ": " + CORREOS));
        Matcher m = Pattern.compile("/verificar/([A-Za-z0-9_-]+)").matcher(correo);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private ResponseEntity<Map> verificar(String token) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/v1/auth/verificar", new HttpEntity<>("{\"token\":\"%s\"}".formatted(token), h), Map.class);
    }

    /** Registro y verificación del correo con el enlace que llegó: lo que hace falta para crear empresas (#22). */
    private Cuenta registrarVerificado(String nombre, String email) {
        Cuenta c = registrar(nombre, email);
        assertThat(verificar(enlaceDeVerificacion(email)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return c;
    }

    private ResponseEntity<Map> crearEmpresa(HttpHeaders h) {
        return http.postForEntity("/v1/empresas",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", h), Map.class);
    }

    // --- #22: verificación del correo ---------------------------------------------------------------------------------------------

    @Test void sinVerificarElCorreoSePuedeMirarPeroNoCrearEmpresas() {
        Cuenta cuenta = registrar("Mi negocio", "ana@negocio.pe");
        HttpHeaders h = conJwt(cuenta.access());

        ResponseEntity<Map> me = http.exchange("/v1/auth/me", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) me.getBody().get("datos")).get("correo_verificado")).isEqualTo(false);
        assertThat(http.exchange("/v1/empresas", HttpMethod.GET, new HttpEntity<>(h), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> creada = crearEmpresa(h);
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(creada.getBody()).containsEntry("codigo", "CORREO_SIN_VERIFICAR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant", Integer.class)).isZero();
    }

    /** El mismo token de sesión: verificado en otra pestaña, la siguiente escritura ya pasa sin volver a entrar. */
    @Test void conElEnlaceDelCorreoQuedaVerificadoYYaPuedeCrearEmpresas() {
        Cuenta cuenta = registrar("Mi negocio", "ana@negocio.pe");

        assertThat(verificar(enlaceDeVerificacion("ana@negocio.pe")).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        HttpHeaders h = conJwt(cuenta.access());
        assertThat(((Map<?, ?>) http.exchange("/v1/auth/me", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos")).get("correo_verificado")).isEqualTo(true);
        assertThat(crearEmpresa(h).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test void elEnlaceSirveUnaSolaVez() {
        registrar("Mi negocio", "ana@negocio.pe");
        String token = enlaceDeVerificacion("ana@negocio.pe");
        verificar(token);

        ResponseEntity<Map> otraVez = verificar(token);
        assertThat(otraVez.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(otraVez.getBody()).containsEntry("codigo", "TOKEN_INVALIDO");
    }

    @Test void sePuedePedirOtroEnlaceYElNuevoTambienVerifica() {
        Cuenta cuenta = registrar("Mi negocio", "ana@negocio.pe");
        String primero = enlaceDeVerificacion("ana@negocio.pe");

        ResponseEntity<Void> reenvio = http.postForEntity("/v1/auth/verificacion", new HttpEntity<>(conJwt(cuenta.access())), Void.class);

        assertThat(reenvio.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String segundo = enlaceDeVerificacion("ana@negocio.pe");
        assertThat(segundo).isNotEqualTo(primero);
        assertThat(verificar(segundo).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.postForEntity("/v1/auth/verificacion", new HttpEntity<>(conJwt(cuenta.access())), Map.class).getStatusCode())
                .as("ya verificado").isEqualTo(HttpStatus.CONFLICT);
    }

    /** El bloqueo es de la sesión del portal: una API key (de un integrador o de un alta asistida) escribe como siempre. */
    @Test void unaApiKeyNoDependeDeLaVerificacion() {
        HttpHeaders admin = new HttpHeaders(); admin.set("X-Platform-Key", "plataforma-test"); admin.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> creado = http.postForEntity("/v1/admin/tenants",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}", admin), Map.class);
        HttpHeaders conKey = new HttpHeaders(); conKey.setContentType(MediaType.APPLICATION_JSON);
        conKey.set("X-Api-Key", (String) ((Map<?, ?>) creado.getBody().get("datos")).get("api_key"));

        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"01\",\"serie\":\"F001\",\"correlativo_inicial\":0}", conKey), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** Sin verificar, el correo puede no ser suyo: 5 enlaces por día con el del registro, y el sexto pedido no manda nada. */
    @Test void elReenvioTieneTope() {
        Cuenta cuenta = registrar("Mi negocio", "ana@negocio.pe");
        for (int i = 0; i < 4; i++)
            assertThat(http.postForEntity("/v1/auth/verificacion", new HttpEntity<>(conJwt(cuenta.access())), Void.class).getStatusCode())
                    .isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<Map> sexto = http.postForEntity("/v1/auth/verificacion", new HttpEntity<>(conJwt(cuenta.access())), Map.class);

        assertThat(sexto.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(sexto.getBody()).containsEntry("codigo", "DEMASIADOS_ENLACES");
        assertThat(CORREOS).hasSize(5);
    }

    private record Cuenta(String access, String refresh, String cuentaId) {}

    private Cuenta registrar(String nombre, String email) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro",
                new HttpEntity<>("{\"nombre\":\"%s\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(nombre, email), h), Map.class);
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
        Cuenta cuenta = registrarVerificado("Mi negocio", "ana@negocio.pe");
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
        Cuenta cuenta = registrarVerificado("Mi negocio", "ana@negocio.pe");
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
        Cuenta cuentaA = registrarVerificado("Negocio A", "a@negocio.pe");
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

    // --- #261: límite de intentos ---------------------------------------------------------------------------------------------------

    private ResponseEntity<Map> login(String email, String password) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/v1/auth/login", new HttpEntity<>("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password), h), Map.class);
    }

    @Test void trasCincoContrasenasErroneasNiLaCorrectaEntraDuranteQuinceMinutos() {
        registrar("Mi negocio", "ana@negocio.pe");
        for (int i = 0; i < 5; i++) assertThat(login("ana@negocio.pe", "incorrecta").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> bloqueado = login("ana@negocio.pe", "Segura123");

        assertThat(bloqueado.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(bloqueado.getBody()).containsEntry("codigo", "DEMASIADOS_INTENTOS_LOGIN");
        assertThat(login("otra@negocio.pe", "incorrecta").getStatusCode()).as("otro correo no se ve afectado").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void laRecuperacionMandaComoMuchoTresCorreosPorHoraYSiempreResponde202() {
        registrar("Mi negocio", "ana@negocio.pe");
        CORREOS.clear();
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        for (int i = 0; i < 5; i++)
            assertThat(http.postForEntity("/v1/auth/recuperar", new HttpEntity<>("{\"email\":\"ana@negocio.pe\"}", h), Void.class).getStatusCode())
                    .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(CORREOS).hasSize(3);
    }

    @Test void recuperarSiempreDevuelve202() {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> r = http.postForEntity("/v1/auth/recuperar", new HttpEntity<>("{\"email\":\"no-existe@negocio.pe\"}", h), Void.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
}
