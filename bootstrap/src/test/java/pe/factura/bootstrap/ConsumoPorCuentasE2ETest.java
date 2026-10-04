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

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El consumo de todas las cuentas contra su plan (#193) de extremo a extremo: HTTP real, filtros reales, Postgres real y los planes sembrados (Gratis 30 documentos,
 * Emprende 300, Pro sin tope). Lo que importa: que el porcentaje y la alerta coincidan con lo que se mide, que «plan vencido» siga la regla del dominio, que las bajas
 * no salgan, que el total y la exportación respeten el filtro y que el texto de un cliente no ejecute una fórmula en la hoja de cálculo.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ConsumoPorCuentasE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    record Cliente(String access, UUID cuentaId, UUID empresa, String apiKey) {}

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    private int sufijo = 0;

    /** Un RUC distinto por cada {@code n}, con su dígito verificador. */
    private static String rucValido(int n) {
        String base = "2010%06d".formatted(n);
        for (int d = 0; d < 10; d++) if (pe.factura.domain.tenant.Ruc.esValido(base + d)) return base + d;
        throw new IllegalStateException("sin dígito verificador para " + base);
    }

    /** Una cuenta con una empresa; la cuenta nace con el plan por defecto (Gratis). */
    private Cliente cliente(String nombre, String email) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"%s\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(nombre, email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        String ruc = rucValido(++sufijo);
        assertThat(llamar(HttpMethod.POST, "/v1/empresas", conBearer(access), "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s\",\"entorno\":\"BETA\"}".formatted(ruc, ruc)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
        HttpHeaders h = conBearer(access);
        h.set("X-Empresa", empresa.toString());
        String key = (String) ((Map<?, ?>) llamar(HttpMethod.POST, "/v1/empresa/api-keys", h, null).getBody().get("datos")).get("api_key");
        return new Cliente(access, jdbc.queryForObject("SELECT id FROM cuenta WHERE email = ?", UUID.class, email), empresa, key);
    }

    private void plan(Cliente c, String nombre) {
        jdbc.update("UPDATE suscripcion SET plan_id = (SELECT id FROM plan WHERE nombre = ?) WHERE cuenta_id = ? AND termina_en IS NULL", nombre, c.cuentaId());
    }

    private void vence(Cliente c, Instant vence, int gracia) {
        jdbc.update("UPDATE suscripcion SET inicia_en = LEAST(inicia_en, ?), vence_en = ?, dias_de_gracia = ? WHERE cuenta_id = ? AND termina_en IS NULL",
                Timestamp.from(vence.minus(Duration.ofDays(30))), Timestamp.from(vence), gracia, c.cuentaId());
    }

    /** {@code n} comprobantes del mes en el estado dado, de golpe. */
    private void documentos(Cliente c, int n, String estado) {
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo)
                SELECT gen_random_uuid(), ?, '01', 'F001', (SELECT coalesce(max(numero), 0) FROM documento WHERE tenant_id = ?) + g, ?, ?, 'archivo' FROM generate_series(1, ?) g""",
                c.empresa(), c.empresa(), java.sql.Date.valueOf("2026-10-15"), estado, n);
    }

    private ResponseEntity<Map> lista(String query) { return llamar(HttpMethod.GET, "/v1/admin/consumo?mes=2026-10" + query, conClaveDePlataforma(), null); }

    private Map<String, Object> datos(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("datos");
    }

    private List<Map<String, Object>> cuentas(ResponseEntity<Map> r) { return (List<Map<String, Object>>) datos(r).get("cuentas"); }

    private List<Object> nombres(ResponseEntity<Map> r) { return cuentas(r).stream().map(x -> x.get("nombre")).toList(); }

    private String csv(String query) {
        ResponseEntity<byte[]> r = http.exchange("/v1/admin/consumo/exportacion?mes=2026-10" + query, HttpMethod.GET, new HttpEntity<>(null, conClaveDePlataforma()), byte[].class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new String(r.getBody(), StandardCharsets.UTF_8);
    }

    // --- la tabla ---------------------------------------------------------------------------------------------------------------------------

    @Test void cadaCuentaDiceSuConsumoSuTopeSuPorcentajeYSiEstaEnAlerta() {
        Cliente ana = cliente("Ana", "ana@negocio.pe");
        plan(ana, "Emprende");
        documentos(ana, 240, "ACEPTADO");
        documentos(ana, 50, "RECHAZADO");

        Map<String, Object> f = cuentas(lista("")).get(0);

        assertThat(f).containsEntry("cuenta_id", ana.cuentaId().toString()).containsEntry("nombre", "Ana").containsEntry("email", "ana@negocio.pe").containsEntry("plan", "Emprende")
                .containsEntry("documentos", 240).containsEntry("limite", 300).containsEntry("porcentaje", 80).containsEntry("en_alerta", true).containsEntry("estado_del_plan", "VIGENTE");
        assertThat(datos(lista(""))).containsEntry("mes", "2026-10").containsEntry("umbral_de_alerta", 80);
    }

    @Test void justoPorDebajoDelUmbralNoEstaEnAlertaYLosPlanesSinTopeNuncaLoEstan() {
        Cliente justo = cliente("Justo", "justo@negocio.pe");
        plan(justo, "Emprende");
        documentos(justo, 239, "ACEPTADO");
        Cliente pro = cliente("Pro", "pro@negocio.pe");
        plan(pro, "Pro");
        documentos(pro, 5000, "ACEPTADO");

        List<Map<String, Object>> filas = cuentas(lista(""));

        assertThat(filas.stream().filter(x -> x.get("nombre").equals("Justo")).findFirst().get()).containsEntry("porcentaje", 79).containsEntry("en_alerta", false);
        Map<String, Object> ilimitada = filas.stream().filter(x -> x.get("nombre").equals("Pro")).findFirst().get();
        assertThat(ilimitada).containsEntry("documentos", 5000).containsEntry("en_alerta", false).doesNotContainKeys("limite", "porcentaje");
    }

    @Test void seOrdenaPorPorcentajeConLosPlanesSinTopeAlFinalOPorDocumentos() {
        Cliente a = cliente("A-poca", "a@negocio.pe");
        documentos(a, 3, "ACEPTADO");
        Cliente b = cliente("B-casi", "b@negocio.pe");
        documentos(b, 29, "ACEPTADO");
        Cliente c = cliente("C-ilimitada", "c@negocio.pe");
        plan(c, "Pro");
        documentos(c, 900, "ACEPTADO");
        Cliente d = cliente("D-mucho", "d@negocio.pe");
        plan(d, "Negocio");
        documentos(d, 600, "ACEPTADO");

        assertThat(nombres(lista(""))).containsExactly("B-casi", "D-mucho", "A-poca", "C-ilimitada");
        assertThat(nombres(lista("&orden=DOCUMENTOS"))).containsExactly("C-ilimitada", "D-mucho", "B-casi", "A-poca");
    }

    @Test void elFiltroCercaDelLimiteSoloTraeLasQueYaLlegaronAlUmbralYElTotalLoRefleja() {
        Cliente alerta = cliente("Alerta", "alerta@negocio.pe");
        documentos(alerta, 24, "ACEPTADO");
        Cliente sobrepasada = cliente("Sobrepasada", "sobre@negocio.pe");
        documentos(sobrepasada, 45, "ACEPTADO");
        Cliente tranquila = cliente("Tranquila", "tranquila@negocio.pe");
        documentos(tranquila, 10, "ACEPTADO");

        ResponseEntity<Map> r = lista("&filtro=CERCA_DEL_LIMITE");

        assertThat(nombres(r)).containsExactly("Sobrepasada", "Alerta");
        assertThat(cuentas(r).get(0)).containsEntry("porcentaje", 150);
        assertThat(r.getHeaders().getFirst("X-Total-Count")).isEqualTo("2");
        assertThat(lista("").getHeaders().getFirst("X-Total-Count")).isEqualTo("3");
    }

    // --- el plan vencido --------------------------------------------------------------------------------------------------------------------

    @Test void elEstadoDeCobroSigueLaReglaDelDominioYElFiltroTraeLasEnGraciaYLasVencidas() {
        Instant ahora = Instant.now();
        Cliente alDia = cliente("AlDia", "aldia@negocio.pe");
        vence(alDia, ahora.plus(Duration.ofDays(10)), 3);
        Cliente enGracia = cliente("EnGracia", "gracia@negocio.pe");
        vence(enGracia, ahora.minus(Duration.ofDays(1)), 3);
        Cliente vencida = cliente("Vencida", "vencida@negocio.pe");
        vence(vencida, ahora.minus(Duration.ofDays(5)), 3);
        Cliente sinFecha = cliente("SinFecha", "sinfecha@negocio.pe");

        List<Map<String, Object>> todas = cuentas(lista("&orden=DOCUMENTOS"));
        Map<Object, Object> estados = new java.util.HashMap<>();
        todas.forEach(x -> estados.put(x.get("nombre"), x.get("estado_del_plan")));
        assertThat(estados).containsEntry("AlDia", "VIGENTE").containsEntry("EnGracia", "EN_GRACIA").containsEntry("Vencida", "VENCIDA").containsEntry("SinFecha", "VIGENTE");
        assertThat(todas.stream().filter(x -> x.get("nombre").equals("SinFecha")).findFirst().get()).doesNotContainKeys("pagado_hasta", "se_sirve_hasta");

        ResponseEntity<Map> vencidos = lista("&filtro=PLAN_VENCIDO");

        assertThat(nombres(vencidos)).containsExactlyInAnyOrder("EnGracia", "Vencida");
        assertThat(vencidos.getHeaders().getFirst("X-Total-Count")).isEqualTo("2");
        Map<String, Object> g = cuentas(vencidos).stream().filter(x -> x.get("nombre").equals("EnGracia")).findFirst().get();
        assertThat(Instant.parse((String) g.get("se_sirve_hasta"))).isEqualTo(Instant.parse((String) g.get("pagado_hasta")).plus(Duration.ofDays(3)));
    }

    // --- quién sale -------------------------------------------------------------------------------------------------------------------------

    @Test void lasCuentasDadasDeBajaNoSalenPeroLasSuspendidasSi() {
        Cliente activa = cliente("Activa", "activa@negocio.pe");
        Cliente baja = cliente("Baja", "baja@negocio.pe");
        Cliente suspendida = cliente("Suspendida", "suspendida@negocio.pe");
        jdbc.update("UPDATE cuenta SET baja_en = now() WHERE id = ?", baja.cuentaId());
        jdbc.update("UPDATE cuenta SET suspendida_en = now() WHERE id = ?", suspendida.cuentaId());

        ResponseEntity<Map> r = lista("&orden=DOCUMENTOS");

        assertThat(nombres(r)).containsExactlyInAnyOrder("Activa", "Suspendida");
        assertThat(r.getHeaders().getFirst("X-Total-Count")).isEqualTo("2");
        assertThat(csv("")).doesNotContain("baja@negocio.pe");
    }

    @Test void soloCuentanLosAceptadosDelMesPedido() {
        Cliente ana = cliente("Ana", "ana@negocio.pe");
        documentos(ana, 4, "ACEPTADO");
        documentos(ana, 6, "ERROR_ENVIO");
        jdbc.update("UPDATE documento SET fecha_emision = '2026-09-30' WHERE tenant_id = ? AND numero = 1", ana.empresa());

        assertThat(cuentas(lista("")).get(0)).containsEntry("documentos", 3);
        assertThat(cuentas(llamar(HttpMethod.GET, "/v1/admin/consumo?mes=2026-09", conClaveDePlataforma(), null)).get(0)).containsEntry("documentos", 1);
    }

    // --- páginas ----------------------------------------------------------------------------------------------------------------------------

    @Test void laPaginacionNoRepiteNiPierdeCuentasYElTotalEsElDeTodas() {
        for (int i = 1; i <= 5; i++) documentos(cliente("Cuenta" + i, "c" + i + "@negocio.pe"), i, "ACEPTADO");

        ResponseEntity<Map> p1 = lista("&por_pagina=2&pagina=1&orden=DOCUMENTOS");
        ResponseEntity<Map> p2 = lista("&por_pagina=2&pagina=2&orden=DOCUMENTOS");
        ResponseEntity<Map> p3 = lista("&por_pagina=2&pagina=3&orden=DOCUMENTOS");

        assertThat(nombres(p1)).containsExactly("Cuenta5", "Cuenta4");
        assertThat(nombres(p2)).containsExactly("Cuenta3", "Cuenta2");
        assertThat(nombres(p3)).containsExactly("Cuenta1");
        assertThat(p1.getHeaders().getFirst("X-Total-Count")).isEqualTo("5");
    }

    // --- la exportación ---------------------------------------------------------------------------------------------------------------------

    @Test void laExportacionTraeTodasLasCuentasSinPaginarYSeDescargaComoCsv() {
        for (int i = 1; i <= 3; i++) documentos(cliente("Cuenta" + i, "c" + i + "@negocio.pe"), i * 5, "ACEPTADO");

        ResponseEntity<String> r = http.exchange("/v1/admin/consumo/exportacion?mes=2026-10&orden=DOCUMENTOS&por_pagina=1", HttpMethod.GET, new HttpEntity<>(null, conClaveDePlataforma()), String.class);

        assertThat(r.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=\"consumo-2026-10.csv\"");
        String[] lineas = r.getBody().substring(1).split("\r\n");
        assertThat(lineas).hasSize(4);
        assertThat(lineas[0]).startsWith("cuenta_id,cuenta,correo,plan,documentos,limite,porcentaje,en_alerta,estado_del_plan");
        assertThat(lineas[1]).contains(",Cuenta3,c3@negocio.pe,Gratis,15,30,50,no,VIGENTE,");
        assertThat(lineas[3]).contains(",Cuenta1,c1@negocio.pe,Gratis,5,30,16,no,VIGENTE,");
    }

    @Test void laExportacionRespetaElFiltro() {
        documentos(cliente("Alerta", "alerta@negocio.pe"), 25, "ACEPTADO");
        documentos(cliente("Tranquila", "tranquila@negocio.pe"), 2, "ACEPTADO");

        String s = csv("&filtro=CERCA_DEL_LIMITE");

        assertThat(s).contains("alerta@negocio.pe").doesNotContain("tranquila@negocio.pe");
    }

    /** Un cliente elige su nombre: si empieza por «=» una hoja de cálculo lo ejecutaría como fórmula al abrir el archivo del administrador. */
    @Test void elNombreDeUnClienteNoSeEjecutaComoFormulaEnLaExportacion() {
        Cliente c = cliente("Ana", "formula@negocio.pe");
        jdbc.update("UPDATE cuenta SET nombre = ? WHERE id = ?", "=HYPERLINK(\"http://malo.example\",\"clic\")", c.cuentaId());

        String s = csv("");

        assertThat(s).contains("\"'=HYPERLINK(\"\"http://malo.example\"\",\"\"clic\"\")\"").doesNotContain(",=HYPERLINK");
    }

    // --- quién puede verlo ------------------------------------------------------------------------------------------------------------------

    @Test void consultarNoEscribeNada() {
        documentos(cliente("Ana", "ana@negocio.pe"), 3, "ACEPTADO");
        long documentos = jdbc.queryForObject("SELECT count(*) FROM documento", Long.class);

        lista("");
        csv("");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM documento", Long.class)).isEqualTo(documentos);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin", Long.class)).isZero();
    }

    @Test void parametrosInvalidosSon400() {
        for (String malo : List.of("&filtro=TODOS", "&orden=NOMBRE", "&mes=2026-13")) {
            assertThat(lista(malo).getStatusCode()).as(malo).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(http.exchange("/v1/admin/consumo/exportacion?mes=2026-10" + malo, HttpMethod.GET, new HttpEntity<>(null, conClaveDePlataforma()), Map.class).getStatusCode()).as(malo).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    /** El consumo de todos los clientes es del administrador: ni el dueño, ni una API key, ni una clave errónea pueden verlo, ni bajarlo. */
    @Test void nadieMasPuedeVerloNiExportarlo() {
        Cliente c = cliente("Ana", "ana@negocio.pe");
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        for (HttpHeaders h : List.of(json(), conBearer(c.access()), conApiKey(c.apiKey()), conClaveErronea)) {
            assertThat(llamar(HttpMethod.GET, "/v1/admin/consumo", h, null).getStatusCode()).as("lista con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.GET, "/v1/admin/consumo/exportacion", h, null).getStatusCode()).as("exportación con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
