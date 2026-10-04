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
import pe.factura.application.port.in.AplicarCambiosDePlanUseCase;
import pe.factura.domain.plan.CicloMensual;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * El cambio de plan de una cuenta (#191) de extremo a extremo: HTTP real, filtros reales, Postgres real. Lo que importa: **subir entra ya, bajar espera al ciclo
 * siguiente** (y la cuenta sigue con su plan de hoy hasta entonces), siempre hay exactamente una suscripción activa, y todo queda en la bitácora.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CambioDePlanE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired AplicarCambiosDePlanUseCase aplicador;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
        jdbc.update("UPDATE plan SET estado = 'ACTIVO'");
    }

    record Cliente(String access, UUID cuentaId, List<UUID> empresas, String apiKey) {}

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    private Cliente cliente(String email, String... rucs) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        List<UUID> empresas = new ArrayList<>();
        String key = null;
        for (String ruc : rucs) {
            assertThat(llamar(HttpMethod.POST, "/v1/empresas", conBearer(access), "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s\",\"entorno\":\"BETA\"}".formatted(ruc, ruc)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID id = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
            empresas.add(id);
            HttpHeaders h = conBearer(access);
            h.set("X-Empresa", id.toString());
            key = (String) ((Map<?, ?>) llamar(HttpMethod.POST, "/v1/empresa/api-keys", h, null).getBody().get("datos")).get("api_key");
        }
        UUID cuentaId = jdbc.queryForObject("SELECT id FROM cuenta WHERE email = ?", UUID.class, email);
        return new Cliente(access, cuentaId, empresas, key);
    }

    private UUID plan(String nombre) { return jdbc.queryForObject("SELECT id FROM plan WHERE nombre = ?", UUID.class, nombre); }

    private static String cuerpo(UUID plan, Instant vence, Integer gracia) {
        StringBuilder b = new StringBuilder("{\"plan_id\":\"" + plan + "\"");
        if (vence != null) b.append(",\"vence_en\":\"").append(vence).append("\"");
        if (gracia != null) b.append(",\"dias_de_gracia\":").append(gracia);
        return b.append("}").toString();
    }

    private ResponseEntity<Map> cambiar(UUID cuenta, String nombrePlan, Instant vence, Integer gracia) {
        return llamar(HttpMethod.POST, "/v1/admin/cuentas/" + cuenta + "/plan", conClaveDePlataforma(), cuerpo(plan(nombrePlan), vence, gracia));
    }

    private ResponseEntity<Map> verPlan(UUID cuenta) { return llamar(HttpMethod.GET, "/v1/admin/cuentas/" + cuenta + "/plan", conClaveDePlataforma(), null); }

    private Map<String, Object> datos(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("datos");
    }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private String nombreDelPlan(UUID cuenta) { return (String) ((Map<String, Object>) datos(verPlan(cuenta)).get("plan")).get("nombre"); }

    private long activas(UUID cuenta) { return jdbc.queryForObject("SELECT count(*) FROM suscripcion WHERE cuenta_id = ? AND termina_en IS NULL", Long.class, cuenta); }

    private long contar(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    private long registros() { return jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion = 'CAMBIAR_PLAN'", Long.class); }

    private Map<String, Object> cuentasDelPlan() {
        List<Map<String, Object>> planes = (List<Map<String, Object>>) llamar(HttpMethod.GET, "/v1/admin/planes", conClaveDePlataforma(), null).getBody().get("datos");
        Map<String, Object> r = new java.util.HashMap<>();
        for (Map<String, Object> p : planes) r.put((String) p.get("nombre"), p.get("cuentas"));
        return r;
    }

    private int numero = 1;

    private void documento(UUID empresa, String estado) {
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, '01', 'F001', ?, ?, ?, 'archivo')",
                UUID.randomUUID(), empresa, numero++, java.sql.Date.valueOf(LocalDate.now(CicloMensual.ZONA)), estado);
    }

    /** A segundos: Postgres guarda microsegundos y un instante con más precisión no vuelve igual. */
    static final Instant VENCE = Instant.now().plus(Duration.ofDays(40)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    // --- subir ------------------------------------------------------------------------------------------------------------------------------

    @Test void unaCuentaNuevaEstaEnGratisSinVencimiento() {
        Cliente c = cliente("ana@negocio.pe");

        Map<String, Object> d = datos(verPlan(c.cuentaId()));

        assertThat(((Map<String, Object>) d.get("plan")).get("nombre")).isEqualTo("Gratis");
        assertThat(d).containsEntry("estado", "VIGENTE").doesNotContainKey("vence_en").doesNotContainKey("programado");
    }

    @Test void subirDePlanEntraAhoraConSuVencimientoYSuGracia() {
        Cliente c = cliente("ana@negocio.pe");

        ResponseEntity<Map> r = cambiar(c.cuentaId(), "Emprende", VENCE, 5);

        Map<String, Object> d = datos(r);
        assertThat(((Map<String, Object>) d.get("plan")).get("nombre")).isEqualTo("Emprende");
        assertThat(d).containsEntry("estado", "VIGENTE").containsEntry("dias_de_gracia", 5).doesNotContainKey("programado");
        assertThat(Instant.parse((String) d.get("vence_en"))).isEqualTo(VENCE);
        assertThat(Instant.parse((String) d.get("hasta_cuando_cubre"))).isEqualTo(VENCE.plus(Duration.ofDays(5)));
        assertThat(nombreDelPlan(c.cuentaId())).isEqualTo("Emprende");
        assertThat(activas(c.cuentaId())).isEqualTo(1);
        // La suscripción anterior se cerró en el mismo instante en que empezó la nueva: sin hueco ni solape.
        List<Map<String, Object>> historial = jdbc.queryForList("SELECT plan_id, inicia_en, termina_en FROM suscripcion WHERE cuenta_id = ? ORDER BY inicia_en", c.cuentaId());
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).get("plan_id")).isEqualTo(plan("Gratis"));
        assertThat(historial.get(0).get("termina_en")).isEqualTo(historial.get(1).get("inicia_en"));
        assertThat(historial.get(1).get("termina_en")).isNull();
    }

    @Test void elListadoDePlanesCuentaLaCuentaEnSuPlanNuevo() {
        Cliente c = cliente("ana@negocio.pe");
        assertThat(cuentasDelPlan()).containsEntry("Gratis", 1).containsEntry("Emprende", 0);

        cambiar(c.cuentaId(), "Emprende", VENCE, 0);

        assertThat(cuentasDelPlan()).containsEntry("Gratis", 0).containsEntry("Emprende", 1);
    }

    @Test void subirDePlanQuedaEnLaBitacoraConQuienDesdeCualHastaCualYEnLaCuenta() {
        Cliente c = cliente("ana@negocio.pe");

        cambiar(c.cuentaId(), "Negocio", VENCE, 7);

        Map<String, Object> fila = jdbc.queryForMap("SELECT actor_tipo, cuenta_id, detalle FROM auditoria_admin WHERE accion = 'CAMBIAR_PLAN'");
        assertThat(fila).containsEntry("actor_tipo", "CLAVE_PLATAFORMA").containsEntry("cuenta_id", c.cuentaId());
        assertThat((String) fila.get("detalle")).startsWith("desde=Gratis hacia=Negocio direccion=SUBIDA efecto=INMEDIATO vence=").endsWith("gracia=7");
    }

    @Test void unAdministradorRealQuedaComoAutorDelCambio() {
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String sesion = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");
        UUID adminId = jdbc.queryForObject("SELECT id FROM administrador WHERE email = 'admin@khipu.pe'", UUID.class);
        Cliente c = cliente("ana@negocio.pe");

        ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/plan", conBearer(sesion), cuerpo(plan("Emprende"), VENCE, 0));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> fila = jdbc.queryForMap("SELECT actor_tipo, administrador_id FROM auditoria_admin WHERE accion = 'CAMBIAR_PLAN'");
        assertThat(fila).containsEntry("actor_tipo", "ADMINISTRADOR").containsEntry("administrador_id", adminId);
    }

    // --- bajar ------------------------------------------------------------------------------------------------------------------------------

    /** Bajar no corta a nadie lo que ya pagó: la cuenta sigue con su plan, y el cambio espera al inicio del ciclo siguiente. */
    @Test void bajarDePlanQuedaProgramadoYLaCuentaSigueConSuPlanDeHoy() {
        Cliente c = cliente("ana@negocio.pe");
        cambiar(c.cuentaId(), "Negocio", VENCE, 0);
        List<Map<String, Object>> antes = jdbc.queryForList("SELECT id, plan_id, inicia_en, vence_en, termina_en FROM suscripcion WHERE cuenta_id = ? ORDER BY inicia_en", c.cuentaId());

        ResponseEntity<Map> r = cambiar(c.cuentaId(), "Emprende", VENCE.plus(Duration.ofDays(30)), 3);

        Map<String, Object> d = datos(r);
        assertThat(((Map<String, Object>) d.get("plan")).get("nombre")).as("sigue con el de hoy").isEqualTo("Negocio");
        Map<String, Object> programado = (Map<String, Object>) d.get("programado");
        assertThat(((Map<String, Object>) programado.get("plan")).get("nombre")).isEqualTo("Emprende");
        assertThat(Instant.parse((String) programado.get("aplica_desde"))).isEqualTo(CicloMensual.inicioDelSiguiente(Instant.now()));
        assertThat(programado).containsEntry("dias_de_gracia", 3);
        assertThat(jdbc.queryForList("SELECT id, plan_id, inicia_en, vence_en, termina_en FROM suscripcion WHERE cuenta_id = ? ORDER BY inicia_en", c.cuentaId())).isEqualTo(antes);
        assertThat(cuentasDelPlan()).containsEntry("Negocio", 1).containsEntry("Emprende", 0);
        assertThat((String) jdbc.queryForObject("SELECT detalle FROM auditoria_admin WHERE detalle LIKE '%BAJADA%'", String.class))
                .startsWith("desde=Negocio hacia=Emprende direccion=BAJADA efecto=CICLO_SIGUIENTE aplica_desde=").endsWith("gracia=3");
    }

    @Test void unaSegundaBajadaReemplazaALaPrimera() {
        Cliente c = cliente("ana@negocio.pe");
        cambiar(c.cuentaId(), "Pro", VENCE, 0);
        cambiar(c.cuentaId(), "Negocio", VENCE.plus(Duration.ofDays(30)), 0);

        cambiar(c.cuentaId(), "Gratis", null, 0);

        assertThat(contar("suscripcion_cambio_programado")).isEqualTo(1);
        Map<String, Object> programado = (Map<String, Object>) datos(verPlan(c.cuentaId())).get("programado");
        assertThat(((Map<String, Object>) programado.get("plan")).get("nombre")).isEqualTo("Gratis");
        assertThat(nombreDelPlan(c.cuentaId())).isEqualTo("Pro");
    }

    @Test void unaSubidaCancelaLaBajadaQueEsperaba() {
        Cliente c = cliente("ana@negocio.pe");
        cambiar(c.cuentaId(), "Negocio", VENCE, 0);
        cambiar(c.cuentaId(), "Emprende", VENCE.plus(Duration.ofDays(30)), 0);
        assertThat(contar("suscripcion_cambio_programado")).isEqualTo(1);

        cambiar(c.cuentaId(), "Pro", VENCE, 0);

        assertThat(contar("suscripcion_cambio_programado")).isZero();
        assertThat(datos(verPlan(c.cuentaId()))).doesNotContainKey("programado");
        assertThat(nombreDelPlan(c.cuentaId())).isEqualTo("Pro");
    }

    @Test void renovarElMismoPlanEntraAhoraYCancelaLaBajada() {
        Cliente c = cliente("ana@negocio.pe");
        cambiar(c.cuentaId(), "Negocio", VENCE, 0);
        cambiar(c.cuentaId(), "Emprende", VENCE.plus(Duration.ofDays(30)), 0);
        Instant otroVencimiento = VENCE.plus(Duration.ofDays(31));

        Map<String, Object> d = datos(cambiar(c.cuentaId(), "Negocio", otroVencimiento, 2));

        assertThat(Instant.parse((String) d.get("vence_en"))).isEqualTo(otroVencimiento);
        assertThat(d).doesNotContainKey("programado");
        assertThat((String) jdbc.queryForObject("SELECT detalle FROM auditoria_admin WHERE detalle LIKE '%RENOVACION%'", String.class)).startsWith("desde=Negocio hacia=Negocio direccion=RENOVACION");
    }

    // --- llegada la fecha -------------------------------------------------------------------------------------------------------------------

    /** El trabajo programado convierte la bajada en la suscripción activa, con la fecha programada como inicio y el vencimiento y la gracia que se indicaron. */
    @Test void llegadaLaFechaLaBajadaPasaAVigenteDesdeLaFechaProgramada() {
        Cliente c = cliente("ana@negocio.pe");
        cambiar(c.cuentaId(), "Negocio", VENCE, 0);
        Instant masTarde = VENCE.plus(Duration.ofDays(30));
        cambiar(c.cuentaId(), "Emprende", masTarde, 3);
        // El reloj no se mueve en un E2E: se echan hacia atrás la suscripción actual y la fecha programada (ayer y hoy menos una hora).
        jdbc.update("UPDATE suscripcion SET inicia_en = now() - interval '2 days' WHERE cuenta_id = ? AND termina_en IS NULL", c.cuentaId());
        Instant fecha = Instant.now().minus(Duration.ofHours(1));
        jdbc.update("UPDATE suscripcion_cambio_programado SET aplica_desde = ? WHERE cuenta_id = ?", Timestamp.from(fecha), c.cuentaId());
        assertThat(nombreDelPlan(c.cuentaId())).as("todavía no se aplicó").isEqualTo("Negocio");

        AplicarCambiosDePlanUseCase.Resultado r = aplicador.aplicarVencidos();

        assertThat(r.aplicados()).isEqualTo(1);
        assertThat(r.fallidos()).isZero();
        assertThat(nombreDelPlan(c.cuentaId())).isEqualTo("Emprende");
        assertThat(contar("suscripcion_cambio_programado")).isZero();
        assertThat(activas(c.cuentaId())).isEqualTo(1);
        List<Map<String, Object>> historial = jdbc.queryForList("SELECT plan_id, inicia_en, vence_en, dias_de_gracia, termina_en FROM suscripcion WHERE cuenta_id = ? AND plan_id IN (?, ?) ORDER BY inicia_en",
                c.cuentaId(), plan("Negocio"), plan("Emprende"));
        assertThat(((Timestamp) historial.get(0).get("termina_en")).toInstant()).isEqualTo(((Timestamp) historial.get(1).get("inicia_en")).toInstant());
        assertThat(((Timestamp) historial.get(1).get("inicia_en")).toInstant().toEpochMilli()).isEqualTo(fecha.toEpochMilli());
        assertThat(historial.get(1).get("dias_de_gracia")).isEqualTo(3);
        assertThat(cuentasDelPlan()).containsEntry("Negocio", 0).containsEntry("Emprende", 1);
        assertThat(aplicador.aplicarVencidos().aplicados()).as("una segunda pasada no vuelve a aplicar nada").isZero();
    }

    @Test void loQueTodaviaNoLlegoNoSeAplica() {
        Cliente c = cliente("ana@negocio.pe");
        cambiar(c.cuentaId(), "Negocio", VENCE, 0);
        cambiar(c.cuentaId(), "Emprende", VENCE.plus(Duration.ofDays(30)), 0);

        assertThat(aplicador.aplicarVencidos().aplicados()).isZero();

        assertThat(nombreDelPlan(c.cuentaId())).isEqualTo("Negocio");
        assertThat(contar("suscripcion_cambio_programado")).isEqualTo(1);
    }

    // --- previsualizar ----------------------------------------------------------------------------------------------------------------------

    @Test void laPrevisualizacionDiceCuandoEntraYElConsumoDelMesSoloDeLosAceptados() {
        Cliente c = cliente("ana@negocio.pe", "20100066603", "20100066611");
        cambiar(c.cuentaId(), "Negocio", VENCE, 0);
        for (int i = 0; i < 200; i++) documento(c.empresas().get(0), "ACEPTADO");
        for (int i = 0; i < 150; i++) documento(c.empresas().get(1), "ACEPTADO_CON_OBS");
        for (int i = 0; i < 50; i++) documento(c.empresas().get(0), "RECHAZADO");

        Map<String, Object> d = datos(llamar(HttpMethod.GET, "/v1/admin/cuentas/" + c.cuentaId() + "/plan/previsualizacion?plan_id=" + plan("Emprende"), conClaveDePlataforma(), null));

        assertThat(d).containsEntry("direccion", "BAJADA").containsEntry("efecto", "CICLO_SIGUIENTE").containsEntry("consumo_del_mes", 350).containsEntry("supera_el_limite", true);
        assertThat(d).containsEntry("mes", YearMonth.now(CicloMensual.ZONA).toString());
        assertThat(Instant.parse((String) d.get("aplica_desde"))).isEqualTo(CicloMensual.inicioDelSiguiente(Instant.now()));
        assertThat(((Map<String, Object>) d.get("limite_de_documentos")).get("maximo")).isEqualTo(300);
        assertThat(((Map<String, Object>) d.get("plan_actual")).get("nombre")).isEqualTo("Negocio");
        assertThat(((Map<String, Object>) d.get("plan_nuevo")).get("nombre")).isEqualTo("Emprende");
    }

    @Test void subirDePlanEnLaPrevisualizacionEntraYaYNoAdvierteSiCabe() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        for (int i = 0; i < 20; i++) documento(c.empresas().get(0), "ACEPTADO");

        Map<String, Object> d = datos(llamar(HttpMethod.GET, "/v1/admin/cuentas/" + c.cuentaId() + "/plan/previsualizacion?plan_id=" + plan("Emprende"), conClaveDePlataforma(), null));

        assertThat(d).containsEntry("direccion", "SUBIDA").containsEntry("efecto", "INMEDIATO").containsEntry("consumo_del_mes", 20).containsEntry("supera_el_limite", false);
    }

    @Test void previsualizarNoEscribeNada() {
        Cliente c = cliente("ana@negocio.pe");
        long suscripciones = contar("suscripcion");

        llamar(HttpMethod.GET, "/v1/admin/cuentas/" + c.cuentaId() + "/plan/previsualizacion?plan_id=" + plan("Pro"), conClaveDePlataforma(), null);

        assertThat(contar("suscripcion")).isEqualTo(suscripciones);
        assertThat(contar("suscripcion_cambio_programado")).isZero();
        assertThat(contar("auditoria_admin")).isZero();
        assertThat(nombreDelPlan(c.cuentaId())).isEqualTo("Gratis");
    }

    // --- lo que se rechaza ------------------------------------------------------------------------------------------------------------------

    @Test void unPlanDePagoSinVencimientoSeRechazaYUnoGratisNoLoNecesita() {
        Cliente c = cliente("ana@negocio.pe");

        ResponseEntity<Map> r = cambiar(c.cuentaId(), "Emprende", null, 0);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(r)).isEqualTo("VENCIMIENTO_REQUERIDO");
        assertThat(cambiar(c.cuentaId(), "Gratis", null, 0).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void unVencimientoEnElPasadoOUnaGraciaFueraDeRangoSeRechazan() {
        Cliente c = cliente("ana@negocio.pe");

        assertThat(codigo(cambiar(c.cuentaId(), "Emprende", Instant.now().minus(Duration.ofDays(1)), 0))).isEqualTo("SUSCRIPCION_FECHAS_INVALIDAS");
        assertThat(codigo(cambiar(c.cuentaId(), "Emprende", VENCE, 91))).isEqualTo("GRACIA_INVALIDA");
        assertThat(codigo(cambiar(c.cuentaId(), "Emprende", VENCE, -1))).isEqualTo("GRACIA_INVALIDA");

        assertThat(registros()).isZero();
        assertThat(nombreDelPlan(c.cuentaId())).isEqualTo("Gratis");
        assertThat(activas(c.cuentaId())).isEqualTo(1);
    }

    @Test void unPlanFueraDeLaOfertaNoSeAsignaPeroLaCuentaQueYaLoTieneLoConserva() {
        Cliente c = cliente("ana@negocio.pe");
        cambiar(c.cuentaId(), "Emprende", VENCE, 0);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/planes/" + plan("Emprende") + "/desactivar", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        Cliente otra = cliente("luis@otro.pe");

        ResponseEntity<Map> r = cambiar(otra.cuentaId(), "Emprende", VENCE, 0);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("PLAN_INACTIVO");
        assertThat(nombreDelPlan(c.cuentaId())).as("quien ya lo tenía lo conserva").isEqualTo("Emprende");
        assertThat(nombreDelPlan(otra.cuentaId())).isEqualTo("Gratis");
    }

    @Test void unaCuentaOUnPlanQueNoExistenSon404YUnCuerpoSinPlanEs422() {
        Cliente c = cliente("ana@negocio.pe");

        assertThat(cambiar(UUID.randomUUID(), "Emprende", VENCE, 0).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/plan", conClaveDePlataforma(), cuerpo(UUID.randomUUID(), VENCE, 0)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/plan", conClaveDePlataforma(), "{}").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(llamar(HttpMethod.GET, "/v1/admin/cuentas/no-es-un-uuid/plan", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(registros()).isZero();
    }

    /** Cambiar el plan de una cuenta no toca el de otra. */
    @Test void cambiarElPlanDeUnaCuentaNoTocaElDeOtra() {
        Cliente a = cliente("ana@negocio.pe");
        Cliente b = cliente("luis@otro.pe");

        cambiar(a.cuentaId(), "Pro", VENCE, 0);

        assertThat(nombreDelPlan(b.cuentaId())).isEqualTo("Gratis");
        assertThat(activas(b.cuentaId())).isEqualTo(1);
    }

    /** Dos administradores cambian el plan de la misma cuenta a la vez: pase lo que pase, queda exactamente una suscripción activa y nadie recibe un 500. */
    @Test void dosCambiosALaVezDejanSiempreUnaSolaSuscripcionActiva() throws Exception {
        Cliente c = cliente("ana@negocio.pe");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<HttpStatusCode>> tareas = List.of(() -> cambiar(c.cuentaId(), "Emprende", VENCE, 0).getStatusCode(), () -> cambiar(c.cuentaId(), "Negocio", VENCE, 0).getStatusCode());
            List<HttpStatusCode> estados = new ArrayList<>();
            for (Future<HttpStatusCode> f : pool.invokeAll(tareas)) estados.add(f.get());

            assertThat(estados).allMatch(e -> e.equals(HttpStatus.OK) || e.equals(HttpStatus.CONFLICT));
            assertThat(estados).contains(HttpStatus.OK);
            assertThat(activas(c.cuentaId())).isEqualTo(1);
            assertThat(registros()).isEqualTo(estados.stream().filter(e -> e.equals(HttpStatus.OK)).count());
        } finally {
            pool.shutdownNow();
        }
    }

    // --- quién puede ------------------------------------------------------------------------------------------------------------------------

    /** El plan es del administrador: ni el dueño de la cuenta, ni una API key, ni una clave errónea pueden verlo ni cambiarlo. */
    @Test void nadieMasPuedeVerNiCambiarElPlanDeUnaCuenta() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        for (HttpHeaders h : List.of(json(), conBearer(c.access()), conApiKey(c.apiKey()), conClaveErronea)) {
            assertThat(llamar(HttpMethod.GET, "/v1/admin/cuentas/" + c.cuentaId() + "/plan", h, null).getStatusCode()).as("ver con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.GET, "/v1/admin/cuentas/" + c.cuentaId() + "/plan/previsualizacion?plan_id=" + plan("Pro"), h, null).getStatusCode()).as("previsualizar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/plan", h, cuerpo(plan("Pro"), VENCE, 0)).getStatusCode()).as("cambiar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(nombreDelPlan(c.cuentaId())).isEqualTo("Gratis");
        assertThat(registros()).isZero();
    }
}
