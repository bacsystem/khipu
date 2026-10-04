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
 * Suspender y reactivar una cuenta (#182) de extremo a extremo: HTTP real, filtros reales, Postgres real. Lo que importa: suspender corta el
 * portal (también con una sesión ya abierta) y la API de TODAS las empresas de la cuenta con un código propio, no toca a ninguna otra
 * cuenta, no borra nada, y reactivar devuelve todo con las mismas credenciales.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class SuspensionE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    /** Una cuenta de cliente real: su JWT y su refresh, sus empresas y una API key por empresa. */
    record Cliente(String email, String access, String refresh, UUID cuentaId, List<UUID> empresas, List<String> apiKeys) {}

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) {
        return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class);
    }

    private Cliente cliente(String email, String... rucs) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        String access = (String) datos.get("access");
        List<UUID> empresas = new java.util.ArrayList<>();
        List<String> keys = new java.util.ArrayList<>();
        for (String ruc : rucs) {
            ResponseEntity<Map> e = llamar(HttpMethod.POST, "/v1/empresas", conBearer(access), "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s\",\"entorno\":\"BETA\"}".formatted(ruc, ruc));
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID id = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
            empresas.add(id);
            HttpHeaders conEmpresa = conBearer(access);
            conEmpresa.set("X-Empresa", id.toString());
            ResponseEntity<Map> k = llamar(HttpMethod.POST, "/v1/empresa/api-keys", conEmpresa, null);
            assertThat(k.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            keys.add((String) ((Map<?, ?>) k.getBody().get("datos")).get("api_key"));
        }
        UUID cuentaId = jdbc.queryForObject("SELECT id FROM cuenta WHERE email = ?", UUID.class, email);
        return new Cliente(email, access, (String) datos.get("refresh"), cuentaId, empresas, keys);
    }

    private ResponseEntity<Map> suspender(UUID cuenta, String cuerpo) { return llamar(HttpMethod.POST, "/v1/admin/cuentas/" + cuenta + "/suspender", conClaveDePlataforma(), cuerpo); }

    private ResponseEntity<Map> reactivar(UUID cuenta) { return llamar(HttpMethod.POST, "/v1/admin/cuentas/" + cuenta + "/reactivar", conClaveDePlataforma(), null); }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private ResponseEntity<Map> login(Cliente c) { return llamar(HttpMethod.POST, "/v1/auth/login", json(), "{\"email\":\"%s\",\"password\":\"Segura123\"}".formatted(c.email())); }

    private ResponseEntity<Map> refrescar(Cliente c) { return llamar(HttpMethod.POST, "/v1/auth/refresh", json(), "{\"refresh\":\"%s\"}".formatted(c.refresh())); }

    /** Lo que la cuenta hace con la API: ver sus series y emitir. Emitir con un cuerpo vacío, si llega al servicio, responde un error de validación, no un 403. */
    private ResponseEntity<Map> verSeries(String key) { return llamar(HttpMethod.GET, "/v1/series", conApiKey(key), null); }

    private ResponseEntity<Map> emitir(String key) { return llamar(HttpMethod.POST, "/v1/facturas", conApiKey(key), "{}"); }

    private long contar(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    @Test void suspenderCortaElPortalYLaApiDeTodasSusEmpresasYReactivarLoDevuelveConLasMismasCredenciales() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", "20100066611");
        Cliente b = cliente("luis@otro.pe", "20100066620");
        long usuarios = contar("usuario"), empresas = contar("tenant"), keys = contar("api_key"), cuentas = contar("cuenta");

        // Antes: todo funciona.
        assertThat(llamar(HttpMethod.GET, "/v1/empresas", conBearer(a.access()), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verSeries(a.apiKeys().get(0)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verSeries(a.apiKeys().get(1)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emitir(a.apiKeys().get(0)).getStatusCode()).as("llega al servicio, que valida el cuerpo").isNotIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);

        assertThat(suspender(a.cuentaId(), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // El portal, también con la sesión que ya estaba abierta (lectura y escritura).
        for (var m : List.of(HttpMethod.GET, HttpMethod.POST)) {
            ResponseEntity<Map> r = llamar(m, "/v1/empresas", conBearer(a.access()), m == HttpMethod.POST ? "{}" : null);
            assertThat(r.getStatusCode()).as("portal %s", m).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(codigo(r)).isEqualTo("CUENTA_SUSPENDIDA");
        }
        // Quién soy y cerrar sesión siguen: el portal puede decirle «tu cuenta está suspendida».
        assertThat(llamar(HttpMethod.GET, "/v1/auth/me", conBearer(a.access()), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        // No inicia sesión ni renueva la que tenía.
        ResponseEntity<Map> login = login(a);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codigo(login)).isEqualTo("CUENTA_SUSPENDIDA");
        assertThat(refrescar(a).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // La API de TODAS sus empresas: ver y emitir, con un código propio y no un error genérico.
        for (String key : a.apiKeys()) {
            ResponseEntity<Map> ver = verSeries(key);
            assertThat(ver.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(codigo(ver)).isEqualTo("CUENTA_SUSPENDIDA");
            ResponseEntity<Map> emision = emitir(key);
            assertThat(emision.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(codigo(emision)).isEqualTo("CUENTA_SUSPENDIDA");
        }

        // Otra cuenta no se entera.
        assertThat(llamar(HttpMethod.GET, "/v1/empresas", conBearer(b.access()), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verSeries(b.apiKeys().get(0)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(b).getStatusCode()).isEqualTo(HttpStatus.OK);

        // No se borró nada.
        assertThat(contar("usuario")).isEqualTo(usuarios);
        assertThat(contar("tenant")).isEqualTo(empresas);
        assertThat(contar("api_key")).isEqualTo(keys);
        assertThat(contar("cuenta")).isEqualTo(cuentas);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_key WHERE NOT activa", Long.class)).as("ninguna key se revocó").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sesion WHERE revocada", Long.class)).as("ninguna sesión se revocó").isZero();

        assertThat(reactivar(a.cuentaId()).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Todo vuelve con las MISMAS credenciales: el mismo JWT, el mismo refresh y las mismas API keys.
        assertThat(llamar(HttpMethod.GET, "/v1/empresas", conBearer(a.access()), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refrescar(a).getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String key : a.apiKeys()) {
            assertThat(verSeries(key).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(emitir(key).getStatusCode()).as("permitida tras reactivar: llega al servicio").isNotIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
        }
        assertThat(login(a).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void unaEmpresaDeIntegracionSinCuentaNoSeVeAfectadaPorSuspenderOtraCuenta() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        ResponseEntity<Map> tenant = llamar(HttpMethod.POST, "/v1/admin/tenants", conClaveDePlataforma(), "{\"ruc\":\"20100066611\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}");
        String keyIntegracion = (String) ((Map<?, ?>) tenant.getBody().get("datos")).get("api_key");
        suspender(a.cuentaId(), null);

        assertThat(verSeries(keyIntegracion).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- bitácora ------------------------------------------------------------------------------------------------------------------

    @Test void suspenderYReactivarQuedanEnLaBitacoraConElMotivoYQuienLoHizo() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");

        suspender(a.cuentaId(), "{\"motivo\":\"  Factura de septiembre sin pagar  \"}");
        reactivar(a.cuentaId());

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT accion, actor_tipo, cuenta_id, detalle FROM auditoria_admin WHERE accion IN ('SUSPENDER_CUENTA', 'REACTIVAR_CUENTA') ORDER BY ocurrido_en, id");
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("accion", "SUSPENDER_CUENTA").containsEntry("actor_tipo", "CLAVE_PLATAFORMA").containsEntry("cuenta_id", a.cuentaId())
                .containsEntry("detalle", "motivo=Factura de septiembre sin pagar");
        assertThat(filas.get(1)).containsEntry("accion", "REACTIVAR_CUENTA").containsEntry("cuenta_id", a.cuentaId()).containsEntry("detalle", null);
    }

    @Test void unAdministradorConSesionTambienDejaSuNombreEnLaBitacora() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String token = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");

        ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/suspender", conBearer(token), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT actor_tipo FROM auditoria_admin WHERE accion = 'SUSPENDER_CUENTA'", String.class)).isEqualTo("ADMINISTRADOR");
        assertThat(jdbc.queryForObject("SELECT administrador_id IS NOT NULL FROM auditoria_admin WHERE accion = 'SUSPENDER_CUENTA'", Boolean.class)).isTrue();
    }

    // --- conflictos y datos inválidos -------------------------------------------------------------------------------------------------

    @Test void suspenderDosVecesOReactivarUnaActivaEsConflictoYNoDejaOtroRegistro() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");

        ResponseEntity<Map> noSuspendida = reactivar(a.cuentaId());
        assertThat(noSuspendida.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(noSuspendida)).isEqualTo("CUENTA_NO_SUSPENDIDA");

        assertThat(suspender(a.cuentaId(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> yaSuspendida = suspender(a.cuentaId(), null);
        assertThat(yaSuspendida.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(yaSuspendida)).isEqualTo("CUENTA_YA_SUSPENDIDA");

        assertThat(contar("auditoria_admin")).as("solo la suspensión que sí ocurrió").isEqualTo(1);
    }

    @Test void unaCuentaQueNoExisteEs404YUnIdMalFormadoEs400() {
        ResponseEntity<Map> noExiste = suspender(UUID.randomUUID(), null);
        assertThat(noExiste.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(codigo(noExiste)).isEqualTo("NO_ENCONTRADO");

        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/no-es-un-uuid/suspender", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(contar("auditoria_admin")).isZero();
    }

    @Test void unMotivoDemasiadoLargoSeRechazaYNoSuspende() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");

        ResponseEntity<Map> r = suspender(a.cuentaId(), "{\"motivo\":\"%s\"}".formatted("x".repeat(201)));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(r)).isEqualTo("MOTIVO_INVALIDO");
        assertThat(llamar(HttpMethod.GET, "/v1/empresas", conBearer(a.access()), null).getStatusCode()).as("la cuenta sigue activa").isEqualTo(HttpStatus.OK);
    }

    // --- el estado se ve en el backoffice ---------------------------------------------------------------------------------------------

    @Test void elListadoYElDetalleDicenElEstadoYDesdeCuandoEstaSuspendida() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        Cliente b = cliente("luis@otro.pe", "20100066611");
        suspender(a.cuentaId(), null);

        ResponseEntity<Map> lista = llamar(HttpMethod.GET, "/v1/admin/cuentas", conClaveDePlataforma(), null);
        List<Map<String, Object>> filas = (List<Map<String, Object>>) lista.getBody().get("datos");
        Map<String, Object> deA = filas.stream().filter(f -> f.get("email").equals("ana@negocio.pe")).findFirst().orElseThrow();
        Map<String, Object> deB = filas.stream().filter(f -> f.get("email").equals("luis@otro.pe")).findFirst().orElseThrow();
        assertThat(deA).containsEntry("estado", "SUSPENDIDA").containsKey("suspendida_en");
        assertThat(deB).containsEntry("estado", "ACTIVA").doesNotContainKey("suspendida_en");

        Map<String, Object> detalle = (Map<String, Object>) llamar(HttpMethod.GET, "/v1/admin/cuentas/" + a.cuentaId(), conClaveDePlataforma(), null).getBody().get("datos");
        assertThat(detalle).containsEntry("estado", "SUSPENDIDA").containsKey("suspendida_en");

        reactivar(a.cuentaId());
        Map<String, Object> reactivada = (Map<String, Object>) llamar(HttpMethod.GET, "/v1/admin/cuentas/" + a.cuentaId(), conClaveDePlataforma(), null).getBody().get("datos");
        assertThat(reactivada).containsEntry("estado", "ACTIVA").doesNotContainKey("suspendida_en");
    }

    // --- quién puede suspender ---------------------------------------------------------------------------------------------------------

    /** Suspender es del administrador: ni el dueño de la cuenta, ni una API key, ni una clave errónea pueden, y no cambia nada. */
    @Test void nadieMasPuedeSuspenderNiReactivar() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");
        List<HttpHeaders> intrusos = List.of(json(), conBearer(a.access()), conApiKey(a.apiKeys().get(0)), conClaveErronea);

        for (HttpHeaders h : intrusos) {
            for (String accion : List.of("suspender", "reactivar")) {
                ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/" + accion, h, null);
                assertThat(r.getStatusCode()).as("%s con %s", accion, h).isEqualTo(HttpStatus.UNAUTHORIZED);
            }
        }

        assertThat(llamar(HttpMethod.GET, "/v1/empresas", conBearer(a.access()), null).getStatusCode()).as("la cuenta sigue activa").isEqualTo(HttpStatus.OK);
        assertThat(contar("auditoria_admin")).isZero();
    }
}
