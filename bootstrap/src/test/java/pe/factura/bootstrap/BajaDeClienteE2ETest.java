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
 * Baja lógica de un cliente (#201) de extremo a extremo: HTTP real, filtros reales, Postgres real. Lo que importa: la cuenta y sus empresas salen de
 * los listados operativos pero se pueden consultar con un filtro explícito; **se conserva todo** (comprobantes incluidos, consultables); el RUC sigue
 * ocupado; es reversible y queda en la bitácora.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class BajaDeClienteE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    record Cliente(String email, String access, UUID cuentaId, List<UUID> empresas, List<String> apiKeys) {}

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
        return new Cliente(email, access, cuentaId, empresas, keys);
    }

    /** Un comprobante emitido, escrito directo: lo que importa aquí es que la baja no lo toque, no el camino de emisión. */
    private UUID comprobante(UUID empresa, int numero) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo)
                VALUES (?, ?, '01', 'F001', ?, '2026-09-15', 'ACEPTADO', ?)""", id, empresa, numero, "doc-" + numero);
        jdbc.update("""
                INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre,
                                         total_gravado, total_exonerado, total_inafecto, total_igv, total)
                VALUES (?, '0101', 'PEN', '6', '20601234565', 'CLIENTE SAC', 0, 0, 0, 0, 118.00)""", id);
        return id;
    }

    private ResponseEntity<Map> darDeBaja(UUID cuenta, String cuerpo) { return llamar(HttpMethod.POST, "/v1/admin/cuentas/" + cuenta + "/baja", conClaveDePlataforma(), cuerpo); }

    private ResponseEntity<Map> reponer(UUID cuenta) { return llamar(HttpMethod.POST, "/v1/admin/cuentas/" + cuenta + "/reponer", conClaveDePlataforma(), null); }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private long contar(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    private List<String> emailsDeCuentas(String query) {
        ResponseEntity<Map> r = llamar(HttpMethod.GET, "/v1/admin/cuentas" + query, conClaveDePlataforma(), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<Map<String, Object>>) r.getBody().get("datos")).stream().map(f -> (String) f.get("email")).toList();
    }

    private List<String> rucsDeEmpresas(String query) {
        ResponseEntity<Map> r = llamar(HttpMethod.GET, "/v1/admin/empresas" + query, conClaveDePlataforma(), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<Map<String, Object>>) r.getBody().get("datos")).stream().map(f -> (String) f.get("ruc")).toList();
    }

    private String total(String ruta, String query) {
        return llamar(HttpMethod.GET, ruta + query, conClaveDePlataforma(), null).getHeaders().getFirst("X-Total-Count");
    }

    // --- sale de los listados, pero se puede consultar ------------------------------------------------------------------------------

    @Test void laBajaSacaALaCuentaYASusEmpresasDeLosListadosPorDefectoYSePuedeConsultarConUnFiltroExplicito() {
        Cliente a = cliente("ana@negocio.pe", "20100066603", "20100066611");
        cliente("luis@otro.pe", "20100066620");

        assertThat(darDeBaja(a.cuentaId(), "{\"motivo\":\"cerró su negocio\"}").getStatusCode()).isEqualTo(HttpStatus.OK);

        // Por defecto, desaparece de los dos listados operativos, y el total la descuenta.
        assertThat(emailsDeCuentas("")).containsExactly("luis@otro.pe");
        assertThat(total("/v1/admin/cuentas", "")).isEqualTo("1");
        assertThat(rucsDeEmpresas("")).containsExactly("20100066620");
        assertThat(total("/v1/admin/empresas", "")).isEqualTo("1");

        // Con el filtro explícito se encuentra, mezclada o sola.
        assertThat(emailsDeCuentas("?bajas=INCLUIDAS")).containsExactlyInAnyOrder("ana@negocio.pe", "luis@otro.pe");
        assertThat(emailsDeCuentas("?bajas=SOLO")).containsExactly("ana@negocio.pe");
        assertThat(total("/v1/admin/cuentas", "?bajas=SOLO")).isEqualTo("1");
        assertThat(rucsDeEmpresas("?bajas=SOLO")).containsExactlyInAnyOrder("20100066603", "20100066611");
        assertThat(total("/v1/admin/empresas", "?bajas=INCLUIDAS")).isEqualTo("3");

        // La fila dice que está de baja y desde cuándo.
        Map<String, Object> fila = ((List<Map<String, Object>>) llamar(HttpMethod.GET, "/v1/admin/cuentas?bajas=SOLO", conClaveDePlataforma(), null).getBody().get("datos")).get(0);
        assertThat(fila).containsEntry("estado", "BAJA").containsKey("baja_en");
        Map<String, Object> deEmpresa = ((List<Map<String, Object>>) llamar(HttpMethod.GET, "/v1/admin/empresas?bajas=SOLO", conClaveDePlataforma(), null).getBody().get("datos")).get(0);
        assertThat(deEmpresa).containsKey("cuenta_de_baja_en");
    }

    @Test void reponerLaDevuelveALosListados() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        darDeBaja(a.cuentaId(), null);

        assertThat(reponer(a.cuentaId()).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(emailsDeCuentas("")).containsExactly("ana@negocio.pe");
        assertThat(rucsDeEmpresas("")).containsExactly("20100066603");
        assertThat(emailsDeCuentas("?bajas=SOLO")).isEmpty();
        Map<String, Object> detalle = (Map<String, Object>) llamar(HttpMethod.GET, "/v1/admin/cuentas/" + a.cuentaId(), conClaveDePlataforma(), null).getBody().get("datos");
        assertThat(detalle).containsEntry("estado", "ACTIVA").doesNotContainKey("baja_en");
    }

    // --- se conserva todo --------------------------------------------------------------------------------------------------------------

    /** La retención es una obligación legal del emisor: la baja no borra ni toca una sola fila, y los comprobantes siguen siendo consultables. */
    @Test void losComprobantesSeConservanYSiguenSiendoConsultables() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        UUID doc = comprobante(a.empresas().get(0), 1);
        long usuarios = contar("usuario"), empresas = contar("tenant"), keys = contar("api_key"), cuentas = contar("cuenta"), documentos = contar("documento"), comprobantes = contar("comprobante");

        darDeBaja(a.cuentaId(), null);

        assertThat(contar("usuario")).isEqualTo(usuarios);
        assertThat(contar("tenant")).isEqualTo(empresas);
        assertThat(contar("api_key")).isEqualTo(keys);
        assertThat(contar("cuenta")).isEqualTo(cuentas);
        assertThat(contar("documento")).isEqualTo(documentos);
        assertThat(contar("comprobante")).isEqualTo(comprobantes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_key WHERE NOT activa", Long.class)).as("ninguna key se revocó").isZero();
        // Consultables por el administrador (el detalle de la cuenta y de la empresa se abren igual) ...
        Map<String, Object> detalle = (Map<String, Object>) llamar(HttpMethod.GET, "/v1/admin/cuentas/" + a.cuentaId(), conClaveDePlataforma(), null).getBody().get("datos");
        assertThat(detalle).containsEntry("estado", "BAJA").containsKey("baja_en");
        assertThat((List<Object>) detalle.get("comprobantes")).hasSize(1);
        assertThat((List<Object>) detalle.get("empresas")).hasSize(1);
        ResponseEntity<Map> empresa = llamar(HttpMethod.GET, "/v1/admin/empresas/" + a.empresas().get(0), conClaveDePlataforma(), null);
        assertThat(empresa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Object>) ((Map<String, Object>) empresa.getBody().get("datos")).get("comprobantes")).hasSize(1);
        // ... y por su dueño con la API.
        ResponseEntity<Map> porApi = llamar(HttpMethod.GET, "/v1/facturas/" + doc, conApiKey(a.apiKeys().get(0)), null);
        assertThat(porApi.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** La baja no es la suspensión: no corta el acceso. Quien quiera cortar el servicio suspende, y las dos cosas son independientes. */
    @Test void laBajaNoCortaElAccesoDelClienteNiSeMezclaConLaSuspension() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");

        darDeBaja(a.cuentaId(), null);

        assertThat(llamar(HttpMethod.GET, "/v1/empresas", conBearer(a.access()), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(llamar(HttpMethod.GET, "/v1/series", conApiKey(a.apiKeys().get(0)), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/suspender", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        reponer(a.cuentaId());
        Map<String, Object> detalle = (Map<String, Object>) llamar(HttpMethod.GET, "/v1/admin/cuentas/" + a.cuentaId(), conClaveDePlataforma(), null).getBody().get("datos");
        assertThat(detalle).as("reponer no reactiva una cuenta suspendida").containsEntry("estado", "SUSPENDIDA");
    }

    // --- el RUC no queda liberado ------------------------------------------------------------------------------------------------------

    @Test void elRucDeUnaCuentaDeBajaSigueOcupadoParaCualquierOtroRegistro() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        Cliente b = cliente("luis@otro.pe");
        darDeBaja(a.cuentaId(), null);

        // Otro cliente no puede registrar ese RUC desde su portal ...
        ResponseEntity<Map> porPortal = llamar(HttpMethod.POST, "/v1/empresas", conBearer(b.access()), "{\"ruc\":\"20100066603\",\"razon_social\":\"OTRA SAC\",\"entorno\":\"BETA\"}");
        assertThat(porPortal.getStatusCode().is2xxSuccessful()).isFalse();
        // ... ni el administrador darlo de alta como integración ...
        ResponseEntity<Map> porAdmin = llamar(HttpMethod.POST, "/v1/admin/tenants", conClaveDePlataforma(), "{\"ruc\":\"20100066603\",\"razon_social\":\"OTRA SAC\",\"entorno\":\"BETA\"}");
        assertThat(porAdmin.getStatusCode().is2xxSuccessful()).isFalse();
        // ... ni con el alta asistida.
        ResponseEntity<Map> altaAsistida = llamar(HttpMethod.POST, "/v1/admin/cuentas", conClaveDePlataforma(), """
                {"nombre":"Otro","email":"otro@x.pe","empresa":{"ruc":"20100066603","razon_social":"OTRA SAC","entorno":"BETA"},"serie":{"tipo":"01","serie":"F001"}}""");
        assertThat(altaAsistida.getStatusCode().is2xxSuccessful()).isFalse();

        assertThat(contar("tenant")).as("ninguna empresa nueva con ese RUC").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cuenta_id FROM tenant WHERE ruc = '20100066603'", UUID.class)).isEqualTo(a.cuentaId());
    }

    // --- bitácora ----------------------------------------------------------------------------------------------------------------------

    @Test void darDeBajaYReponerQuedanEnLaBitacoraConElMotivoYQuienLoHizo() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");

        darDeBaja(a.cuentaId(), "{\"motivo\":\"  Cerró su negocio  \"}");
        reponer(a.cuentaId());

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT accion, actor_tipo, cuenta_id, detalle FROM auditoria_admin WHERE accion IN ('DAR_DE_BAJA_CUENTA', 'REPONER_CUENTA') ORDER BY ocurrido_en, id");
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("accion", "DAR_DE_BAJA_CUENTA").containsEntry("actor_tipo", "CLAVE_PLATAFORMA").containsEntry("cuenta_id", a.cuentaId())
                .containsEntry("detalle", "motivo=Cerró su negocio");
        assertThat(filas.get(1)).containsEntry("accion", "REPONER_CUENTA").containsEntry("cuenta_id", a.cuentaId()).containsEntry("detalle", null);
    }

    @Test void unAdministradorConSesionTambienDejaSuNombreEnLaBitacora() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String token = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");

        ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/baja", conBearer(token), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT actor_tipo FROM auditoria_admin WHERE accion = 'DAR_DE_BAJA_CUENTA'", String.class)).isEqualTo("ADMINISTRADOR");
        assertThat(jdbc.queryForObject("SELECT administrador_id IS NOT NULL FROM auditoria_admin WHERE accion = 'DAR_DE_BAJA_CUENTA'", Boolean.class)).isTrue();
    }

    // --- conflictos y datos inválidos -------------------------------------------------------------------------------------------------

    @Test void darDeBajaDosVecesOReponerUnaQueNoEstaDeBajaEsConflictoYNoDejaOtroRegistro() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");

        ResponseEntity<Map> noEstaDeBaja = reponer(a.cuentaId());
        assertThat(noEstaDeBaja.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(noEstaDeBaja)).isEqualTo("CUENTA_NO_DE_BAJA");

        assertThat(darDeBaja(a.cuentaId(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> yaDeBaja = darDeBaja(a.cuentaId(), null);
        assertThat(yaDeBaja.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(yaDeBaja)).isEqualTo("CUENTA_YA_DE_BAJA");

        assertThat(contar("auditoria_admin")).as("solo la baja que sí ocurrió").isEqualTo(1);
    }

    @Test void unaCuentaQueNoExisteEs404UnIdMalFormadoEs400YUnValorDeBajasInvalidoEs400() {
        ResponseEntity<Map> noExiste = darDeBaja(UUID.randomUUID(), null);
        assertThat(noExiste.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(codigo(noExiste)).isEqualTo("NO_ENCONTRADO");

        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/no-es-un-uuid/baja", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(llamar(HttpMethod.GET, "/v1/admin/cuentas?bajas=TODAS", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(llamar(HttpMethod.GET, "/v1/admin/empresas?bajas=TODAS", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(contar("auditoria_admin")).isZero();
    }

    @Test void unMotivoDemasiadoLargoSeRechazaYNoDaDeBaja() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");

        ResponseEntity<Map> r = darDeBaja(a.cuentaId(), "{\"motivo\":\"%s\"}".formatted("x".repeat(201)));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(r)).isEqualTo("MOTIVO_INVALIDO");
        assertThat(emailsDeCuentas("")).as("la cuenta sigue en los listados").containsExactly("ana@negocio.pe");
    }

    // --- quién puede ---------------------------------------------------------------------------------------------------------------------

    /** La baja es del administrador: ni el dueño de la cuenta, ni una API key, ni una clave errónea pueden, y no cambia nada. */
    @Test void nadieMasPuedeDarDeBajaNiReponer() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");
        List<HttpHeaders> intrusos = List.of(json(), conBearer(a.access()), conApiKey(a.apiKeys().get(0)), conClaveErronea);

        for (HttpHeaders h : intrusos) {
            for (String accion : List.of("baja", "reponer")) {
                ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/admin/cuentas/" + a.cuentaId() + "/" + accion, h, null);
                assertThat(r.getStatusCode()).as("%s con %s", accion, h).isEqualTo(HttpStatus.UNAUTHORIZED);
            }
        }

        assertThat(emailsDeCuentas("")).containsExactly("ana@negocio.pe");
        assertThat(contar("auditoria_admin")).isZero();
    }
}
