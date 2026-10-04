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
 * Listado de cuentas del backoffice (#180) de extremo a extremo: HTTP real, filtros reales y Postgres real. Lo que más
 * importa es el aislamiento: el listado trae el correo y las empresas de TODOS los clientes, así que solo lo pueden leer la
 * clave de plataforma y un administrador, nunca un cliente ni una integración.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AdminCuentasE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    /** Una cuenta de cliente real, registrada por el portal, con una empresa dada de alta con su JWT. Devuelve su JWT. */
    private String clienteConEmpresa(String email, String ruc, String razonSocial) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Sin verificar el correo no se crea la empresa (#22); el enlace del correo lo prueba AuthE2ETest.
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        ResponseEntity<Map> empresa = http.postForEntity("/v1/empresas", new HttpEntity<>(
                "{\"ruc\":\"%s\",\"razon_social\":\"%s\",\"entorno\":\"BETA\"}".formatted(ruc, razonSocial), conBearer(access)), Map.class);
        assertThat(empresa.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return access;
    }

    private ResponseEntity<Map> listar(HttpHeaders h, String consulta) {
        return http.exchange("/v1/admin/cuentas" + consulta, HttpMethod.GET, new HttpEntity<>(h), Map.class);
    }

    @Test void laClaveDePlataformaVeLasCuentasConSusEmpresasYSuUltimoAcceso() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");

        ResponseEntity<Map> r = listar(conClaveDePlataforma(), "");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        List<Map<String, Object>> datos = (List<Map<String, Object>>) r.getBody().get("datos");
        assertThat(datos).hasSize(1);
        assertThat(datos.get(0)).containsEntry("email", "ana@negocio.pe").containsEntry("nombre", "Mi negocio").containsEntry("empresas", 1);
        assertThat(datos.get(0)).containsKeys("id", "creada_en", "ultimo_acceso");   // el registro abre una sesión
        assertThat(datos.get(0)).doesNotContainKeys("estado", "plan");
    }

    @Test void buscaPorRucYPorRazonSocialYElTotalRefleja() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        clienteConEmpresa("luis@otro.pe", "20100066611", "FERRETERIA LUNA SAC");

        ResponseEntity<Map> porRuc = listar(conClaveDePlataforma(), "?q=2010006660");
        assertThat(porRuc.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(((List<Map<String, Object>>) porRuc.getBody().get("datos")).get(0)).containsEntry("email", "ana@negocio.pe");

        ResponseEntity<Map> porRazon = listar(conClaveDePlataforma(), "?q=luna");
        assertThat(((List<Map<String, Object>>) porRazon.getBody().get("datos")).get(0)).containsEntry("email", "luis@otro.pe");

        ResponseEntity<Map> ninguna = listar(conClaveDePlataforma(), "?q=no-existe");
        assertThat(ninguna.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ninguna.getHeaders().getFirst("X-Total-Count")).isEqualTo("0");
        assertThat((List<?>) ninguna.getBody().get("datos")).isEmpty();
    }

    @Test void unAdministradorConSesionTambienPuedeLeerlo() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String token = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");

        assertThat(listar(conBearer(token), "").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- #181: detalle ------------------------------------------------------------------------------------------------------------

    private String cuentaIdDe(String email) {
        List<Map<String, Object>> datos = (List<Map<String, Object>>) listar(conClaveDePlataforma(), "?q=" + email).getBody().get("datos");
        return (String) datos.get(0).get("id");
    }

    private ResponseEntity<Map> abrir(HttpHeaders h, String id) {
        return http.exchange("/v1/admin/cuentas/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class);
    }

    @Test void abrirUnaCuentaMuestraSusUsuariosSusEmpresasYSuActividad() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        clienteConEmpresa("luis@otro.pe", "20100066611", "FERRETERIA LUNA SAC");
        String id = cuentaIdDe("ana@negocio.pe");

        ResponseEntity<Map> r = abrir(conClaveDePlataforma(), id);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> d = (Map<String, Object>) r.getBody().get("datos");
        assertThat(d).containsEntry("id", id).containsEntry("nombre", "Mi negocio").containsEntry("email", "ana@negocio.pe");
        List<Map<String, Object>> usuarios = (List<Map<String, Object>>) d.get("usuarios");
        assertThat(usuarios).hasSize(1);
        assertThat(usuarios.get(0)).containsEntry("email", "ana@negocio.pe").containsEntry("rol", "ADMIN").containsEntry("activo", true).containsKey("ultimo_acceso");
        List<Map<String, Object>> empresas = (List<Map<String, Object>>) d.get("empresas");
        assertThat(empresas).as("solo las de esta cuenta").hasSize(1);
        assertThat(empresas.get(0)).containsEntry("ruc", "20100066603").containsEntry("razon_social", "COMERCIAL ANDINA SAC").containsEntry("entorno", "BETA")
                .containsEntry("tiene_certificado", false).containsEntry("tiene_credenciales_sol", false);
        assertThat((List<?>) d.get("comprobantes")).isEmpty();
        assertThat((List<?>) d.get("eventos")).as("el registro público no pasa por el administrador").isEmpty();
    }

    /** La bitácora de la cuenta es la de las acciones del administrador sobre ella: el alta asistida deja la suya. */
    @Test void unaCuentaDadaDeAltaPorElAdministradorMuestraEseEventoYNingunSecreto() {
        ResponseEntity<Map> alta = http.postForEntity("/v1/admin/cuentas", new HttpEntity<>(
                "{\"nombre\":\"Alta asistida\",\"email\":\"alta@negocio.pe\",\"telefono\":\"987654321\",\"empresa\":{\"ruc\":\"20100066603\",\"razon_social\":\"ALTA SAC\",\"entorno\":\"BETA\"},\"serie\":{\"tipo\":\"01\",\"serie\":\"F001\"}}",
                conClaveDePlataforma()), Map.class);
        Map<?, ?> creada = (Map<?, ?>) alta.getBody().get("datos");
        String id = (String) creada.get("cuenta_id");

        ResponseEntity<Map> r = abrir(conClaveDePlataforma(), id);

        List<Map<String, Object>> eventos = (List<Map<String, Object>>) ((Map<String, Object>) r.getBody().get("datos")).get("eventos");
        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0)).containsEntry("accion", "CREAR_CUENTA").containsEntry("actor", "CLAVE_PLATAFORMA");
        assertThat((String) eventos.get(0).get("detalle")).contains("20100066603").doesNotContain((String) creada.get("api_key"));
        assertThat(r.getBody().toString()).as("ni la API key, ni hashes, ni la IP").doesNotContain((String) creada.get("api_key")).doesNotContain("ip=").doesNotContain("hash");
    }

    @Test void unAdministradorConSesionTambienAbreLaCuenta() {
        clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        String id = cuentaIdDe("ana@negocio.pe");
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String token = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");

        assertThat(abrir(conBearer(token), id).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void abrirUnaCuentaQueNoExisteEs404YUnIdMalFormadoEs400() {
        ResponseEntity<Map> noExiste = abrir(conClaveDePlataforma(), java.util.UUID.randomUUID().toString());
        assertThat(noExiste.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(noExiste.getBody()).containsEntry("codigo", "NO_ENCONTRADO");

        ResponseEntity<Map> malo = abrir(conClaveDePlataforma(), "no-es-un-uuid");
        assertThat(malo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** El detalle trae los usuarios y empresas de un cliente: las mismas puertas que el listado, ninguna más. */
    @Test void nadieMasAbreUnaCuenta() {
        String jwtDeCliente = clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        String id = cuentaIdDe("ana@negocio.pe");
        ResponseEntity<Map> tenant = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(
                "{\"ruc\":\"20100066611\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}", conClaveDePlataforma()), Map.class);
        String apiKey = (String) ((Map<?, ?>) tenant.getBody().get("datos")).get("api_key");
        HttpHeaders conApiKey = json(); conApiKey.set("X-Api-Key", apiKey);
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        assertThat(abrir(json(), id).getStatusCode()).as("sin credencial").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(abrir(conBearer(jwtDeCliente), id).getStatusCode()).as("JWT del propio cliente").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(abrir(conApiKey, id).getStatusCode()).as("API key de una empresa").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(abrir(conClaveErronea, id).getStatusCode()).as("clave de plataforma errónea").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void nadieMasLoLee() {
        String jwtDeCliente = clienteConEmpresa("ana@negocio.pe", "20100066603", "COMERCIAL ANDINA SAC");
        ResponseEntity<Map> tenant = http.postForEntity("/v1/admin/tenants", new HttpEntity<>(
                "{\"ruc\":\"20100066611\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}", conClaveDePlataforma()), Map.class);
        String apiKey = (String) ((Map<?, ?>) tenant.getBody().get("datos")).get("api_key");
        HttpHeaders conApiKey = json(); conApiKey.set("X-Api-Key", apiKey);
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        assertThat(listar(json(), "").getStatusCode()).as("sin credencial").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(listar(conBearer(jwtDeCliente), "").getStatusCode()).as("JWT de un cliente del portal").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(listar(conApiKey, "").getStatusCode()).as("API key de una empresa").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(listar(conClaveErronea, "").getStatusCode()).as("clave de plataforma errónea").isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
