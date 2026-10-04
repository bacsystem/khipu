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
 * Alta asistida de un cliente (#188) de extremo a extremo: HTTP real y Postgres real. Lo que importa: que el alta deje al cliente
 * entrando de verdad con la contraseña que él elija, que la API key sirva, que nadie sin credencial de administrador pueda dar de
 * alta a nadie, y que un rechazo no deje nada a medias.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AltaAsistidaE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Correos «enviados»: el e2e necesita leer el enlace de la invitación, que solo viaja por correo. */
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

    static final String ALTA = """
            {"nombre":"Comercial Andina","email":"ana@andina.pe","telefono":"987654321",
             "empresa":{"ruc":"20100066603","razon_social":"COMERCIAL ANDINA SAC","entorno":"BETA"},
             "serie":{"tipo":"01","serie":"F001"}}""";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        CORREOS.clear();
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }
    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }
    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }
    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private ResponseEntity<Map> alta(HttpHeaders h, String cuerpo) { return http.postForEntity("/v1/admin/cuentas", new HttpEntity<>(cuerpo, h), Map.class); }

    private long filas(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    private String tokenDeLaInvitacion() {
        assertThat(CORREOS).as("correos enviados").hasSize(1);
        Matcher m = Pattern.compile("/restablecer/([A-Za-z0-9_-]+)\\?invitacion=1").matcher(CORREOS.get(0));
        assertThat(m.find()).as("enlace de invitación en: " + CORREOS.get(0)).isTrue();
        return m.group(1);
    }

    @Test void elAltaCompletaDejaAlClienteEntrandoConLaContrasenaQueElijaYConSuApiKeyFuncionando() {
        ResponseEntity<Map> r = alta(conClaveDePlataforma(), ALTA);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> datos = (Map<String, Object>) r.getBody().get("datos");
        assertThat(datos).containsEntry("ruc", "20100066603").containsEntry("invitacion_enviada", true);
        assertThat((Map<String, Object>) datos.get("serie")).containsEntry("tipo", "01").containsEntry("serie", "F001");
        String apiKey = (String) datos.get("api_key");
        assertThat(apiKey).startsWith("fk_");

        // La API key es de la empresa recién creada y esa empresa ya tiene su serie.
        ResponseEntity<Map> series = http.exchange("/v1/series", HttpMethod.GET, new HttpEntity<>(conApiKey(apiKey)), Map.class);
        assertThat(series.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) series.getBody().get("datos")).extracting(s -> s.get("serie")).containsExactly("F001");

        // El administrador no eligió contraseña y nadie la conoce: hasta aceptar la invitación, no se entra.
        ResponseEntity<Map> sinInvitacion = http.postForEntity("/v1/auth/login", new HttpEntity<>("{\"email\":\"ana@andina.pe\",\"password\":\"Segura123\"}", json()), Map.class);
        assertThat(sinInvitacion.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // El cliente acepta la invitación: elige su contraseña y entra.
        String token = tokenDeLaInvitacion();
        ResponseEntity<Void> restablecer = http.postForEntity("/v1/auth/restablecer", new HttpEntity<>("{\"token\":\"%s\",\"password\":\"Segura123\"}".formatted(token), json()), Void.class);
        assertThat(restablecer.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> login = http.postForEntity("/v1/auth/login", new HttpEntity<>("{\"email\":\"ana@andina.pe\",\"password\":\"Segura123\"}", json()), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String access = (String) ((Map<?, ?>) login.getBody().get("datos")).get("access");

        // Y ve su empresa: cuenta, usuario y empresa quedaron atados.
        ResponseEntity<Map> empresas = http.exchange("/v1/empresas", HttpMethod.GET, new HttpEntity<>(conBearer(access)), Map.class);
        assertThat((List<Map<String, Object>>) empresas.getBody().get("datos")).extracting(e -> e.get("ruc")).containsExactly("20100066603");

        // La invitación es de un solo uso.
        ResponseEntity<Map> reuso = http.postForEntity("/v1/auth/restablecer", new HttpEntity<>("{\"token\":\"%s\",\"password\":\"Otra12345\"}".formatted(token), json()), Map.class);
        assertThat(reuso.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test void laCuentaNuevaApareceEnElListadoDelBackoffice() {
        alta(conClaveDePlataforma(), ALTA);

        ResponseEntity<Map> r = http.exchange("/v1/admin/cuentas", HttpMethod.GET, new HttpEntity<>(conClaveDePlataforma()), Map.class);
        List<Map<String, Object>> datos = (List<Map<String, Object>>) r.getBody().get("datos");
        assertThat(datos).hasSize(1);
        assertThat(datos.get(0)).containsEntry("email", "ana@andina.pe").containsEntry("nombre", "Comercial Andina").containsEntry("empresas", 1);
    }

    @Test void queDaEnLaBitacoraAVozDeQuienLoHizoSinSecretos() {
        ResponseEntity<Map> r = alta(conClaveDePlataforma(), ALTA);
        Map<String, Object> datos = (Map<String, Object>) r.getBody().get("datos");

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM auditoria_admin");
        assertThat(filas).hasSize(1);
        Map<String, Object> fila = filas.get(0);
        assertThat(fila.get("accion")).isEqualTo("CREAR_CUENTA");
        assertThat(fila.get("actor_tipo")).isEqualTo("CLAVE_PLATAFORMA");
        assertThat(fila.get("cuenta_id").toString()).isEqualTo(datos.get("cuenta_id"));
        assertThat(fila.get("tenant_id").toString()).isEqualTo(datos.get("tenant_id"));
        assertThat((String) fila.get("ip")).isNotBlank();
        assertThat((String) fila.get("detalle")).contains("20100066603", "F001").doesNotContain((String) datos.get("api_key")).doesNotContain("ana@andina.pe");
    }

    @Test void unAdministradorConSesionQuedaRegistradoConSuPropioId() {
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"root@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        Map<String, Object> sesion = SesionAdminDePrueba.entrar(http, "root@khipu.pe", "Segura123");
        String token = (String) sesion.get("access_token");
        String administradorId = (String) ((Map<?, ?>) sesion.get("administrador")).get("id");

        assertThat(alta(conBearer(token), ALTA).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> fila = jdbc.queryForList("SELECT * FROM auditoria_admin WHERE accion = 'CREAR_CUENTA'").get(0);
        assertThat(fila.get("actor_tipo")).isEqualTo("ADMINISTRADOR");
        assertThat(fila.get("administrador_id").toString()).isEqualTo(administradorId);
    }

    /** El alta crea una cuenta con acceso a facturar: solo la clave de plataforma o un administrador, nunca un cliente ni una integración. */
    @Test void soloLaPlataformaOUnAdministradorPuedenDarDeAltaAUnCliente() {
        ResponseEntity<Map> registro = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Otro\",\"email\":\"otro@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}", json()), Map.class);
        // El perfil de prueba abre el registro público (application-test.yml): así hay un JWT de cliente real con el que probar. Si dejara
        // de abrirlo, este assert falla en vez de probar con un token inventado.
        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String jwtDeCliente = (String) ((Map<?, ?>) registro.getBody().get("datos")).get("access");
        ResponseEntity<Map> tenant = http.postForEntity("/v1/admin/tenants", new HttpEntity<>("{\"ruc\":\"20601234565\",\"razon_social\":\"OTRA SAC\",\"entorno\":\"BETA\"}", conClaveDePlataforma()), Map.class);
        String apiKey = (String) ((Map<?, ?>) tenant.getBody().get("datos")).get("api_key");
        long cuentasAntes = filas("cuenta");

        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");
        HttpHeaders soloXForwardedFor = json(); soloXForwardedFor.set("X-Forwarded-For", "203.0.113.7");
        assertThat(alta(json(), ALTA).getStatusCode()).as("sin credencial").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(alta(soloXForwardedFor, ALTA).getStatusCode()).as("solo X-Forwarded-For").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(alta(conBearer(jwtDeCliente), ALTA).getStatusCode()).as("JWT de cliente").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(alta(conApiKey(apiKey), ALTA).getStatusCode()).as("API key de una empresa").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(alta(conClaveErronea, ALTA).getStatusCode()).as("clave de plataforma errónea").isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(filas("cuenta")).as("ninguna alta dejó una cuenta").isEqualTo(cuentasAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE email = 'ana@andina.pe'", Long.class)).isZero();
        assertThat(CORREOS).isEmpty();
    }

    @Test void unCorreoOUnaEmpresaYaRegistradosSeRechazanSinDejarNadaNuevo() {
        assertThat(alta(conClaveDePlataforma(), ALTA).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> mismoCorreo = alta(conClaveDePlataforma(), ALTA.replace("20100066603", "20601234565"));
        assertThat(mismoCorreo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mismoCorreo.getBody().get("codigo")).isEqualTo("DUPLICADO");

        ResponseEntity<Map> mismaEmpresa = alta(conClaveDePlataforma(), ALTA.replace("ana@andina.pe", "otra@andina.pe"));
        assertThat(mismaEmpresa.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(filas("cuenta")).isEqualTo(1);
        assertThat(filas("tenant")).isEqualTo(1);
        assertThat(filas("auditoria_admin")).isEqualTo(1);
        assertThat(CORREOS).hasSize(1);
    }

    @Test void unaSolicitudInvalidaSeRechazaSinDejarNada() {
        List<String> invalidas = List.of(
                ALTA.replace("ana@andina.pe", "no-es-un-correo"),
                ALTA.replace("20100066603", "20100066604"),               // dígito verificador del RUC
                ALTA.replace("\"serie\":\"F001\"", "\"serie\":\"B001\""),   // una factura no lleva serie de boleta
                ALTA.replace("987654321", "12345"));
        for (String cuerpo : invalidas) {
            ResponseEntity<Map> r = alta(conClaveDePlataforma(), cuerpo);
            assertThat(r.getStatusCode().is4xxClientError()).as(cuerpo).isTrue();
        }
        for (String tabla : List.of("cuenta", "usuario", "tenant", "api_key", "serie", "token_recuperacion", "auditoria_admin"))
            assertThat(filas(tabla)).as(tabla).isZero();
        assertThat(CORREOS).isEmpty();
    }
}
