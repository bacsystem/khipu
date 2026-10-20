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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #21 de punta a punta: un administrador invita a un emisor y a alguien de solo lectura, cada uno acepta desde el enlace del correo y entra con su rol, y la API aplica ese
 * rol en cada petición. Con la base real: el rol que manda es el de la base, no el del token.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class EquipoE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

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
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, intento_de_acceso CASCADE");
        admin = registrarVerificado("ana@torres.pe");
        ResponseEntity<Map> creada = http.postForEntity("/v1/empresas",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"FERRETERIA TORRES S.A.C.\",\"entorno\":\"BETA\"}", jwt(admin, null)), Map.class);
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        empresa = (String) datos(creada).get("id");
    }

    // --- Ayudas -----------------------------------------------------------------------------------------------------------------------

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
                new HttpEntity<>("{\"nombre\":\"Ferretería Torres\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/v1/auth/verificar", new HttpEntity<>("{\"token\":\"%s\"}".formatted(token(email, "/verificar/")), json()), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return login(email, "Segura123");
    }

    private ResponseEntity<Map> intentarLogin(String email, String password) {
        return http.postForEntity("/v1/auth/login", new HttpEntity<>("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password), json()), Map.class);
    }

    private String login(String email, String password) {
        ResponseEntity<Map> r = intentarLogin(email, password);
        assertThat(r.getStatusCode()).as("login de %s: %s", email, r.getBody()).isEqualTo(HttpStatus.OK);
        return (String) datos(r).get("access");
    }

    private ResponseEntity<Map> invitar(String access, String email, String rol) {
        return http.postForEntity("/v1/cuenta/invitaciones", new HttpEntity<>("{\"email\":\"%s\",\"rol\":\"%s\"}".formatted(email, rol), jwt(access, null)), Map.class);
    }

    private ResponseEntity<Map> aceptar(String token, String password) {
        return http.postForEntity("/v1/auth/invitacion/aceptar", new HttpEntity<>("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password), json()), Map.class);
    }

    /** Invita, acepta con el enlace del correo y entra: devuelve el access token del invitado. */
    private String sumar(String email, String rol) {
        assertThat(invitar(admin, email, rol).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(aceptar(token(email, "/invitacion/"), "Clave1234").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return login(email, "Clave1234");
    }

    private ResponseEntity<Map> pedir(HttpMethod metodo, String ruta, String access, String cuerpo) {
        return http.exchange(ruta, metodo, new HttpEntity<>(cuerpo, jwt(access, empresa)), Map.class);
    }

    private void prohibidoPorRol(HttpMethod metodo, String ruta, String access, String cuerpo) {
        ResponseEntity<Map> r = pedir(metodo, ruta, access, cuerpo);
        assertThat(r.getStatusCode()).as("%s %s", metodo, ruta).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).as("%s %s", metodo, ruta).containsEntry("codigo", "ROL_INSUFICIENTE");
    }

    static final String SERIE = "{\"tipo\":\"01\",\"serie\":\"F00%d\",\"correlativo_inicial\":0}";
    static final String CLIENTE = "{\"tipo_doc\":\"6\",\"num_doc\":\"20100047226\",\"razon_social\":\"CLIENTE S.A.C.\"}";

    // --- Invitar y aceptar ------------------------------------------------------------------------------------------------------------

    @Test void elInvitadoAceptaEntraConSuRolYElCorreoVerificado() {
        String caro = sumar("caro@estudio.pe", "LECTURA");

        Map<String, Object> me = datos(http.exchange("/v1/auth/me", HttpMethod.GET, new HttpEntity<>(jwt(caro, null)), Map.class));
        assertThat(me).containsEntry("rol", "LECTURA").containsEntry("correo_verificado", true).containsEntry("email", "caro@estudio.pe");
        assertThat(me.get("cuenta_id")).isEqualTo(datos(http.exchange("/v1/auth/me", HttpMethod.GET, new HttpEntity<>(jwt(admin, null)), Map.class)).get("cuenta_id"));
        // Ve las empresas de la cuenta que lo invitó.
        assertThat((List<?>) http.exchange("/v1/empresas", HttpMethod.GET, new HttpEntity<>(jwt(caro, null)), Map.class).getBody().get("datos")).hasSize(1);
    }

    @Test void elEnlaceSeVeAntesDeAceptarYSirveUnaSolaVez() {
        invitar(admin, "beto@torres.pe", "EMISOR");
        String token = token("beto@torres.pe", "/invitacion/");

        ResponseEntity<Map> vista = http.postForEntity("/v1/auth/invitacion", new HttpEntity<>("{\"token\":\"%s\"}".formatted(token), json()), Map.class);
        assertThat(datos(vista)).containsEntry("email", "beto@torres.pe").containsEntry("cuenta", "Ferretería Torres").containsEntry("rol", "EMISOR");

        assertThat(aceptar(token, "Clave1234").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> otraVez = aceptar(token, "Otra12345");
        assertThat(otraVez.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(otraVez.getBody()).containsEntry("codigo", "TOKEN_INVALIDO");
        login("beto@torres.pe", "Clave1234");
    }

    @Test void unaInvitacionRevocadaOVencidaNoSirve() {
        invitar(admin, "beto@torres.pe", "EMISOR");
        String revocada = token("beto@torres.pe", "/invitacion/");
        String id = (String) ((Map<String, Object>) ((List<?>) http.exchange("/v1/cuenta/invitaciones", HttpMethod.GET, new HttpEntity<>(jwt(admin, null)), Map.class)
                .getBody().get("datos")).get(0)).get("id");
        assertThat(http.exchange("/v1/cuenta/invitaciones/" + id, HttpMethod.DELETE, new HttpEntity<>(jwt(admin, null)), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(aceptar(revocada, "Clave1234").getBody()).containsEntry("codigo", "TOKEN_INVALIDO");

        invitar(admin, "caro@estudio.pe", "LECTURA");
        String vencida = token("caro@estudio.pe", "/invitacion/");
        jdbc.update("UPDATE invitacion SET expira_en = now() - interval '1 minute' WHERE email = 'caro@estudio.pe'");
        assertThat(aceptar(vencida, "Clave1234").getBody()).containsEntry("codigo", "TOKEN_INVALIDO");
        assertThat(intentarLogin("beto@torres.pe", "Clave1234").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(intentarLogin("caro@estudio.pe", "Clave1234").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void reenviarInvalidaElEnlaceAnterior() {
        invitar(admin, "beto@torres.pe", "EMISOR");
        String primero = token("beto@torres.pe", "/invitacion/");
        String id = (String) ((Map<String, Object>) ((List<?>) http.exchange("/v1/cuenta/invitaciones", HttpMethod.GET, new HttpEntity<>(jwt(admin, null)), Map.class)
                .getBody().get("datos")).get(0)).get("id");

        assertThat(http.postForEntity("/v1/cuenta/invitaciones/" + id + "/reenviar", new HttpEntity<>(jwt(admin, null)), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(aceptar(primero, "Clave1234").getBody()).containsEntry("codigo", "TOKEN_INVALIDO");
        assertThat(aceptar(token("beto@torres.pe", "/invitacion/"), "Clave1234").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test void noSeInvitaAQuienYaTieneUsuario() {
        registrarVerificado("otro@negocio.pe");
        assertThat(invitar(admin, "otro@negocio.pe", "EMISOR").getBody()).containsEntry("codigo", "USUARIO_EXISTENTE");
        assertThat(invitar(admin, "ana@torres.pe", "EMISOR").getBody()).containsEntry("codigo", "YA_ES_MIEMBRO");
    }

    // --- Permisos por rol -----------------------------------------------------------------------------------------------------------

    @Test void soloLecturaMiraPeroNoEmiteNiConfigura() {
        String caro = sumar("caro@estudio.pe", "LECTURA");

        assertThat(pedir(HttpMethod.GET, "/v1/facturas", caro, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pedir(HttpMethod.GET, "/v1/series", caro, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        prohibidoPorRol(HttpMethod.POST, "/v1/facturas", caro, "{}");
        prohibidoPorRol(HttpMethod.POST, "/v1/clientes", caro, CLIENTE);
        prohibidoPorRol(HttpMethod.POST, "/v1/series", caro, SERIE.formatted(1));
        prohibidoPorRol(HttpMethod.GET, "/v1/empresa/api-keys", caro, null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM serie", Integer.class)).isZero();
    }

    @Test void elEmisorEmiteYGuardaClientesPeroNoTocaLaEmpresaNiLasKeysNiElEquipo() {
        String beto = sumar("beto@torres.pe", "EMISOR");

        assertThat(pedir(HttpMethod.POST, "/v1/clientes", beto, CLIENTE).getStatusCode().is2xxSuccessful()).isTrue();
        prohibidoPorRol(HttpMethod.POST, "/v1/series", beto, SERIE.formatted(1));
        prohibidoPorRol(HttpMethod.PUT, "/v1/empresa/datos-fiscales", beto, "{}");
        prohibidoPorRol(HttpMethod.PUT, "/v1/empresa/credenciales-sol", beto, "{\"usuario\":\"MODDATOS\",\"clave\":\"moddatos\"}");
        prohibidoPorRol(HttpMethod.POST, "/v1/empresa/api-keys", beto, "{}");
        prohibidoPorRol(HttpMethod.GET, "/v1/empresa/api-keys", beto, null);
        prohibidoPorRol(HttpMethod.POST, "/v1/empresas", beto, "{\"ruc\":\"20100047226\",\"razon_social\":\"OTRA S.A.C.\",\"entorno\":\"BETA\"}");
        prohibidoPorRol(HttpMethod.POST, "/v1/cuenta/invitaciones", beto, "{\"email\":\"x@y.pe\",\"rol\":\"ADMIN\"}");
        prohibidoPorRol(HttpMethod.GET, "/v1/cuenta/usuarios", beto, null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM serie", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant", Integer.class)).isEqualTo(1);
    }

    @Test void elAdministradorConfiguraYUnaApiKeySigueSinDependerDeRoles() {
        assertThat(pedir(HttpMethod.POST, "/v1/series", admin, SERIE.formatted(1)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> key = pedir(HttpMethod.POST, "/v1/empresa/api-keys", admin, "{}");
        assertThat(key.getStatusCode().is2xxSuccessful()).as("%s", key.getBody()).isTrue();

        HttpHeaders conKey = json(); conKey.set("X-Api-Key", (String) datos(key).get("api_key"));
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>(SERIE.formatted(2), conKey), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** El rol vale desde la siguiente petición, con la misma sesión: el de la base manda, no el del token. */
    @Test void cambiarElRolValeEnLaSiguientePeticion() {
        String beto = sumar("beto@torres.pe", "EMISOR");
        String betoId = (String) datos(http.exchange("/v1/auth/me", HttpMethod.GET, new HttpEntity<>(jwt(beto, null)), Map.class)).get("id");
        prohibidoPorRol(HttpMethod.POST, "/v1/series", beto, SERIE.formatted(1));

        assertThat(pedir(HttpMethod.PUT, "/v1/cuenta/usuarios/" + betoId + "/rol", admin, "{\"rol\":\"ADMIN\"}").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pedir(HttpMethod.POST, "/v1/series", beto, SERIE.formatted(1)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(pedir(HttpMethod.PUT, "/v1/cuenta/usuarios/" + betoId + "/rol", admin, "{\"rol\":\"LECTURA\"}").getStatusCode()).isEqualTo(HttpStatus.OK);
        prohibidoPorRol(HttpMethod.POST, "/v1/clientes", beto, CLIENTE);
    }

    @Test void laCuentaNoSeQuedaSinAdministrador() {
        String anaId = (String) datos(http.exchange("/v1/auth/me", HttpMethod.GET, new HttpEntity<>(jwt(admin, null)), Map.class)).get("id");

        ResponseEntity<Map> r = pedir(HttpMethod.PUT, "/v1/cuenta/usuarios/" + anaId + "/rol", admin, "{\"rol\":\"EMISOR\"}");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("codigo", "ULTIMO_ADMINISTRADOR");
        ResponseEntity<Map> desactivarse = pedir(HttpMethod.POST, "/v1/cuenta/usuarios/" + anaId + "/desactivar", admin, null);
        assertThat(desactivarse.getBody()).containsEntry("codigo", "NO_PUEDES_DESACTIVARTE");
        assertThat(jdbc.queryForObject("SELECT rol FROM usuario WHERE email = 'ana@torres.pe'", String.class)).isEqualTo("ADMIN");
    }

    @Test void unUsuarioDesactivadoQuedaAfueraConLaSesionQueTenia() {
        String caro = sumar("caro@estudio.pe", "LECTURA");
        String caroId = (String) datos(http.exchange("/v1/auth/me", HttpMethod.GET, new HttpEntity<>(jwt(caro, null)), Map.class)).get("id");

        assertThat(pedir(HttpMethod.POST, "/v1/cuenta/usuarios/" + caroId + "/desactivar", admin, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(pedir(HttpMethod.GET, "/v1/facturas", caro, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(intentarLogin("caro@estudio.pe", "Clave1234").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sesion s JOIN usuario u ON u.id = s.usuario_id WHERE u.email = 'caro@estudio.pe' AND NOT s.revocada", Integer.class))
                .isZero();

        assertThat(pedir(HttpMethod.POST, "/v1/cuenta/usuarios/" + caroId + "/reactivar", admin, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        login("caro@estudio.pe", "Clave1234");
    }
}
