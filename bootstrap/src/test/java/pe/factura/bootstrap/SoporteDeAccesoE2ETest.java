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
 * Forzar el restablecimiento de contraseña y reenviar la verificación (#183) de extremo a extremo: HTTP real, filtros reales, Postgres real y
 * un correo que se captura al salir. Lo que importa: el usuario puede **usar el enlace que llegó** (cambiar su contraseña, verificar su correo),
 * el administrador nunca ve ni fija una contraseña ni recibe el enlace, y todo queda en la bitácora sin el token.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class SoporteDeAccesoE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Los correos que salieron, y dos interruptores: si el servidor entrega de verdad, y si el servidor de correo rechaza el envío. */
    static final List<String> CORREOS = new CopyOnWriteArrayList<>();
    static volatile boolean entrega = true;
    static volatile boolean rechaza = false;

    @TestConfiguration
    static class CorreoDePrueba {
        @Bean @Primary CorreoSender correoQueGuarda() {
            return new CorreoSender() {
                public void enviar(String para, String asunto, String cuerpo) {
                    if (rechaza) throw new IllegalStateException("550 buzón no disponible");
                    CORREOS.add(para + "\n" + asunto + "\n" + cuerpo);
                }
                public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpo); }
                @Override public boolean entregaDeVerdad() { return entrega; }
            };
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        CORREOS.clear();
        entrega = true;
        rechaza = false;
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    record Cliente(String email, String access, UUID cuentaId, UUID usuarioId) {}

    /** Una cuenta real que se acaba de registrar: su correo SIN verificar. */
    private Cliente registrado(String email) {
        ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/auth/registro", json(),
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        UUID usuario = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
        UUID cuenta = jdbc.queryForObject("SELECT cuenta_id FROM usuario WHERE email = ?", UUID.class, email);
        CORREOS.clear();   // el correo de verificación del propio registro no es lo que se prueba
        return new Cliente(email, access, cuenta, usuario);
    }

    private ResponseEntity<Map> restablecer(HttpHeaders h, UUID cuenta, UUID usuario) {
        return llamar(HttpMethod.POST, "/v1/admin/cuentas/" + cuenta + "/usuarios/" + usuario + "/restablecimiento", h, null);
    }

    private ResponseEntity<Map> reenviar(HttpHeaders h, UUID cuenta, UUID usuario) {
        return llamar(HttpMethod.POST, "/v1/admin/cuentas/" + cuenta + "/usuarios/" + usuario + "/verificacion", h, null);
    }

    /** El token del último enlace con {@code ruta} que llegó a {@code email}, tal como lo usaría el usuario. */
    private String enlace(String email, String ruta) {
        String correo = CORREOS.stream().filter(c -> c.startsWith(email + "\n") && c.contains(ruta)).reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no llegó un enlace " + ruta + " a " + email + ": " + CORREOS));
        Matcher m = Pattern.compile(Pattern.quote(ruta) + "([A-Za-z0-9_-]+)").matcher(correo);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private long contar(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    /** Solo lo que dejan estas dos acciones: dar de alta un tenant o registrarse deja sus propias filas, que no son lo que se prueba. */
    private long enLaBitacora() { return jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion IN ('ENVIAR_RESTABLECIMIENTO', 'REENVIAR_VERIFICACION')", Long.class); }

    @Test void elUsuarioUsaElEnlaceQueLlegoYElige_SuContrasena_ElAdministradorNoVeNada() {
        Cliente c = registrado("ana@negocio.pe");

        ResponseEntity<Map> r = restablecer(conClaveDePlataforma(), c.cuentaId(), c.usuarioId());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> datos = (Map<String, Object>) r.getBody().get("datos");
        assertThat(datos).containsEntry("correo", "ana@negocio.pe").containsEntry("usuario_id", c.usuarioId().toString());
        String token = enlace("ana@negocio.pe", "/restablecer/");
        // El administrador no recibe nada con lo que entrar: ni el token, ni el enlace, ni una contraseña.
        assertThat(r.getBody().toString()).doesNotContain(token).doesNotContain("/restablecer/").doesNotContainIgnoringCase("password");

        // El usuario abre su enlace y elige SU contraseña.
        ResponseEntity<Map> cambio = llamar(HttpMethod.POST, "/v1/auth/restablecer", json(), "{\"token\":\"%s\",\"password\":\"NuevaClave456\"}".formatted(token));
        assertThat(cambio.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(llamar(HttpMethod.POST, "/v1/auth/login", json(), "{\"email\":\"ana@negocio.pe\",\"password\":\"NuevaClave456\"}").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(llamar(HttpMethod.POST, "/v1/auth/login", json(), "{\"email\":\"ana@negocio.pe\",\"password\":\"Segura123\"}").getStatusCode()).as("la anterior ya no sirve").isEqualTo(HttpStatus.UNAUTHORIZED);
        // El enlace es de un solo uso.
        assertThat(llamar(HttpMethod.POST, "/v1/auth/restablecer", json(), "{\"token\":\"%s\",\"password\":\"OtraClave789\"}".formatted(token)).getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test void elReenvioDeLaVerificacionLeLlegaAlUsuarioYLaPuedeUsar() {
        Cliente c = registrado("ana@negocio.pe");
        assertThat(jdbc.queryForObject("SELECT correo_verificado_at IS NULL FROM usuario WHERE id = ?", Boolean.class, c.usuarioId())).isTrue();

        ResponseEntity<Map> r = reenviar(conClaveDePlataforma(), c.cuentaId(), c.usuarioId());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = enlace("ana@negocio.pe", "/verificar/");
        assertThat(r.getBody().toString()).doesNotContain(token);
        assertThat(llamar(HttpMethod.POST, "/v1/auth/verificar", json(), "{\"token\":\"%s\"}".formatted(token)).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(jdbc.queryForObject("SELECT correo_verificado_at IS NOT NULL FROM usuario WHERE id = ?", Boolean.class, c.usuarioId())).isTrue();

        // Ya verificado: no hay nada que reenviar.
        CORREOS.clear();
        ResponseEntity<Map> otra = reenviar(conClaveDePlataforma(), c.cuentaId(), c.usuarioId());
        assertThat(otra.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(otra.getBody()).containsEntry("codigo", "CORREO_YA_VERIFICADO");
        assertThat(CORREOS).isEmpty();
    }

    @Test void unUsuarioYaVerificadoPuedeRestablecerSuContrasena() {
        Cliente c = registrado("ana@negocio.pe");
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE id = ?", c.usuarioId());

        assertThat(restablecer(conClaveDePlataforma(), c.cuentaId(), c.usuarioId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(CORREOS).hasSize(1);
    }

    // --- bitácora ------------------------------------------------------------------------------------------------------------------

    @Test void lasDosAccionesQuedanEnLaBitacoraSinElToken() {
        Cliente c = registrado("ana@negocio.pe");

        restablecer(conClaveDePlataforma(), c.cuentaId(), c.usuarioId());
        String tokenRestablecer = enlace("ana@negocio.pe", "/restablecer/");
        reenviar(conClaveDePlataforma(), c.cuentaId(), c.usuarioId());
        String tokenVerificar = enlace("ana@negocio.pe", "/verificar/");

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT accion, actor_tipo, cuenta_id, detalle FROM auditoria_admin ORDER BY ocurrido_en, id");
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("accion", "ENVIAR_RESTABLECIMIENTO").containsEntry("actor_tipo", "CLAVE_PLATAFORMA")
                .containsEntry("cuenta_id", c.cuentaId()).containsEntry("detalle", "usuario=ana@negocio.pe");
        assertThat(filas.get(1)).containsEntry("accion", "REENVIAR_VERIFICACION").containsEntry("cuenta_id", c.cuentaId()).containsEntry("detalle", "usuario=ana@negocio.pe");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE detalle LIKE '%' || ? || '%' OR detalle LIKE '%' || ? || '%'", Long.class,
                tokenRestablecer, tokenVerificar)).as("el token no llega a la bitácora").isZero();
    }

    @Test void unAdministradorConSesionDejaSuNombreEnLaBitacora() {
        Cliente c = registrado("ana@negocio.pe");
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String token = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");

        ResponseEntity<Map> r = restablecer(conBearer(token), c.cuentaId(), c.usuarioId());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT actor_tipo FROM auditoria_admin WHERE accion = 'ENVIAR_RESTABLECIMIENTO'", String.class)).isEqualTo("ADMINISTRADOR");
    }

    // --- cuando algo falla --------------------------------------------------------------------------------------------------------

    @Test void unUsuarioDeOtraCuentaNoSeEncuentraPorLaRutaDeEsta() {
        Cliente a = registrado("ana@negocio.pe");
        Cliente b = registrado("luis@otro.pe");

        ResponseEntity<Map> r = restablecer(conClaveDePlataforma(), a.cuentaId(), b.usuarioId());
        ResponseEntity<Map> v = reenviar(conClaveDePlataforma(), a.cuentaId(), b.usuarioId());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(v.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(CORREOS).isEmpty();
        assertThat(contar("token_recuperacion")).isZero();
        assertThat(contar("token_verificacion")).as("solo los dos del registro, ninguno por la acción").isEqualTo(2);
        assertThat(enLaBitacora()).isZero();
    }

    @Test void unUsuarioQueNoExisteEs404YUnIdMalFormadoEs400() {
        Cliente c = registrado("ana@negocio.pe");

        assertThat(restablecer(conClaveDePlataforma(), c.cuentaId(), UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/usuarios/no-es-un-uuid/restablecimiento", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void unUsuarioDesactivadoNoRecibeNada() {
        Cliente c = registrado("ana@negocio.pe");
        jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", c.usuarioId());

        ResponseEntity<Map> r = restablecer(conClaveDePlataforma(), c.cuentaId(), c.usuarioId());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("codigo", "USUARIO_INACTIVO");
        assertThat(CORREOS).isEmpty();
    }

    /** Sin SMTP el adaptador no lanza: decir «enviado» sería mentir. No se crea nada, y se dice. */
    @Test void sinCorreoConfiguradoEnElServidorNoSeCreaNadaYSeResponde503() {
        Cliente c = registrado("ana@negocio.pe");
        entrega = false;

        ResponseEntity<Map> r = restablecer(conClaveDePlataforma(), c.cuentaId(), c.usuarioId());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody()).containsEntry("codigo", "CORREO_NO_CONFIGURADO");
        assertThat(contar("token_recuperacion")).isZero();
        assertThat(contar("auditoria_admin")).isZero();
    }

    @Test void siElServidorDeCorreoRechazaElEnvioSeResponde502YElIntentoQuedaEnLaBitacora() {
        Cliente c = registrado("ana@negocio.pe");
        rechaza = true;

        ResponseEntity<Map> r = restablecer(conClaveDePlataforma(), c.cuentaId(), c.usuarioId());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(r.getBody()).containsEntry("codigo", "CORREO_NO_ENVIADO");
        assertThat(contar("auditoria_admin")).as("es el intento del administrador").isEqualTo(1);
        assertThat(CORREOS).isEmpty();
    }

    // --- quién puede ---------------------------------------------------------------------------------------------------------------

    /** Es del administrador: ni el dueño de la cuenta, ni una API key, ni una clave errónea pueden mandar correos a nombre de otro. */
    @Test void nadieMasPuedeDispararLosCorreos() {
        Cliente c = registrado("ana@negocio.pe");
        ResponseEntity<Map> tenant = llamar(HttpMethod.POST, "/v1/admin/tenants", conClaveDePlataforma(), "{\"ruc\":\"20100066611\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}");
        HttpHeaders conApiKey = json(); conApiKey.set("X-Api-Key", (String) ((Map<?, ?>) tenant.getBody().get("datos")).get("api_key"));
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        for (HttpHeaders h : List.of(json(), conBearer(c.access()), conApiKey, conClaveErronea)) {
            assertThat(restablecer(h, c.cuentaId(), c.usuarioId()).getStatusCode()).as("restablecer con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(reenviar(h, c.cuentaId(), c.usuarioId()).getStatusCode()).as("reenviar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(CORREOS).isEmpty();
        assertThat(enLaBitacora()).isZero();
    }
}
