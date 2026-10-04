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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Impersonar a un usuario (#184) de extremo a extremo: HTTP real, filtros reales, Postgres real. Lo que importa: el token es de solo lectura y vence a los 15
 * minutos; nada del cliente cambia mientras se mira (ni la contraseña, ni las credenciales SOL, ni las API keys); queda en la bitácora quién impersonó a quién; y
 * el cliente lo ve en su propio historial sin saber qué administrador fue.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ImpersonacionE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    /** Un cliente real: su cuenta, su usuario, su sesión normal y su empresa con una API key. */
    record Cliente(String email, String access, UUID cuentaId, UUID usuarioId, UUID empresaId, String apiKey) {}

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private HttpHeaders conBearer(String token, UUID empresa) { HttpHeaders h = conBearer(token); h.set("X-Empresa", empresa.toString()); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    private Cliente cliente(String email, String ruc) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        assertThat(llamar(HttpMethod.POST, "/v1/empresas", conBearer(access), "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s\",\"entorno\":\"BETA\"}".formatted(ruc, ruc)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
        ResponseEntity<Map> k = llamar(HttpMethod.POST, "/v1/empresa/api-keys", conBearer(access, empresa), null);
        String key = (String) ((Map<?, ?>) k.getBody().get("datos")).get("api_key");
        UUID cuenta = jdbc.queryForObject("SELECT id FROM cuenta WHERE email = ?", UUID.class, email);
        UUID usuario = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
        return new Cliente(email, access, cuenta, usuario, empresa, key);
    }

    /** La sesión de un administrador real (JWT propio, con el segundo factor ya hecho). */
    private String sesionDeAdministrador() {
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        return (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");
    }

    private ResponseEntity<Map> impersonar(String sesionAdmin, Cliente c) { return llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/usuarios/" + c.usuarioId() + "/impersonar", conBearer(sesionAdmin), null); }

    private String tokenDeSoporte(String sesionAdmin, Cliente c) {
        ResponseEntity<Map> r = impersonar(sesionAdmin, c);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) ((Map<?, ?>) r.getBody().get("datos")).get("access_token");
    }

    /** El `exp` del payload de un JWT, sin verificar la firma (solo para comprobar lo que el propio token dice). */
    private long expDelJwt(String jwt) {
        try {
            byte[] payload = java.util.Base64.getUrlDecoder().decode(jwt.split("[.]")[1]);
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).get("exp").asLong();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private long contar(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    // --- la sesión de soporte ---------------------------------------------------------------------------------------------------------

    @Test void elAdministradorRecibeUnTokenDelUsuarioQueVenceEnQuinceMinutosYNoTieneRefresh() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        String admin = sesionDeAdministrador();

        ResponseEntity<Map> r = impersonar(admin, a);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getCacheControl()).contains("no-store");
        Map<String, Object> datos = (Map<String, Object>) r.getBody().get("datos");
        assertThat(datos).containsKeys("access_token", "expira_en", "usuario").doesNotContainKey("refresh");
        Instant expira = Instant.parse((String) datos.get("expira_en"));
        assertThat(Duration.between(Instant.now(), expira)).isBetween(Duration.ofMinutes(14), Duration.ofMinutes(16));
        assertThat(((Map<String, Object>) datos.get("usuario"))).containsEntry("email", "ana@negocio.pe");
        assertThat(r.getBody().toString()).as("nunca el hash de la contraseña").doesNotContain("hash").doesNotContain("password");
        // El JWT que sale lo dice por sí mismo: vence cuando dice la respuesta.
        assertThat(expDelJwt((String) datos.get("access_token"))).isEqualTo(expira.getEpochSecond());
    }

    @Test void conElTokenSePuedeMirarComoElUsuarioYElPortalSabeQueEsSoporte() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        String soporte = tokenDeSoporte(sesionDeAdministrador(), a);

        ResponseEntity<Map> me = llamar(HttpMethod.GET, "/v1/auth/me", conBearer(soporte), null);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> usuario = (Map<String, Object>) me.getBody().get("datos");
        assertThat(usuario).containsEntry("email", "ana@negocio.pe").containsKey("soporte_hasta");
        assertThat(me.getBody().toString()).as("el cliente nunca sabe qué administrador fue").doesNotContain("administrador");
        assertThat(llamar(HttpMethod.GET, "/v1/empresas", conBearer(soporte), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(llamar(HttpMethod.GET, "/v1/empresa", conBearer(soporte, a.empresaId()), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(llamar(HttpMethod.GET, "/v1/series", conBearer(soporte, a.empresaId()), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Una sesión normal no dice nada de soporte.
        Map<String, Object> normal = (Map<String, Object>) llamar(HttpMethod.GET, "/v1/auth/me", conBearer(a.access()), null).getBody().get("datos");
        assertThat(normal).doesNotContainKey("soporte_hasta");
    }

    /** Nada del cliente cambia: ni la contraseña, ni las credenciales SOL, ni las API keys, ni se crea ni se emite nada. */
    @Test void conElTokenNoSePuedeCambiarNada() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        String soporte = tokenDeSoporte(sesionDeAdministrador(), a);
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE id = ?", String.class, a.usuarioId());
        long empresas = contar("tenant"), keys = contar("api_key"), documentos = contar("documento"), series = contar("serie"), sesiones = contar("sesion");

        List<Object[]> intentos = List.of(
                new Object[]{HttpMethod.PUT, "/v1/empresa/credenciales-sol", "{\"usuario\":\"MODDATOS\",\"clave\":\"moddatos\"}"},
                new Object[]{HttpMethod.PUT, "/v1/empresa/datos-fiscales", "{}"},
                new Object[]{HttpMethod.POST, "/v1/empresa/api-keys", null},
                new Object[]{HttpMethod.POST, "/v1/empresas", "{\"ruc\":\"20100066611\",\"razon_social\":\"OTRA SAC\",\"entorno\":\"BETA\"}"},
                new Object[]{HttpMethod.POST, "/v1/series", "{\"tipo\":\"01\",\"serie\":\"F001\"}"},
                new Object[]{HttpMethod.POST, "/v1/facturas", "{}"},
                new Object[]{HttpMethod.POST, "/v1/auth/logout", "{\"refresh\":\"x\"}"},
                new Object[]{HttpMethod.POST, "/v1/auth/verificacion", null});
        for (Object[] i : intentos) {
            ResponseEntity<Map> r = llamar((HttpMethod) i[0], (String) i[1], conBearer(soporte, a.empresaId()), (String) i[2]);
            assertThat(r.getStatusCode()).as("%s %s", i[0], i[1]).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(codigo(r)).as("%s %s", i[0], i[1]).isEqualTo("SOPORTE_SOLO_LECTURA");
        }
        ResponseEntity<Map> borrar = llamar(HttpMethod.DELETE, "/v1/empresa/api-keys/" + UUID.randomUUID(), conBearer(soporte, a.empresaId()), null);
        assertThat(borrar.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codigo(borrar)).isEqualTo("SOPORTE_SOLO_LECTURA");

        assertThat(jdbc.queryForObject("SELECT password_hash FROM usuario WHERE id = ?", String.class, a.usuarioId())).as("la contraseña no cambió").isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT sol_usuario_enc IS NULL AND sol_clave_enc IS NULL FROM tenant WHERE id = ?", Boolean.class, a.empresaId())).as("las credenciales SOL siguen sin tocar").isTrue();
        assertThat(contar("tenant")).isEqualTo(empresas);
        assertThat(contar("api_key")).isEqualTo(keys);
        assertThat(contar("documento")).isEqualTo(documentos);
        assertThat(contar("serie")).isEqualTo(series);
        assertThat(contar("sesion")).as("no se cerró ni se creó ninguna sesión").isEqualTo(sesiones);
        assertThat(jdbc.queryForObject("SELECT activa FROM api_key WHERE tenant_id = ?", Boolean.class, a.empresaId())).as("la API key sigue vigente").isTrue();
    }

    /** El soporte no abre más puertas que el cliente: su sesión sigue acotada a su cuenta. */
    @Test void laSesionDeSoporteSigueAcotadaALaCuentaDelUsuario() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        Cliente b = cliente("luis@otro.pe", "20100066611");
        String soporte = tokenDeSoporte(sesionDeAdministrador(), a);

        ResponseEntity<Map> deB = llamar(HttpMethod.GET, "/v1/empresa", conBearer(soporte, b.empresaId()), null);

        assertThat(deB.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codigo(deB)).isEqualTo("EMPRESA_AJENA");
        assertThat(((List<Map<String, Object>>) llamar(HttpMethod.GET, "/v1/empresas", conBearer(soporte), null).getBody().get("datos")).stream().map(e -> e.get("ruc"))).containsExactly("20100066603");
    }

    @Test void conLaCuentaSuspendidaLaSesionDeSoporteTampocoMiraSusDatos() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        String soporte = tokenDeSoporte(sesionDeAdministrador(), a);
        llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/suspender", conClaveDePlataforma(), null);

        ResponseEntity<Map> r = llamar(HttpMethod.GET, "/v1/empresas", conBearer(soporte), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codigo(r)).isEqualTo("CUENTA_SUSPENDIDA");
    }

    // --- bitácora e historial del cliente -----------------------------------------------------------------------------------------------

    @Test void quedaEnLaBitacoraQuienImpersonoAQuienCuandoYPorCuantoTiempo() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        String admin = sesionDeAdministrador();
        UUID adminId = jdbc.queryForObject("SELECT id FROM administrador WHERE email = 'admin@khipu.pe'", UUID.class);

        String token = tokenDeSoporte(admin, a);

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT accion, actor_tipo, administrador_id, cuenta_id, detalle, ocurrido_en FROM auditoria_admin WHERE accion = 'IMPERSONAR_USUARIO'");
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0)).containsEntry("actor_tipo", "ADMINISTRADOR").containsEntry("administrador_id", adminId).containsEntry("cuenta_id", a.cuentaId())
                .containsEntry("detalle", "usuario=ana@negocio.pe duracion_s=900");
        assertThat(String.valueOf(filas.get(0).get("detalle"))).as("nunca el token").doesNotContain(token);
        assertThat(((java.sql.Timestamp) filas.get(0).get("ocurrido_en")).toInstant()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(5));
        // También aparece en el detalle de la cuenta que ve el administrador.
        Map<String, Object> detalle = (Map<String, Object>) llamar(HttpMethod.GET, "/v1/admin/cuentas/" + a.cuentaId(), conClaveDePlataforma(), null).getBody().get("datos");
        assertThat(((List<Map<String, Object>>) detalle.get("eventos")).stream().map(e -> e.get("accion"))).contains("IMPERSONAR_USUARIO");
    }

    @Test void elClienteVeSusAccesosDeSoporteSinSaberQueAdministradorFue() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        Cliente b = cliente("luis@otro.pe", "20100066611");
        String admin = sesionDeAdministrador();
        tokenDeSoporte(admin, a);
        tokenDeSoporte(admin, a);

        ResponseEntity<Map> r = llamar(HttpMethod.GET, "/v1/cuenta/accesos-de-soporte", conBearer(a.access()), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> accesos = (List<Map<String, Object>>) r.getBody().get("datos");
        assertThat(accesos).hasSize(2);
        assertThat(accesos.get(0)).containsEntry("usuario", "ana@negocio.pe").containsEntry("duracion_segundos", 900).containsKey("ocurrido_en");
        assertThat(r.getBody().toString()).doesNotContain("admin@khipu.pe").doesNotContain("administrador");
        // El de otra cuenta no ve nada de los accesos a esta.
        assertThat((List<Object>) llamar(HttpMethod.GET, "/v1/cuenta/accesos-de-soporte", conBearer(b.access()), null).getBody().get("datos")).isEmpty();
        // Y con la propia sesión de soporte también se puede leer (solo lectura).
        assertThat(llamar(HttpMethod.GET, "/v1/cuenta/accesos-de-soporte", conBearer(tokenDeSoporte(admin, a)), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void conUnaApiKeyNoHayHistorialDeCuenta() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");

        ResponseEntity<Map> r = llamar(HttpMethod.GET, "/v1/cuenta/accesos-de-soporte", conApiKey(a.apiKey()), null);

        assertThat(r.getStatusCode().is2xxSuccessful()).isFalse();
    }

    // --- quién puede y a quién ----------------------------------------------------------------------------------------------------------

    @Test void laClaveDePlataformaNoPuedeImpersonarYNoDejaRegistro() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");

        ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/usuarios/" + a.usuarioId() + "/impersonar", conClaveDePlataforma(), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codigo(r)).isEqualTo("REQUIERE_ADMINISTRADOR");
        assertThat(contar("auditoria_admin")).isZero();
    }

    @Test void unUsuarioDeOtraCuentaInexistenteODesactivadoNoSeImpersona() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        Cliente b = cliente("luis@otro.pe", "20100066611");
        String admin = sesionDeAdministrador();

        ResponseEntity<Map> deOtraCuenta = llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/usuarios/" + b.usuarioId() + "/impersonar", conBearer(admin), null);
        assertThat(deOtraCuenta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/usuarios/" + UUID.randomUUID() + "/impersonar", conBearer(admin), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/usuarios/no-es-un-uuid/impersonar", conBearer(admin), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", a.usuarioId());
        ResponseEntity<Map> inactivo = impersonar(admin, a);
        assertThat(inactivo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(inactivo)).isEqualTo("USUARIO_INACTIVO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion = 'IMPERSONAR_USUARIO'", Long.class)).as("ninguno de los intentos fallidos deja registro de una impersonación").isZero();
    }

    /** Impersonar es del administrador: ni el dueño de la cuenta, ni una API key, ni una clave errónea pueden, y no queda registro. */
    @Test void nadieMasPuedeImpersonar() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");
        String ruta = "/v1/admin/cuentas/" + a.cuentaId() + "/usuarios/" + a.usuarioId() + "/impersonar";

        for (HttpHeaders h : List.of(json(), conBearer(a.access()), conApiKey(a.apiKey()), conClaveErronea)) {
            assertThat(llamar(HttpMethod.POST, ruta, h, null).getStatusCode()).as("con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(contar("auditoria_admin")).isZero();
    }

    /** Una sesión de soporte no sirve como sesión de administrador, ni al revés: son dos tipos de token distintos. */
    @Test void elTokenDeSoporteNoAbreLasRutasDeAdministrador() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        String soporte = tokenDeSoporte(sesionDeAdministrador(), a);

        assertThat(llamar(HttpMethod.GET, "/v1/admin/cuentas", conBearer(soporte), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/usuarios/" + a.usuarioId() + "/impersonar", conBearer(soporte), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
