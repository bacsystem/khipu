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
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.domain.tenant.Tenant;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las acciones del administrador sobre una empresa (#187) de extremo a extremo: HTTP real, filtros reales, Postgres real. Lo que importa: cambiar el entorno
 * no toca nada más (ni los comprobantes ya emitidos ni el certificado ni las credenciales SOL), una key revocada deja de autenticar de inmediato y las otras
 * no, la prueba de conexión usa las credenciales y el entorno de la empresa, y las tres acciones quedan en la bitácora sin secretos.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AccionesDeEmpresaE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    /** SUNAT de mentira: lo que responde al {@code getStatus} (o la excepción que lanza) y con qué empresa lo llamaron. */
    static final AtomicReference<RuntimeException> FALLA = new AtomicReference<>();
    static final AtomicReference<Tenant> LLAMADO_CON = new AtomicReference<>();

    @TestConfiguration
    static class SunatDePrueba {
        @Bean @Primary SunatBillingGateway sunatQueContesta() {
            return new SunatBillingGateway() {
                public byte[] sendBill(Tenant t, String nombreArchivo, byte[] xml) { throw new AssertionError("las acciones del administrador no envían comprobantes"); }
                public String sendSummary(Tenant t, String nombreArchivo, byte[] xml) { throw new AssertionError("las acciones del administrador no envían resúmenes"); }
                public EstadoTicket getStatus(Tenant t, String ticket) {
                    LLAMADO_CON.set(t);
                    if (FALLA.get() != null) throw FALLA.get();
                    return new EstadoTicket("0", null);
                }
            };
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        FALLA.set(null);
        LLAMADO_CON.set(null);
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    /** Un cliente real: su cuenta, su empresa con credenciales SOL y dos API keys. */
    record Cliente(String access, UUID cuentaId, UUID empresaId, List<UUID> keyIds, List<String> apiKeys) {}

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    private Cliente cliente(String email, String ruc, boolean conSol) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        assertThat(llamar(HttpMethod.POST, "/v1/empresas", conBearer(access), "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s\",\"entorno\":\"BETA\"}".formatted(ruc, ruc)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
        HttpHeaders conEmpresa = conBearer(access);
        conEmpresa.set("X-Empresa", empresa.toString());
        List<String> keys = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ResponseEntity<Map> k = llamar(HttpMethod.POST, "/v1/empresa/api-keys", conEmpresa, null);
            assertThat(k.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            keys.add((String) ((Map<?, ?>) k.getBody().get("datos")).get("api_key"));
        }
        if (conSol) assertThat(llamar(HttpMethod.PUT, "/v1/empresa/credenciales-sol", conApiKey(keys.get(0)), "{\"usuario\":\"MODDATOS\",\"clave\":\"moddatos\"}").getStatusCode().is2xxSuccessful()).isTrue();
        UUID cuenta = jdbc.queryForObject("SELECT id FROM cuenta WHERE email = ?", UUID.class, email);
        List<UUID> ids = jdbc.queryForList("SELECT id FROM api_key WHERE tenant_id = ? ORDER BY created_at, id", UUID.class, empresa);
        return new Cliente(access, cuenta, empresa, ids, keys);
    }

    private ResponseEntity<Map> cambiarEntorno(UUID empresa, String entorno) { return llamar(HttpMethod.POST, "/v1/admin/empresas/" + empresa + "/entorno", conClaveDePlataforma(), "{\"entorno\":\"%s\"}".formatted(entorno)); }

    private ResponseEntity<Map> revocar(UUID empresa, UUID key) { return llamar(HttpMethod.POST, "/v1/admin/empresas/" + empresa + "/api-keys/" + key + "/revocar", conClaveDePlataforma(), null); }

    private ResponseEntity<Map> probar(UUID empresa) { return llamar(HttpMethod.POST, "/v1/admin/empresas/" + empresa + "/prueba-de-conexion", conClaveDePlataforma(), null); }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private String entornoEnLaBase(UUID empresa) { return jdbc.queryForObject("SELECT entorno FROM tenant WHERE id = ?", String.class, empresa); }

    private long contar(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    private List<Map<String, Object>> bitacora(String... acciones) {
        return jdbc.queryForList("SELECT accion, actor_tipo, cuenta_id, tenant_id, detalle FROM auditoria_admin WHERE accion IN (" + String.join(",", java.util.Collections.nCopies(acciones.length, "?")) + ") ORDER BY ocurrido_en, id", (Object[]) acciones);
    }

    /** Un comprobante emitido, escrito directo: lo que importa aquí es que el cambio de entorno no lo toque. */
    private UUID comprobante(UUID empresa) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, '01', 'F001', 1, '2026-09-15', 'ACEPTADO', 'doc-1')", id, empresa);
        jdbc.update("""
                INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, total_gravado, total_exonerado, total_inafecto, total_igv, total)
                VALUES (?, '0101', 'PEN', '6', '20601234565', 'CLIENTE SAC', 0, 0, 0, 0, 118.00)""", id);
        return id;
    }

    // --- entorno ----------------------------------------------------------------------------------------------------------------------

    @Test void cambiarElEntornoCambiaContraQueURLEmiteYNoTocaNadaMas() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", true);
        Cliente b = cliente("luis@otro.pe", "20100066611", true);
        UUID doc = comprobante(a.empresaId());
        Map<String, Object> antes = jdbc.queryForMap("SELECT d.estado, d.serie, d.numero, d.fecha_emision, c.total FROM documento d JOIN comprobante c ON c.documento_id = d.id WHERE d.id = ?", doc);
        long documentos = contar("documento"), comprobantes = contar("comprobante"), keys = contar("api_key"), empresas = contar("tenant");

        ResponseEntity<Map> r = cambiarEntorno(a.empresaId(), "PRODUCCION");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> datos = (Map<String, Object>) r.getBody().get("datos");
        assertThat(datos).containsEntry("desde", "BETA").containsEntry("hacia", "PRODUCCION");
        assertThat(entornoEnLaBase(a.empresaId())).isEqualTo("PRODUCCION");
        // La empresa lo ve así por su propia API.
        assertThat(((Map<String, Object>) llamar(HttpMethod.GET, "/v1/empresa", conApiKey(a.apiKeys().get(0)), null).getBody().get("datos"))).containsEntry("entorno", "PRODUCCION");
        // No se tocó nada más: ni los comprobantes ya emitidos ni el certificado, las credenciales SOL, las keys y las demás empresas.
        assertThat(jdbc.queryForMap("SELECT d.estado, d.serie, d.numero, d.fecha_emision, c.total FROM documento d JOIN comprobante c ON c.documento_id = d.id WHERE d.id = ?", doc)).isEqualTo(antes);
        assertThat(contar("documento")).isEqualTo(documentos);
        assertThat(contar("comprobante")).isEqualTo(comprobantes);
        assertThat(contar("api_key")).isEqualTo(keys);
        assertThat(contar("tenant")).isEqualTo(empresas);
        assertThat(jdbc.queryForObject("SELECT sol_usuario_enc IS NOT NULL AND sol_clave_enc IS NOT NULL FROM tenant WHERE id = ?", Boolean.class, a.empresaId())).as("las credenciales SOL siguen cargadas").isTrue();
        assertThat(entornoEnLaBase(b.empresaId())).as("otra empresa no se entera").isEqualTo("BETA");
        // Y se puede volver.
        assertThat(cambiarEntorno(a.empresaId(), "BETA").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entornoEnLaBase(a.empresaId())).isEqualTo("BETA");
    }

    @Test void conEnviosPendientesNoSeCambiaElEntornoNiSeDejaRegistro() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", true);
        UUID doc = comprobante(a.empresaId());
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, ?, 'ENVIAR', now())", a.empresaId(), doc);

        ResponseEntity<Map> r = cambiarEntorno(a.empresaId(), "PRODUCCION");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("EMPRESA_CON_ENVIOS_PENDIENTES");
        assertThat(entornoEnLaBase(a.empresaId())).isEqualTo("BETA");
        assertThat(bitacora("CAMBIAR_ENTORNO_EMPRESA")).isEmpty();
        // Cuando el outbox se vacía, ya se puede.
        jdbc.update("DELETE FROM outbox");
        assertThat(cambiarEntorno(a.empresaId(), "PRODUCCION").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void pedirElEntornoQueYaTieneEsConflictoYUnEntornoInventadoEs400() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", false);

        ResponseEntity<Map> mismo = cambiarEntorno(a.empresaId(), "BETA");
        assertThat(mismo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(mismo)).isEqualTo("ENTORNO_SIN_CAMBIOS");
        assertThat(cambiarEntorno(a.empresaId(), "PRUEBAS").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/empresas/" + a.empresaId() + "/entorno", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(bitacora("CAMBIAR_ENTORNO_EMPRESA")).isEmpty();
    }

    // --- revocar una API key ----------------------------------------------------------------------------------------------------------

    @Test void unaKeyRevocadaDejaDeAutenticarDeInmediatoYLasOtrasNo() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", false);
        assertThat(llamar(HttpMethod.GET, "/v1/series", conApiKey(a.apiKeys().get(0)), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> r = revocar(a.empresaId(), a.keyIds().get(0));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> datos = (Map<String, Object>) r.getBody().get("datos");
        assertThat(datos).containsEntry("api_key_id", a.keyIds().get(0).toString()).containsKey("revocada_en");
        assertThat(r.getBody().toString()).as("nunca la clave").doesNotContain(a.apiKeys().get(0));
        assertThat(llamar(HttpMethod.GET, "/v1/series", conApiKey(a.apiKeys().get(0)), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(llamar(HttpMethod.GET, "/v1/series", conApiKey(a.apiKeys().get(1)), null).getStatusCode()).as("la otra key sigue sirviendo").isEqualTo(HttpStatus.OK);
        assertThat(contar("api_key")).as("se revoca, no se borra").isEqualTo(2);
    }

    @Test void revocarUnaKeyYaRevocadaEsConflictoYUnaDeOtraEmpresaEs404SinTocarla() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", false);
        Cliente b = cliente("luis@otro.pe", "20100066611", false);
        revocar(a.empresaId(), a.keyIds().get(0));

        ResponseEntity<Map> yaRevocada = revocar(a.empresaId(), a.keyIds().get(0));
        assertThat(yaRevocada.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(yaRevocada)).isEqualTo("API_KEY_YA_REVOCADA");

        assertThat(revocar(a.empresaId(), b.keyIds().get(0)).getStatusCode()).as("la key es de otra empresa").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(revocar(a.empresaId(), UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(llamar(HttpMethod.GET, "/v1/series", conApiKey(b.apiKeys().get(0)), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bitacora("REVOCAR_API_KEY_EMPRESA")).as("solo la revocación que sí ocurrió").hasSize(1);
    }

    // --- probar la conexión -----------------------------------------------------------------------------------------------------------

    @Test void laPruebaUsaLasCredencialesSolYElEntornoDeLaEmpresaYDiceComoContestoSunat() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", true);

        ResponseEntity<Map> conectado = probar(a.empresaId());
        assertThat(conectado.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> datos = (Map<String, Object>) conectado.getBody().get("datos");
        assertThat(datos).containsEntry("resultado", "CONECTADO").containsEntry("entorno", "BETA").doesNotContainKey("codigo");
        // Se llamó con la empresa de verdad: su RUC, sus credenciales SOL (descifradas) y su entorno.
        Tenant llamado = LLAMADO_CON.get();
        assertThat(llamado.ruc()).isEqualTo("20100066603");
        assertThat(llamado.sol().usuario()).isEqualTo("MODDATOS");
        assertThat(llamado.entorno().name()).isEqualTo("BETA");
        // La respuesta nunca lleva las credenciales.
        assertThat(conectado.getBody().toString()).doesNotContain("MODDATOS").doesNotContain("moddatos");

        cambiarEntorno(a.empresaId(), "PRODUCCION");
        assertThat(((Map<String, Object>) probar(a.empresaId()).getBody().get("datos"))).containsEntry("entorno", "PRODUCCION");
        assertThat(LLAMADO_CON.get().entorno().name()).isEqualTo("PRODUCCION");
    }

    @Test void unErrorDefinitivoOLaFaltaDeRespuestaSeInformanConSuCodigoYSuMensaje() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", true);

        FALLA.set(new pe.factura.application.port.out.SunatRechazoException("1033", "El ticket no existe"));
        Map<String, Object> rechazado = (Map<String, Object>) probar(a.empresaId()).getBody().get("datos");
        assertThat(rechazado).containsEntry("resultado", "RECHAZADO").containsEntry("codigo", "1033").containsEntry("mensaje", "El ticket no existe");

        FALLA.set(new pe.factura.application.port.out.SunatTransientException("0109", "Tiempo de espera agotado llamando a SUNAT"));
        Map<String, Object> sinRespuesta = (Map<String, Object>) probar(a.empresaId()).getBody().get("datos");
        assertThat(sinRespuesta).containsEntry("resultado", "SIN_RESPUESTA").containsEntry("codigo", "0109");
    }

    @Test void sinCredencialesSolNoSeLlamaASunatNiSeDejaRegistro() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", false);

        ResponseEntity<Map> r = probar(a.empresaId());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("SOL_NO_CARGADAS");
        assertThat(LLAMADO_CON.get()).isNull();
        assertThat(bitacora("PROBAR_CONEXION_EMPRESA")).isEmpty();
    }

    // --- bitácora ---------------------------------------------------------------------------------------------------------------------

    @Test void lasTresAccionesQuedanEnLaBitacoraTambienEnLaDeLaCuentaYSinSecretos() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", true);
        FALLA.set(new pe.factura.application.port.out.SunatRechazoException("1033", "El ticket no existe"));

        cambiarEntorno(a.empresaId(), "PRODUCCION");
        revocar(a.empresaId(), a.keyIds().get(0));
        probar(a.empresaId());

        List<Map<String, Object>> filas = bitacora("CAMBIAR_ENTORNO_EMPRESA", "REVOCAR_API_KEY_EMPRESA", "PROBAR_CONEXION_EMPRESA");
        assertThat(filas).hasSize(3);
        for (Map<String, Object> f : filas) {
            assertThat(f).containsEntry("actor_tipo", "CLAVE_PLATAFORMA").containsEntry("tenant_id", a.empresaId()).containsEntry("cuenta_id", a.cuentaId());
            assertThat(String.valueOf(f.get("detalle"))).doesNotContain("MODDATOS").doesNotContain("moddatos").doesNotContain(a.apiKeys().get(0)).doesNotContain(a.apiKeys().get(1));
        }
        assertThat(filas.get(0)).containsEntry("accion", "CAMBIAR_ENTORNO_EMPRESA").containsEntry("detalle", "desde=BETA hacia=PRODUCCION");
        assertThat(String.valueOf(filas.get(1).get("detalle"))).startsWith("prefijo=fk_");
        assertThat(filas.get(2)).containsEntry("accion", "PROBAR_CONEXION_EMPRESA").containsEntry("detalle", "entorno=PRODUCCION resultado=RECHAZADO codigo=1033");
        // Y aparecen en el detalle de la cuenta que ve el administrador.
        Map<String, Object> detalle = (Map<String, Object>) llamar(HttpMethod.GET, "/v1/admin/cuentas/" + a.cuentaId(), conClaveDePlataforma(), null).getBody().get("datos");
        assertThat(((List<Map<String, Object>>) detalle.get("eventos")).stream().map(e -> e.get("accion"))).contains("CAMBIAR_ENTORNO_EMPRESA", "REVOCAR_API_KEY_EMPRESA", "PROBAR_CONEXION_EMPRESA");
    }

    @Test void unaEmpresaDeIntegracionSinCuentaTambienSeAtiendeYLaBitacoraNoLlevaCuenta() {
        ResponseEntity<Map> tenant = llamar(HttpMethod.POST, "/v1/admin/tenants", conClaveDePlataforma(), "{\"ruc\":\"20100066611\",\"razon_social\":\"INTEGRADOR SAC\",\"entorno\":\"BETA\"}");
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = '20100066611'", UUID.class);
        assertThat(tenant.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(cambiarEntorno(empresa, "PRODUCCION").getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> fila = bitacora("CAMBIAR_ENTORNO_EMPRESA").get(0);
        assertThat(fila).containsEntry("tenant_id", empresa).containsEntry("cuenta_id", null);
    }

    @Test void unAdministradorConSesionTambienDejaSuNombreEnLaBitacora() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", false);
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String token = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");

        ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/admin/empresas/" + a.empresaId() + "/entorno", conBearer(token), "{\"entorno\":\"PRODUCCION\"}");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT actor_tipo FROM auditoria_admin WHERE accion = 'CAMBIAR_ENTORNO_EMPRESA'", String.class)).isEqualTo("ADMINISTRADOR");
        assertThat(jdbc.queryForObject("SELECT administrador_id IS NOT NULL FROM auditoria_admin WHERE accion = 'CAMBIAR_ENTORNO_EMPRESA'", Boolean.class)).isTrue();
    }

    // --- datos inválidos y permisos ---------------------------------------------------------------------------------------------------

    @Test void unaEmpresaQueNoExisteEs404UnIdMalFormadoEs400() {
        assertThat(cambiarEntorno(UUID.randomUUID(), "PRODUCCION").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(probar(UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/empresas/no-es-un-uuid/entorno", conClaveDePlataforma(), "{\"entorno\":\"BETA\"}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/empresas/" + UUID.randomUUID() + "/api-keys/no-es-un-uuid/revocar", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(contar("auditoria_admin")).isZero();
    }

    /** Son del administrador: ni el dueño de la empresa, ni una API key, ni una clave errónea pueden, y no cambia nada. */
    @Test void nadieMasPuedeHacerEstasAcciones() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", true);
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");
        List<HttpHeaders> intrusos = List.of(json(), conBearer(a.access()), conApiKey(a.apiKeys().get(0)), conClaveErronea);
        String base = "/v1/admin/empresas/" + a.empresaId();

        for (HttpHeaders h : intrusos) {
            assertThat(llamar(HttpMethod.POST, base + "/entorno", h, "{\"entorno\":\"PRODUCCION\"}").getStatusCode()).as("entorno con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.POST, base + "/api-keys/" + a.keyIds().get(0) + "/revocar", h, null).getStatusCode()).as("revocar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.POST, base + "/prueba-de-conexion", h, null).getStatusCode()).as("probar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(entornoEnLaBase(a.empresaId())).isEqualTo("BETA");
        assertThat(llamar(HttpMethod.GET, "/v1/series", conApiKey(a.apiKeys().get(0)), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(LLAMADO_CON.get()).isNull();
        assertThat(contar("auditoria_admin")).isZero();
    }
}
