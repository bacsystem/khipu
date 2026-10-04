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
import pe.factura.domain.plan.CicloMensual;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gestión de planes (#190) de extremo a extremo: HTTP real, filtros reales, Postgres real. Lo que importa: cambiar un límite **no toca el ciclo en curso** (queda
 * programado para el siguiente), desactivar un plan **no toca a las cuentas que lo tienen**, un plan en uso no se borra, y todo cambio deja su registro.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PlanesAdminE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    static final String UNO = "{\"maximo\":1}";
    static final String SIN_TOPE = "{\"ilimitado\":true}";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
        jdbc.update("DELETE FROM plan WHERE nombre NOT IN ('Gratis', 'Emprende', 'Negocio', 'Pro')");
    }

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    private ResponseEntity<Map> crear(String cuerpo) { return llamar(HttpMethod.POST, "/v1/admin/planes", conClaveDePlataforma(), cuerpo); }

    private ResponseEntity<Map> editar(String id, String cuerpo) { return llamar(HttpMethod.PUT, "/v1/admin/planes/" + id, conClaveDePlataforma(), cuerpo); }

    private ResponseEntity<Map> accion(String id, String accion) { return llamar(HttpMethod.POST, "/v1/admin/planes/" + id + "/" + accion, conClaveDePlataforma(), null); }

    private ResponseEntity<Map> borrar(String id) { return llamar(HttpMethod.DELETE, "/v1/admin/planes/" + id, conClaveDePlataforma(), null); }

    private static String cuerpo(String nombre, String precio, String documentos, int rucs, String usuarios, String keys, int retencion) {
        return "{\"nombre\":\"%s\",\"precio_mensual\":%s,\"limites\":{\"documentos_al_mes\":%s,\"rucs\":%d,\"usuarios\":%s,\"api_keys\":%s,\"retencion_anios\":%d}}"
                .formatted(nombre, precio, documentos, rucs, usuarios, keys, retencion);
    }

    private static String cuerpo(String nombre, String precio, int documentos) {
        return cuerpo(nombre, precio, "{\"maximo\":" + documentos + "}", 1, UNO, UNO, 5);
    }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private Map<String, Object> datos(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode().is2xxSuccessful()).as("%s", r.getBody()).isTrue();
        return (Map<String, Object>) r.getBody().get("datos");
    }

    private String idDe(ResponseEntity<Map> r) { return (String) datos(r).get("id"); }

    private List<Map<String, Object>> listar() {
        ResponseEntity<Map> r = llamar(HttpMethod.GET, "/v1/admin/planes", conClaveDePlataforma(), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) r.getBody().get("datos");
    }

    private Map<String, Object> plan(String nombre) {
        return listar().stream().filter(p -> nombre.equals(p.get("nombre"))).findFirst().orElseThrow();
    }

    private long contar(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    private long registros(String accion) { return jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion = ?", Long.class, accion); }

    private String detalle(String accion) { return jdbc.queryForObject("SELECT detalle FROM auditoria_admin WHERE accion = ?", String.class, accion); }

    record Cliente(String access, UUID cuentaId, String apiKey) {}

    private Cliente cliente(String email, String ruc) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        UUID cuentaId = jdbc.queryForObject("SELECT id FROM cuenta WHERE email = ?", UUID.class, email);
        if (ruc == null) return new Cliente(access, cuentaId, null);
        assertThat(llamar(HttpMethod.POST, "/v1/empresas", conBearer(access), "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s\",\"entorno\":\"BETA\"}".formatted(ruc, ruc)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
        HttpHeaders h = conBearer(access);
        h.set("X-Empresa", empresa.toString());
        String key = (String) ((Map<?, ?>) llamar(HttpMethod.POST, "/v1/empresa/api-keys", h, null).getBody().get("datos")).get("api_key");
        return new Cliente(access, cuentaId, key);
    }

    /** Le da el plan a una cuenta (lo que hará #191 con el cambio de plan), cerrando la suscripción que tenía. */
    private void asignar(UUID cuenta, String planId) {
        jdbc.update("UPDATE suscripcion SET termina_en = now() WHERE cuenta_id = ? AND termina_en IS NULL", cuenta);
        jdbc.update("INSERT INTO suscripcion (cuenta_id, plan_id, inicia_en) VALUES (?, ?, now())", cuenta, UUID.fromString(planId));
    }

    // --- listar -----------------------------------------------------------------------------------------------------------------------------

    @Test void listaLosCuatroPlanesConSusLimitesYCuantasCuentasLosUsan() {
        cliente("ana@negocio.pe", null);
        cliente("luis@otro.pe", null);

        List<Map<String, Object>> planes = listar();

        assertThat(planes).extracting(p -> p.get("nombre")).containsExactly("Gratis", "Emprende", "Negocio", "Pro");
        assertThat(plan("Gratis")).containsEntry("cuentas", 2).containsEntry("por_defecto", true).containsEntry("estado", "ACTIVO");
        assertThat(plan("Emprende")).containsEntry("cuentas", 0);
        Map<String, Object> negocio = (Map<String, Object>) plan("Negocio").get("limites");
        assertThat((Map<String, Object>) negocio.get("documentos_al_mes")).containsEntry("maximo", 1500).containsEntry("ilimitado", false);
        assertThat(negocio).containsEntry("rucs", 3).containsEntry("retencion_anios", 5);
        Map<String, Object> pro = (Map<String, Object>) plan("Pro").get("limites");
        assertThat((Map<String, Object>) pro.get("documentos_al_mes")).containsEntry("ilimitado", true).doesNotContainKey("maximo");
        assertThat(plan("Pro")).containsEntry("precio_mensual", 129.0).doesNotContainKey("limites_programados");
    }

    @Test void unaCuentaQuePasaDePlanCuentaSoloEnElVigente() {
        Cliente a = cliente("ana@negocio.pe", null);
        String emprende = (String) plan("Emprende").get("id");

        asignar(a.cuentaId(), emprende);

        assertThat(plan("Gratis")).containsEntry("cuentas", 0);
        assertThat(plan("Emprende")).containsEntry("cuentas", 1);
    }

    // --- crear ------------------------------------------------------------------------------------------------------------------------------

    @Test void crearUnPlanLoDejaActivoYQuedaEnLaBitacora() {
        ResponseEntity<Map> r = crear(cuerpo("Estudio", "49.90", "{\"maximo\":800}", 2, UNO, SIN_TOPE, 6));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(datos(r)).containsEntry("nombre", "Estudio").containsEntry("estado", "ACTIVO").containsEntry("por_defecto", false).containsEntry("cuentas", 0);
        assertThat(plan("Estudio")).containsEntry("precio_mensual", 49.9);
        assertThat(registros("CREAR_PLAN")).isEqualTo(1);
        assertThat(detalle("CREAR_PLAN")).isEqualTo("plan=Estudio precio=49.90 documentos=800 ruc=2 usuarios=1 api_keys=ilimitado retencion=6");
        assertThat(jdbc.queryForObject("SELECT actor_tipo FROM auditoria_admin WHERE accion = 'CREAR_PLAN'", String.class)).isEqualTo("CLAVE_PLATAFORMA");
    }

    @Test void unAdministradorRealQuedaComoAutorDeLaBitacora() {
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String sesion = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");
        UUID adminId = jdbc.queryForObject("SELECT id FROM administrador WHERE email = 'admin@khipu.pe'", UUID.class);

        ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/admin/planes", conBearer(sesion), cuerpo("Estudio", "49.90", 800));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> fila = jdbc.queryForMap("SELECT actor_tipo, administrador_id FROM auditoria_admin WHERE accion = 'CREAR_PLAN'");
        assertThat(fila).containsEntry("actor_tipo", "ADMINISTRADOR").containsEntry("administrador_id", adminId);
    }

    @Test void losDatosInvalidosSeRechazanSinCrearNiRegistrarNada() {
        long planesAntes = contar("plan");

        assertThat(codigo(crear(cuerpo("Malo", "-1", 10)))).isEqualTo("PRECIO_INVALIDO");
        assertThat(codigo(crear(cuerpo("Raro", "29.999", 10)))).isEqualTo("PRECIO_INVALIDO");
        assertThat(codigo(crear(cuerpo("Cero", "10", 0)))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(crear(cuerpo("SinRuc", "10", "{\"maximo\":5}", 0, UNO, UNO, 5)))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(crear(cuerpo("SinAnios", "10", "{\"maximo\":5}", 1, UNO, UNO, 0)))).isEqualTo("RETENCION_INVALIDA");
        assertThat(codigo(crear(cuerpo(" ", "10", 10)))).isEqualTo("NOMBRE_REQUERIDO");
        assertThat(codigo(crear(cuerpo("x".repeat(41), "10", 10)))).isEqualTo("NOMBRE_INVALIDO");
        assertThat(codigo(crear(cuerpo("Ambiguo", "10", "{\"maximo\":5,\"ilimitado\":true}", 1, UNO, UNO, 5)))).isEqualTo("LIMITE_INVALIDO");
        assertThat(codigo(crear(cuerpo("Vacio", "10", "{}", 1, UNO, UNO, 5)))).isEqualTo("LIMITE_INVALIDO");
        assertThat(crear("{\"nombre\":\"SinLimites\",\"precio_mensual\":1}").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(contar("plan")).isEqualTo(planesAntes);
        assertThat(contar("auditoria_admin")).isZero();
    }

    @Test void elNombreRepetidoEsConflictoSinImportarMayusculasNiEspacios() {
        assertThat(codigo(crear(cuerpo("EMPRENDE", "10", 10)))).isEqualTo("NOMBRE_DUPLICADO");
        assertThat(codigo(crear(cuerpo("  emprende  ", "10", 10)))).isEqualTo("NOMBRE_DUPLICADO");
        assertThat(crear(cuerpo("EMPRENDE", "10", 10)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(contar("plan")).isEqualTo(4);
        assertThat(contar("auditoria_admin")).isZero();
    }

    /** Dos altas simultáneas con el mismo nombre: gana una y la otra es un conflicto limpio, no un 500 (lo decide la base, no un «mirar y luego guardar»). */
    @Test void dosAltasALaVezConElMismoNombreDejanSoloUnPlan() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<HttpStatusCode>> tareas = List.of(() -> crear(cuerpo("Carrera", "10", 10)).getStatusCode(), () -> crear(cuerpo("carrera", "10", 10)).getStatusCode());
            List<HttpStatusCode> estados = new ArrayList<>();
            for (Future<HttpStatusCode> f : pool.invokeAll(tareas)) estados.add(f.get());

            assertThat(estados).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM plan WHERE lower(nombre) = 'carrera'", Long.class)).isEqualTo(1);
            assertThat(registros("CREAR_PLAN")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- editar -----------------------------------------------------------------------------------------------------------------------------

    @Test void elNombreYElPrecioCambianAlInstante() {
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));

        ResponseEntity<Map> r = editar(id, cuerpo("Estudio Plus", "59.90", 800));

        assertThat(datos(r)).containsEntry("nombre", "Estudio Plus");
        assertThat(plan("Estudio Plus")).containsEntry("precio_mensual", 59.9);
        assertThat(detalle("EDITAR_PLAN")).isEqualTo("plan=Estudio; nombre=Estudio>Estudio Plus; precio=49.90>59.90");
    }

    /** Lo central del issue: subir el tope a mitad de mes no regala documentos del ciclo en curso; entra al inicio del ciclo siguiente. */
    @Test void cambiarUnLimiteNoTocaElCicloEnCursoYQuedaProgramadoParaElSiguiente() {
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));

        ResponseEntity<Map> r = editar(id, cuerpo("Estudio", "49.90", 2000));

        Map<String, Object> d = datos(r);
        assertThat(((Map<String, Object>) ((Map<String, Object>) d.get("limites")).get("documentos_al_mes"))).containsEntry("maximo", 800);
        Map<String, Object> programado = (Map<String, Object>) d.get("limites_programados");
        assertThat(((Map<String, Object>) ((Map<String, Object>) programado.get("limites")).get("documentos_al_mes"))).containsEntry("maximo", 2000);
        Instant esperado = CicloMensual.inicioDelSiguiente(Instant.now());
        assertThat(Instant.parse((String) programado.get("aplica_desde"))).isEqualTo(esperado);
        // En la base: el plan sigue con 800, y 2000 espera.
        assertThat(jdbc.queryForObject("SELECT documentos_al_mes FROM plan WHERE id = ?", Integer.class, UUID.fromString(id))).isEqualTo(800);
        assertThat(jdbc.queryForObject("SELECT documentos_al_mes FROM plan_cambio_programado WHERE plan_id = ?", Integer.class, UUID.fromString(id))).isEqualTo(2000);
        assertThat(jdbc.queryForObject("SELECT aplica_desde FROM plan_cambio_programado WHERE plan_id = ?", Timestamp.class, UUID.fromString(id)).toInstant()).isEqualTo(esperado);
        assertThat(detalle("EDITAR_PLAN")).startsWith("plan=Estudio; documentos=800>2000; limites_desde=");
        // Y el listado lo dice igual.
        assertThat(plan("Estudio")).containsKey("limites_programados");
    }

    @Test void bajarUnLimiteTambienEsParaElCicloSiguiente() {
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));

        editar(id, cuerpo("Estudio", "49.90", 100));

        assertThat(jdbc.queryForObject("SELECT documentos_al_mes FROM plan WHERE id = ?", Integer.class, UUID.fromString(id))).isEqualTo(800);
        assertThat(jdbc.queryForObject("SELECT documentos_al_mes FROM plan_cambio_programado WHERE plan_id = ?", Integer.class, UUID.fromString(id))).isEqualTo(100);
    }

    @Test void unSegundoCambioReemplazaAlProgramadoYPonerLosActualesLoCancela() {
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));
        editar(id, cuerpo("Estudio", "49.90", 2000));

        editar(id, cuerpo("Estudio", "49.90", 3000));

        assertThat(contar("plan_cambio_programado")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT documentos_al_mes FROM plan_cambio_programado", Integer.class)).isEqualTo(3000);

        ResponseEntity<Map> cancelado = editar(id, cuerpo("Estudio", "49.90", 800));

        assertThat(datos(cancelado)).doesNotContainKey("limites_programados");
        assertThat(contar("plan_cambio_programado")).isZero();
        assertThat(registros("EDITAR_PLAN")).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT detalle FROM auditoria_admin WHERE accion = 'EDITAR_PLAN' ORDER BY ocurrido_en, id", String.class).get(2))
                .isEqualTo("plan=Estudio; limites_programados=cancelados");
    }

    @Test void editarSinCambiarNadaNoEscribeNiDejaRegistro() {
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));

        assertThat(editar(id, cuerpo("Estudio", "49.90", 800)).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(registros("EDITAR_PLAN")).isZero();
        assertThat(contar("plan_cambio_programado")).isZero();
    }

    /** Editar el plan no toca a las cuentas que ya lo tienen. */
    @Test void editarNoTocaLaSuscripcionDeLasCuentas() {
        Cliente a = cliente("ana@negocio.pe", null);
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));
        asignar(a.cuentaId(), id);
        List<Map<String, Object>> antes = jdbc.queryForList("SELECT id, plan_id, inicia_en, vence_en, termina_en FROM suscripcion WHERE cuenta_id = ? ORDER BY inicia_en", a.cuentaId());

        editar(id, cuerpo("Estudio Plus", "99", 5000));

        assertThat(jdbc.queryForList("SELECT id, plan_id, inicia_en, vence_en, termina_en FROM suscripcion WHERE cuenta_id = ? ORDER BY inicia_en", a.cuentaId())).isEqualTo(antes);
        assertThat(plan("Estudio Plus")).containsEntry("cuentas", 1);
    }

    @Test void editarConDatosInvalidosONombreRepetidoNoCambiaNada() {
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));
        long registrosAntes = contar("auditoria_admin");

        assertThat(codigo(editar(id, cuerpo("Estudio", "-3", 800)))).isEqualTo("PRECIO_INVALIDO");
        assertThat(codigo(editar(id, cuerpo("Pro", "49.90", 800)))).isEqualTo("NOMBRE_DUPLICADO");
        assertThat(codigo(editar(id, cuerpo("Estudio", "49.90", 0)))).isEqualTo("LIMITE_INVALIDO");

        assertThat(plan("Estudio")).containsEntry("precio_mensual", 49.9);
        assertThat(contar("auditoria_admin")).isEqualTo(registrosAntes);
    }

    @Test void editarUnPlanQueNoExisteEs404UnIdMalFormadoEs400() {
        assertThat(editar(UUID.randomUUID().toString(), cuerpo("X", "1", 1)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(editar("no-es-un-uuid", cuerpo("X", "1", 1)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- desactivar -------------------------------------------------------------------------------------------------------------------------

    /** Desactivar saca el plan de la oferta, pero las cuentas que ya lo tienen siguen con su suscripción intacta. */
    @Test void desactivarUnPlanNoTocaALasCuentasQueYaLoTienen() {
        Cliente a = cliente("ana@negocio.pe", null);
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));
        asignar(a.cuentaId(), id);
        List<Map<String, Object>> antes = jdbc.queryForList("SELECT id, plan_id, inicia_en, vence_en, termina_en FROM suscripcion WHERE cuenta_id = ? ORDER BY inicia_en", a.cuentaId());

        ResponseEntity<Map> r = accion(id, "desactivar");

        assertThat(datos(r)).containsEntry("estado", "INACTIVO").containsEntry("cuentas", 1);
        assertThat(plan("Estudio")).containsEntry("estado", "INACTIVO").containsEntry("cuentas", 1);
        assertThat(jdbc.queryForList("SELECT id, plan_id, inicia_en, vence_en, termina_en FROM suscripcion WHERE cuenta_id = ? ORDER BY inicia_en", a.cuentaId())).isEqualTo(antes);
        assertThat(detalle("DESACTIVAR_PLAN")).isEqualTo("plan=Estudio cuentas=1");
    }

    @Test void activarLoDevuelveALaOfertaYAmbosCambiosQuedanRegistrados() {
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));
        accion(id, "desactivar");

        ResponseEntity<Map> r = accion(id, "activar");

        assertThat(datos(r)).containsEntry("estado", "ACTIVO");
        assertThat(registros("DESACTIVAR_PLAN")).isEqualTo(1);
        assertThat(registros("ACTIVAR_PLAN")).isEqualTo(1);
    }

    @Test void elPlanPorDefectoNoSeDesactiva() {
        String gratis = (String) plan("Gratis").get("id");

        ResponseEntity<Map> r = accion(gratis, "desactivar");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("PLAN_POR_DEFECTO");
        assertThat(plan("Gratis")).containsEntry("estado", "ACTIVO");
        assertThat(contar("auditoria_admin")).isZero();
    }

    @Test void desactivarUnoInactivoOActivarUnoActivoEsConflicto() {
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));

        assertThat(codigo(accion(id, "activar"))).isEqualTo("PLAN_YA_ACTIVO");
        accion(id, "desactivar");
        assertThat(codigo(accion(id, "desactivar"))).isEqualTo("PLAN_YA_INACTIVO");
        assertThat(registros("DESACTIVAR_PLAN")).isEqualTo(1);
    }

    // --- borrar -----------------------------------------------------------------------------------------------------------------------------

    @Test void unPlanQueNadieUsoSeBorraYQuedaRegistrado() {
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));
        editar(id, cuerpo("Estudio", "49.90", 2000));

        ResponseEntity<Map> r = borrar(id);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listar()).extracting(p -> p.get("nombre")).doesNotContain("Estudio");
        assertThat(contar("plan_cambio_programado")).as("su cambio programado se va con él").isZero();
        assertThat(detalle("ELIMINAR_PLAN")).isEqualTo("plan=Estudio");
    }

    /** «Un plan con cuentas activas no se puede borrar, solo desactivar». */
    @Test void unPlanConCuentasNoSeBorra() {
        Cliente a = cliente("ana@negocio.pe", null);
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));
        asignar(a.cuentaId(), id);

        ResponseEntity<Map> r = borrar(id);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("PLAN_EN_USO");
        assertThat((String) r.getBody().get("mensaje")).contains("1 cuenta").contains("desactívalo");
        assertThat(plan("Estudio")).containsEntry("cuentas", 1);
        assertThat(registros("ELIMINAR_PLAN")).isZero();
    }

    @Test void unPlanQueSoloTieneHistorialTampocoSeBorra() {
        Cliente a = cliente("ana@negocio.pe", null);
        String id = idDe(crear(cuerpo("Estudio", "49.90", 800)));
        asignar(a.cuentaId(), id);
        asignar(a.cuentaId(), (String) plan("Gratis").get("id"));

        ResponseEntity<Map> r = borrar(id);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("PLAN_EN_USO");
        assertThat(plan("Estudio")).containsEntry("cuentas", 0);
    }

    @Test void elPlanPorDefectoNoSeBorraNiSiNadieLoUsaYLosIdsMalosSeRechazan() {
        assertThat(codigo(borrar((String) plan("Gratis").get("id")))).isEqualTo("PLAN_POR_DEFECTO");
        assertThat(borrar(UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(borrar("no-es-un-uuid").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(contar("plan")).isEqualTo(4);
        assertThat(contar("auditoria_admin")).isZero();
    }

    // --- quién puede ------------------------------------------------------------------------------------------------------------------------

    /** Los planes son del administrador: ni el dueño de una cuenta, ni una API key, ni una clave errónea pueden leerlos ni tocarlos, y no cambia nada. */
    @Test void nadieMasPuedeVerNiCambiarLosPlanes() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        String id = (String) plan("Emprende").get("id");
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");
        List<HttpHeaders> intrusos = List.of(json(), conBearer(a.access()), conApiKey(a.apiKey()), conClaveErronea);

        for (HttpHeaders h : intrusos) {
            assertThat(llamar(HttpMethod.GET, "/v1/admin/planes", h, null).getStatusCode()).as("listar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.POST, "/v1/admin/planes", h, cuerpo("Intruso", "1", 1)).getStatusCode()).as("crear con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.PUT, "/v1/admin/planes/" + id, h, cuerpo("Emprende", "0", 1)).getStatusCode()).as("editar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.POST, "/v1/admin/planes/" + id + "/desactivar", h, null).getStatusCode()).as("desactivar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.DELETE, "/v1/admin/planes/" + id, h, null).getStatusCode()).as("borrar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(contar("plan")).isEqualTo(4);
        assertThat(plan("Emprende")).containsEntry("estado", "ACTIVO");
        assertThat(contar("auditoria_admin")).isZero();
    }
}
