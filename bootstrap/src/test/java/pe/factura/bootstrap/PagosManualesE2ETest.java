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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los pagos registrados a mano (#194) de extremo a extremo: HTTP real, filtros reales, Postgres real. Lo que importa: que el pago, la extensión del vencimiento y la
 * bitácora son **una** transacción (o quedan los tres o ninguno), que el mismo apunte no se anota dos veces, que dos administradores a la vez no se pisan y que nadie más
 * puede ver ni anotar pagos.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PagosManualesE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    static final LocalDate HOY = LocalDate.now(CicloMensual.ZONA);

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
        jdbc.update("ALTER TABLE auditoria_admin DROP CONSTRAINT IF EXISTS ck_prueba_sin_pagos");
    }

    record Cliente(String access, UUID cuentaId, String apiKey) {}

    /** Un RUC válido distinto por cliente: dos empresas no pueden compartirlo. */
    static final String[] RUCS = {"20100066603", "20100066611", "20100066620", "20100066638", "20100066646"};
    int siguienteRuc = 0;

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    private Cliente cliente(String email) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        String ruc = RUCS[siguienteRuc++];
        assertThat(llamar(HttpMethod.POST, "/v1/empresas", conBearer(access), "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA\",\"entorno\":\"BETA\"}".formatted(ruc)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
        HttpHeaders h = conBearer(access);
        h.set("X-Empresa", empresa.toString());
        String key = (String) ((Map<?, ?>) llamar(HttpMethod.POST, "/v1/empresa/api-keys", h, null).getBody().get("datos")).get("api_key");
        return new Cliente(access, jdbc.queryForObject("SELECT id FROM cuenta WHERE email = ?", UUID.class, email), key);
    }

    /** La cuenta pasa a Emprende, pagada hasta dentro de {@code dias} días (negativo: ya venció), con 3 días de gracia. */
    private Instant emprendeHasta(Cliente c, long dias) {
        Instant vence = Instant.now().plus(Duration.ofDays(dias)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("UPDATE suscripcion SET plan_id = (SELECT id FROM plan WHERE nombre = 'Emprende'), inicia_en = LEAST(inicia_en, ?), vence_en = ?, dias_de_gracia = 3 WHERE cuenta_id = ? AND termina_en IS NULL",
                Timestamp.from(vence.minus(Duration.ofDays(30))), Timestamp.from(vence), c.cuentaId());
        return vence;
    }

    private Instant venceActual(Cliente c) {
        Timestamp t = jdbc.queryForObject("SELECT vence_en FROM suscripcion WHERE cuenta_id = ? AND termina_en IS NULL", Timestamp.class, c.cuentaId());
        return t == null ? null : t.toInstant();
    }

    /** El vencimiento al que llega un pago que cubre hasta {@code ultimoDia}: la medianoche de Lima del día siguiente. */
    private static Instant venceria(LocalDate ultimoDia) { return ultimoDia.plusDays(1).atStartOfDay(CicloMensual.ZONA).toInstant(); }

    private static String cuerpo(LocalDate desde, LocalDate hasta, String monto, String medio, LocalDate fecha, String referencia, boolean extender) {
        return "{\"periodo_desde\":\"%s\",\"periodo_hasta\":\"%s\",\"monto\":%s,\"medio\":\"%s\",\"fecha_de_pago\":\"%s\"%s,\"nota\":\"Pagó por el banco\",\"extender_vencimiento\":%s}"
                .formatted(desde, hasta, monto, medio, fecha, referencia == null ? "" : ",\"referencia\":\"" + referencia + "\"", extender);
    }

    private static String cuerpo(LocalDate hasta, String referencia, boolean extender) { return cuerpo(HOY, hasta, "29.00", "YAPE", HOY.minusDays(1), referencia, extender); }

    private ResponseEntity<Map> registrar(Cliente c, String cuerpo) { return llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/pagos", conClaveDePlataforma(), cuerpo); }

    private ResponseEntity<Map> historial(UUID cuenta, String query) { return llamar(HttpMethod.GET, "/v1/admin/cuentas/" + cuenta + "/pagos" + query, conClaveDePlataforma(), null); }

    private Map<String, Object> datos(ResponseEntity<Map> r, HttpStatus esperado) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(esperado);
        return (Map<String, Object>) r.getBody().get("datos");
    }

    private String codigo(ResponseEntity<Map> r) { return (String) r.getBody().get("codigo"); }

    private long contar(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    private long registros() { return jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion = 'REGISTRAR_PAGO'", Long.class); }

    // --- registrar sin extender -------------------------------------------------------------------------------------------------------------

    @Test void registrarUnPagoLoGuardaConTodosSusDatosYNoMueveElVencimiento() {
        Cliente c = cliente("ana@negocio.pe");
        Instant vence = emprendeHasta(c, 5);
        UUID suscripcion = jdbc.queryForObject("SELECT id FROM suscripcion WHERE cuenta_id = ? AND termina_en IS NULL", UUID.class, c.cuentaId());

        Map<String, Object> p = datos(registrar(c, cuerpo(HOY, HOY.plusDays(29), "29.50", "TRANSFERENCIA", HOY.minusDays(2), "OP-777", false)), HttpStatus.CREATED);

        assertThat(p).containsEntry("cuenta_id", c.cuentaId().toString()).containsEntry("periodo_desde", HOY.toString()).containsEntry("periodo_hasta", HOY.plusDays(29).toString())
                .containsEntry("medio", "TRANSFERENCIA").containsEntry("fecha_de_pago", HOY.minusDays(2).toString()).containsEntry("referencia", "OP-777").containsEntry("nota", "Pagó por el banco")
                .doesNotContainKey("extendio_hasta");
        assertThat(((Number) p.get("monto")).doubleValue()).isEqualTo(29.5);
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM pago WHERE id = ?", UUID.fromString((String) p.get("id")));
        assertThat(fila.get("suscripcion_id")).isEqualTo(suscripcion);
        assertThat(fila.get("medio")).isEqualTo("TRANSFERENCIA");
        assertThat(fila.get("extendio_hasta")).isNull();
        assertThat(venceActual(c)).isEqualTo(vence);
        assertThat(registros()).isEqualTo(1);
    }

    @Test void unPagoSinReferenciaSeGuardaYElMismoPagoSinReferenciaSePuedeAnotarOtraVez() {
        Cliente c = cliente("ana@negocio.pe");

        assertThat(registrar(c, cuerpo(HOY.plusDays(29), null, false)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registrar(c, cuerpo(HOY.plusDays(29), null, false)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(contar("pago")).isEqualTo(2);
    }

    @Test void unPagoEnUnPlanGratisSePuedeAnotarSinExtender() {
        Cliente c = cliente("ana@negocio.pe");

        datos(registrar(c, cuerpo(HOY.plusDays(29), "OP-1", false)), HttpStatus.CREATED);

        assertThat(venceActual(c)).isNull();
    }

    // --- extender el vencimiento ------------------------------------------------------------------------------------------------------------

    @Test void extenderMueveElVencimientoALaMedianocheDeLimaYNoCambiaElPlanNiLaGracia() {
        Cliente c = cliente("ana@negocio.pe");
        emprendeHasta(c, 5);
        UUID suscripcion = jdbc.queryForObject("SELECT id FROM suscripcion WHERE cuenta_id = ? AND termina_en IS NULL", UUID.class, c.cuentaId());
        LocalDate ultimo = HOY.plusDays(35);

        Map<String, Object> p = datos(registrar(c, cuerpo(ultimo, "OP-2", true)), HttpStatus.CREATED);

        assertThat(Instant.parse((String) p.get("extendio_hasta"))).isEqualTo(venceria(ultimo));
        assertThat(venceActual(c)).isEqualTo(venceria(ultimo));
        Map<String, Object> s = jdbc.queryForMap("SELECT id, plan_id, dias_de_gracia, termina_en FROM suscripcion WHERE cuenta_id = ? AND termina_en IS NULL", c.cuentaId());
        assertThat(s.get("id")).isEqualTo(suscripcion);
        assertThat(s.get("dias_de_gracia")).isEqualTo(3);
        assertThat(contar("suscripcion")).isEqualTo(1);
        Instant guardado = ((Timestamp) jdbc.queryForObject("SELECT extendio_hasta FROM pago", Timestamp.class)).toInstant();
        assertThat(guardado).isEqualTo(venceria(ultimo));
    }

    @Test void extenderUnPlanVencidoEnGraciaLoDejaAlDiaSegunLaFichaDelPlan() {
        Cliente c = cliente("ana@negocio.pe");
        emprendeHasta(c, -1);
        assertThat(((Map<String, Object>) llamar(HttpMethod.GET, "/v1/admin/cuentas/" + c.cuentaId() + "/plan", conClaveDePlataforma(), null).getBody().get("datos")).get("estado")).isEqualTo("EN_GRACIA");

        registrar(c, cuerpo(HOY.plusDays(30), "OP-3", true));

        assertThat(((Map<String, Object>) llamar(HttpMethod.GET, "/v1/admin/cuentas/" + c.cuentaId() + "/plan", conClaveDePlataforma(), null).getBody().get("datos")).get("estado")).isEqualTo("VIGENTE");
    }

    @Test void extenderNoCancelaLaBajadaProgramada() {
        Cliente c = cliente("ana@negocio.pe");
        emprendeHasta(c, 5);
        UUID gratis = jdbc.queryForObject("SELECT id FROM plan WHERE nombre = 'Gratis'", UUID.class);
        jdbc.update("INSERT INTO suscripcion_cambio_programado (cuenta_id, plan_id, aplica_desde, vence_en, dias_de_gracia) VALUES (?, ?, ?, NULL, 0)", c.cuentaId(), gratis,
                Timestamp.from(Instant.now().plus(Duration.ofDays(3))));

        registrar(c, cuerpo(HOY.plusDays(35), "OP-4", true));

        assertThat(contar("suscripcion_cambio_programado")).isEqualTo(1);
    }

    @Test void extenderUnPlanQueNoVenceEs409YNoDejaNada() {
        Cliente c = cliente("ana@negocio.pe");

        ResponseEntity<Map> r = registrar(c, cuerpo(HOY.plusDays(30), "OP-5", true));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("PLAN_SIN_VENCIMIENTO");
        assertThat(contar("pago")).isZero();
        assertThat(registros()).isZero();
        assertThat(venceActual(c)).isNull();
    }

    @Test void unPagoQueNoAdelantaElVencimientoEs409YNoDejaNada() {
        Cliente c = cliente("ana@negocio.pe");
        Instant vence = emprendeHasta(c, 60);

        ResponseEntity<Map> r = registrar(c, cuerpo(HOY.plusDays(10), "OP-6", true));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("EXTENSION_SIN_EFECTO");
        assertThat(contar("pago")).isZero();
        assertThat(registros()).isZero();
        assertThat(venceActual(c)).isEqualTo(vence);
    }

    // --- el mismo apunte repetido -----------------------------------------------------------------------------------------------------------

    @Test void elMismoPagoRepetidoEs409YNoSeAnotaNiSeExtiendeDosVeces() {
        Cliente c = cliente("ana@negocio.pe");
        emprendeHasta(c, 5);
        registrar(c, cuerpo(HOY.plusDays(35), "OP-7", true));
        Instant despuesDelPrimero = venceActual(c);

        ResponseEntity<Map> r = registrar(c, cuerpo(HOY.plusDays(65), "op-7", true));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(r)).isEqualTo("PAGO_DUPLICADO");
        assertThat(contar("pago")).isEqualTo(1);
        assertThat(registros()).isEqualTo(1);
        assertThat(venceActual(c)).as("el rechazo deshace la extensión que había empezado").isEqualTo(despuesDelPrimero);
    }

    @Test void laMismaReferenciaEnOtraCuentaSiSeAnota() {
        Cliente a = cliente("ana@negocio.pe");
        Cliente b = cliente("beto@negocio.pe");

        assertThat(registrar(a, cuerpo(HOY.plusDays(30), "OP-8", false)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registrar(b, cuerpo(HOY.plusDays(30), "OP-8", false)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // --- atomicidad -------------------------------------------------------------------------------------------------------------------------

    /** Si la bitácora no puede escribir, el pago y la extensión tampoco quedan: no hay un pago sin su rastro. */
    @Test void siLaBitacoraFallaNoQuedaElPagoNiLaExtension() {
        Cliente c = cliente("ana@negocio.pe");
        Instant vence = emprendeHasta(c, 5);
        jdbc.update("ALTER TABLE auditoria_admin ADD CONSTRAINT ck_prueba_sin_pagos CHECK (accion <> 'REGISTRAR_PAGO')");
        try {
            ResponseEntity<Map> r = registrar(c, cuerpo(HOY.plusDays(35), "OP-9", true));

            assertThat(r.getStatusCode().is5xxServerError()).isTrue();
            assertThat(contar("pago")).isZero();
            assertThat(venceActual(c)).isEqualTo(vence);
        } finally {
            jdbc.update("ALTER TABLE auditoria_admin DROP CONSTRAINT ck_prueba_sin_pagos");
        }
    }

    /** Dos administradores que pagan a la vez la misma cuenta: uno anota y extiende, el otro recibe un 409 y no deja nada. Nunca un 500. */
    @Test void dosPagosConExtensionAlMismoTiempoSoloDejanUno() throws Exception {
        Cliente c = cliente("ana@negocio.pe");
        emprendeHasta(c, 5);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<Map>>> futuros = new ArrayList<>();
            for (String ref : List.of("A-1", "B-1")) {
                Callable<ResponseEntity<Map>> tarea = () -> { salida.await(); return registrar(c, cuerpo(HOY.plusDays(35), ref, true)); };
                futuros.add(pool.submit(tarea));
            }
            salida.countDown();
            List<HttpStatusCode> estados = new ArrayList<>();
            for (Future<ResponseEntity<Map>> f : futuros) estados.add(f.get().getStatusCode());

            assertThat(estados).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(contar("pago")).isEqualTo(1);
            assertThat(registros()).isEqualTo(1);
            assertThat(venceActual(c)).isEqualTo(venceria(HOY.plusDays(35)));
        } finally {
            pool.shutdownNow();
        }
    }

    // --- datos inválidos --------------------------------------------------------------------------------------------------------------------

    @Test void losDatosInvalidosSon422YNoDejanNadaNiExtienden() {
        Cliente c = cliente("ana@negocio.pe");
        Instant vence = emprendeHasta(c, 5);
        LocalDate fin = HOY.plusDays(35);
        Map<String, String> casos = new java.util.LinkedHashMap<>();
        casos.put("PERIODO_INVALIDO", cuerpo(fin, HOY, "29", "YAPE", HOY.minusDays(1), "x1", true));
        casos.put("PERIODO_INVALIDO ", cuerpo(HOY, HOY.plusYears(1), "29", "YAPE", HOY.minusDays(1), "x2", true));
        casos.put("MONTO_INVALIDO", cuerpo(HOY, fin, "0", "YAPE", HOY.minusDays(1), "x3", true));
        casos.put("MONTO_INVALIDO ", cuerpo(HOY, fin, "-5", "YAPE", HOY.minusDays(1), "x4", true));
        casos.put("MONTO_INVALIDO  ", cuerpo(HOY, fin, "29.999", "YAPE", HOY.minusDays(1), "x5", true));
        casos.put("FECHA_DE_PAGO_FUTURA", cuerpo(HOY, fin, "29", "YAPE", HOY.plusDays(2), "x6", true));
        casos.put("REFERENCIA_INVALIDA", cuerpo(HOY, fin, "29", "YAPE", HOY.minusDays(1), "r".repeat(101), true));

        casos.forEach((esperado, cuerpo) -> {
            ResponseEntity<Map> r = registrar(c, cuerpo);
            assertThat(r.getStatusCode()).as("%s: %s", esperado, r.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(codigo(r)).isEqualTo(esperado.trim());
        });

        assertThat(contar("pago")).isZero();
        assertThat(registros()).isZero();
        assertThat(venceActual(c)).isEqualTo(vence);
    }

    @Test void unMedioDesconocidoOUnJsonRotoSon400() {
        Cliente c = cliente("ana@negocio.pe");

        assertThat(registrar(c, cuerpo(HOY, HOY.plusDays(30), "29", "BITCOIN", HOY, "z", false)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(registrar(c, "no es json").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(contar("pago")).isZero();
    }

    @Test void unaCuentaQueNoExisteEs404YUnIdMalFormadoEs400() {
        String c = cuerpo(HOY.plusDays(30), "q", false);

        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + UUID.randomUUID() + "/pagos", conClaveDePlataforma(), c).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(historial(UUID.randomUUID(), "").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/no-es-un-uuid/pagos", conClaveDePlataforma(), c).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(contar("pago")).isZero();
    }

    // --- la bitácora ------------------------------------------------------------------------------------------------------------------------

    @Test void registrarUnPagoQuedaEnLaBitacoraConLaCuentaYSinLaReferenciaNiLaNota() {
        Cliente c = cliente("ana@negocio.pe");
        emprendeHasta(c, 5);

        Map<String, Object> p = datos(registrar(c, cuerpo(HOY.plusDays(35), "OP-SECRETA-1", true)), HttpStatus.CREATED);

        Map<String, Object> fila = jdbc.queryForMap("SELECT actor_tipo, cuenta_id, detalle FROM auditoria_admin WHERE accion = 'REGISTRAR_PAGO'");
        assertThat(fila).containsEntry("actor_tipo", "CLAVE_PLATAFORMA").containsEntry("cuenta_id", c.cuentaId());
        assertThat((String) fila.get("detalle")).startsWith("pago=" + p.get("id") + " periodo=" + HOY + "/" + HOY.plusDays(35) + " monto=29.00 medio=YAPE").endsWith("vence=" + HOY.plusDays(36))
                .doesNotContain("OP-SECRETA-1").doesNotContain("Pagó por el banco");
    }

    @Test void sinExtenderLaBitacoraDiceQueElVencimientoNoCambio() {
        Cliente c = cliente("ana@negocio.pe");

        registrar(c, cuerpo(HOY.plusDays(30), "OP-10", false));

        assertThat((String) jdbc.queryForObject("SELECT detalle FROM auditoria_admin WHERE accion = 'REGISTRAR_PAGO'", String.class)).endsWith("vence=sin_cambio");
    }

    @Test void unAdministradorRealQuedaComoAutorDelPago() {
        http.postForEntity("/v1/admin/administradores", new HttpEntity<>("{\"email\":\"admin@khipu.pe\",\"password\":\"Segura123\"}", conClaveDePlataforma()), Map.class);
        String sesion = (String) SesionAdminDePrueba.entrar(http, "admin@khipu.pe", "Segura123").get("access_token");
        UUID adminId = jdbc.queryForObject("SELECT id FROM administrador WHERE email = 'admin@khipu.pe'", UUID.class);
        Cliente c = cliente("ana@negocio.pe");

        ResponseEntity<Map> r = llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/pagos", conBearer(sesion), cuerpo(HOY.plusDays(30), "OP-11", false));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> fila = jdbc.queryForMap("SELECT actor_tipo, administrador_id FROM auditoria_admin WHERE accion = 'REGISTRAR_PAGO'");
        assertThat(fila).containsEntry("actor_tipo", "ADMINISTRADOR").containsEntry("administrador_id", adminId);
    }

    // --- el historial -----------------------------------------------------------------------------------------------------------------------

    @Test void elHistorialVaDelMasRecienteAlMasAntiguoConSuTotalYSusPaginas() {
        Cliente c = cliente("ana@negocio.pe");
        for (int i = 1; i <= 5; i++) registrar(c, cuerpo(HOY, HOY.plusDays(29), String.valueOf(i * 10), "EFECTIVO", HOY.minusDays(10 - i), null, false));

        ResponseEntity<Map> p1 = historial(c.cuentaId(), "?por_pagina=2&pagina=1");
        ResponseEntity<Map> p3 = historial(c.cuentaId(), "?por_pagina=2&pagina=3");

        List<Map<String, Object>> primera = (List<Map<String, Object>>) p1.getBody().get("datos");
        assertThat(primera).extracting(x -> x.get("fecha_de_pago")).containsExactly(HOY.minusDays(5).toString(), HOY.minusDays(6).toString());
        assertThat(((Number) primera.get(0).get("monto")).intValue()).isEqualTo(50);
        assertThat(p1.getHeaders().getFirst("X-Total-Count")).isEqualTo("5");
        assertThat((List<?>) p3.getBody().get("datos")).hasSize(1);
    }

    @Test void elHistorialDeUnaCuentaNoMuestraLosPagosDeOtraYUnaCuentaSinPagosEstaVacia() {
        Cliente a = cliente("ana@negocio.pe");
        Cliente b = cliente("beto@negocio.pe");
        registrar(a, cuerpo(HOY.plusDays(30), "A-1", false));

        assertThat((List<?>) historial(a.cuentaId(), "").getBody().get("datos")).hasSize(1);
        ResponseEntity<Map> deB = historial(b.cuentaId(), "");
        assertThat((List<?>) deB.getBody().get("datos")).isEmpty();
        assertThat(deB.getHeaders().getFirst("X-Total-Count")).isEqualTo("0");
    }

    /** Un cambio de plan cierra la suscripción, pero los pagos que se anotaron con ella siguen en el historial de la cuenta. */
    @Test void losPagosSobrevivenAUnCambioDePlanDeLaCuenta() {
        Cliente c = cliente("ana@negocio.pe");
        emprendeHasta(c, 5);
        registrar(c, cuerpo(HOY.plusDays(35), "OP-12", false));
        UUID negocio = jdbc.queryForObject("SELECT id FROM plan WHERE nombre = 'Negocio'", UUID.class);

        ResponseEntity<Map> cambio = llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/plan", conClaveDePlataforma(),
                "{\"plan_id\":\"%s\",\"vence_en\":\"%s\",\"dias_de_gracia\":0}".formatted(negocio, Instant.now().plus(Duration.ofDays(40))));

        assertThat(cambio.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) historial(c.cuentaId(), "").getBody().get("datos")).hasSize(1);
        assertThat(contar("pago")).isEqualTo(1);
    }

    @Test void consultarElHistorialNoEscribeNada() {
        Cliente c = cliente("ana@negocio.pe");
        registrar(c, cuerpo(HOY.plusDays(30), "OP-13", false));
        long pagos = contar("pago");
        long bitacora = contar("auditoria_admin");

        historial(c.cuentaId(), "");

        assertThat(contar("pago")).isEqualTo(pagos);
        assertThat(contar("auditoria_admin")).isEqualTo(bitacora);
    }

    // --- quién puede ------------------------------------------------------------------------------------------------------------------------

    /** Los pagos de los clientes son del administrador: ni el dueño, ni una API key, ni una clave errónea pueden verlos ni anotarlos. */
    @Test void nadieMasPuedeVerNiAnotarPagos() {
        Cliente c = cliente("ana@negocio.pe");
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");
        String cuerpo = cuerpo(HOY.plusDays(30), "OP-14", false);

        for (HttpHeaders h : List.of(json(), conBearer(c.access()), conApiKey(c.apiKey()), conClaveErronea)) {
            assertThat(llamar(HttpMethod.POST, "/v1/admin/cuentas/" + c.cuentaId() + "/pagos", h, cuerpo).getStatusCode()).as("anotar con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.GET, "/v1/admin/cuentas/" + c.cuentaId() + "/pagos", h, null).getStatusCode()).as("ver con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(contar("pago")).isZero();
    }
}
