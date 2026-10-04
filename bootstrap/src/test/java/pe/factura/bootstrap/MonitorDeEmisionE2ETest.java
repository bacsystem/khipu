package pe.factura.bootstrap;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * El monitor global de emisión (#195) de extremo a extremo: HTTP real, filtros reales, Postgres real y servicios de SUNAT falsos en un puerto local (los tests no salen a
 * internet). Lo que importa: que sume **todas** las empresas, que cada comprobante caiga en la categoría que dice el dominio, que la alerta del outbox salga solo con
 * envíos vencidos hace más de 5 minutos, que el estado de SUNAT refleje lo que contestan los servicios y que solo la plataforma lo vea.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class MonitorDeEmisionE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Dos servicios de SUNAT falsos: {@code /arriba} contesta 200 y {@code /caido} contesta 503. Los otros dos van a un puerto cerrado. */
    static final HttpServer SUNAT = sunatFalso();

    static HttpServer sunatFalso() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            s.createContext("/arriba", x -> { x.sendResponseHeaders(200, -1); x.close(); });
            s.createContext("/caido", x -> { x.sendResponseHeaders(503, -1); x.close(); });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource static void sunat(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + SUNAT.getAddress().getPort();
        r.add("app.sunat.beta-url", () -> base + "/arriba/billService");
        r.add("app.sunat.prod-url", () -> base + "/caido/billService");
        r.add("app.sunat.consulta-url", () -> "http://127.0.0.1:1/billConsultService");
        r.add("app.sunat.validez-url", () -> "http://127.0.0.1:1/billValidService");
        r.add("app.sunat.timeout-seconds", () -> "2");
    }

    @AfterAll static void cerrar() { SUNAT.stop(0); }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    static final String[] RUCS = {"20100066603", "20100066611"};
    static final AtomicLong NUMERO = new AtomicLong(1);

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    record Cliente(String access, UUID empresa, String apiKey) {}

    private int siguiente = 0;

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conClaveDePlataforma() { HttpHeaders h = json(); h.set("X-Platform-Key", "plataforma-test"); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private HttpHeaders conBearer(String token) { HttpHeaders h = json(); h.setBearerAuth(token); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    private Cliente cliente(String email) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        String ruc = RUCS[siguiente++];
        assertThat(llamar(HttpMethod.POST, "/v1/empresas", conBearer(access), "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s\",\"entorno\":\"BETA\"}".formatted(ruc, ruc)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
        HttpHeaders h = conBearer(access);
        h.set("X-Empresa", empresa.toString());
        String key = (String) ((Map<?, ?>) llamar(HttpMethod.POST, "/v1/empresa/api-keys", h, null).getBody().get("datos")).get("api_key");
        return new Cliente(access, empresa, key);
    }

    /** Un comprobante creado hace {@code minutos} minutos. */
    private void documento(Cliente c, String estado, long minutos) {
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, created_at) VALUES (?, ?, '01', 'F001', ?, current_date, ?, 'x', ?)",
                UUID.randomUUID(), c.empresa(), NUMERO.getAndIncrement(), estado, Timestamp.from(Instant.now().minus(Duration.ofMinutes(minutos))));
    }

    /** Un envío pendiente que le toca reintentarse {@code minutos} minutos atrás (negativo: dentro de tantos minutos). */
    private void envio(Cliente c, long minutos) {
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, ?, 'ENVIAR', ?)",
                c.empresa(), UUID.randomUUID(), Timestamp.from(Instant.now().minus(Duration.ofMinutes(minutos))));
    }

    private Map<String, Object> monitor() {
        ResponseEntity<Map> r = llamar(HttpMethod.GET, "/v1/admin/monitor", conClaveDePlataforma(), null);
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("datos");
    }

    private long suma(Map<String, Object> d, String campo) {
        return ((List<Map<String, Object>>) d.get("horas")).stream().mapToLong(f -> ((Number) f.get(campo)).longValue()).sum();
    }

    // --- los comprobantes -------------------------------------------------------------------------------------------------------------------

    @Test void sumaLosComprobantesDeTodasLasEmpresasPorLoQueLesPaso() {
        Cliente a = cliente("ana@negocio.pe");
        Cliente b = cliente("beto@negocio.pe");
        for (int i = 0; i < 5; i++) documento(a, "ACEPTADO", 10);
        documento(b, "ACEPTADO_CON_OBS", 10);
        documento(b, "ACEPTADO", 10);
        documento(a, "RECHAZADO", 10);
        documento(b, "ERROR_ENVIO", 10);
        documento(b, "FUERA_DE_PLAZO", 10);
        documento(a, "ENVIADO", 10);
        documento(b, "FIRMADO", 10);
        documento(a, "ANULADO", 10);

        Map<String, Object> d = monitor();

        assertThat(((List<?>) d.get("horas"))).hasSize(24);
        assertThat(suma(d, "aceptados")).isEqualTo(7);
        assertThat(suma(d, "rechazados")).isEqualTo(1);
        assertThat(suma(d, "con_error")).isEqualTo(2);
        assertThat(suma(d, "en_camino")).isEqualTo(2);
        assertThat(suma(d, "otros")).isEqualTo(1);
        assertThat(suma(d, "total")).isEqualTo(13);
    }

    @Test void loDeHaceMasDe24HorasNoEntraEnLaSerie() {
        Cliente a = cliente("ana@negocio.pe");
        documento(a, "ACEPTADO", 10);
        documento(a, "ACEPTADO", 25 * 60);

        assertThat(suma(monitor(), "total")).isEqualTo(1);
    }

    @Test void lasHorasVanContiguasDeLaMasVieja_aLaActualYLasVaciasEnCero() {
        Cliente a = cliente("ana@negocio.pe");
        documento(a, "ACEPTADO", 10);

        List<Map<String, Object>> horas = (List<Map<String, Object>>) monitor().get("horas");

        for (int i = 1; i < 24; i++)
            assertThat(Instant.parse((String) horas.get(i).get("desde"))).isEqualTo(Instant.parse((String) horas.get(i - 1).get("desde")).plus(Duration.ofHours(1)));
        assertThat(horas.stream().filter(f -> ((Number) f.get("total")).longValue() == 0)).hasSizeGreaterThanOrEqualTo(22);
    }

    @Test void elDiaDeLimaEsLaSumaDeLasHorasDesdeSuMedianoche() {
        Cliente a = cliente("ana@negocio.pe");
        documento(a, "ACEPTADO", 1);
        documento(a, "RECHAZADO", 1);
        documento(a, "ACEPTADO", 30 * 60);

        Map<String, Object> d = monitor();
        Instant medianoche = Instant.parse((String) ((Map<String, Object>) d.get("hoy")).get("desde"));
        long esperado = ((List<Map<String, Object>>) d.get("horas")).stream().filter(f -> !Instant.parse((String) f.get("desde")).isBefore(medianoche))
                .mapToLong(f -> ((Number) f.get("total")).longValue()).sum();

        assertThat(((Number) ((Map<String, Object>) d.get("hoy")).get("total")).longValue()).isEqualTo(esperado);
        assertThat(medianoche.atZone(pe.factura.domain.plan.CicloMensual.ZONA).toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
    }

    @Test void sinComprobantesTodoVaEnCeroYSinTasaDeRechazo() {
        Map<String, Object> d = monitor();

        assertThat(suma(d, "total")).isZero();
        assertThat((Map<String, Object>) d.get("hoy")).containsEntry("total", 0).doesNotContainKey("tasa_de_rechazo");
    }

    @Test void laTasaDeRechazoDelDiaSaleDeLosResueltos() {
        Cliente a = cliente("ana@negocio.pe");
        for (int i = 0; i < 3; i++) documento(a, "ACEPTADO", 1);
        documento(a, "RECHAZADO", 1);
        documento(a, "ENVIADO", 1);

        Map<String, Object> hoy = (Map<String, Object>) monitor().get("hoy");

        // Si la prueba corre justo pasada la medianoche de Lima, lo creado hace un minuto es de ayer y el día va en cero: no hay tasa que comparar.
        assumeTrue(((Number) hoy.get("total")).longValue() > 0);
        assertThat(((Number) hoy.get("tasa_de_rechazo")).doubleValue()).isEqualTo(0.25);
    }

    // --- el outbox --------------------------------------------------------------------------------------------------------------------------

    @Test void unaColaVaciaNoDaAlerta() {
        Map<String, Object> o = (Map<String, Object>) monitor().get("outbox");

        assertThat(o).containsEntry("pendientes", 0).containsEntry("vencidos", 0).containsEntry("alerta", false).doesNotContainKeys("mas_viejo_desde", "vencido_hace_segundos");
    }

    @Test void unEnvioVencidoHaceMasDeCincoMinutosDaAlerta() {
        Cliente a = cliente("ana@negocio.pe");
        envio(a, 12);
        envio(a, -30);

        Map<String, Object> o = (Map<String, Object>) monitor().get("outbox");

        assertThat(o).containsEntry("pendientes", 2).containsEntry("vencidos", 1).containsEntry("alerta", true);
        assertThat(((Number) o.get("vencido_hace_segundos")).longValue()).isBetween(12 * 60L, 12 * 60L + 60);
        assertThat(o).containsKey("mas_viejo_desde");
    }

    @Test void unEnvioVencidoHaceUnMinutoTodaviaNoDaAlerta() {
        Cliente a = cliente("ana@negocio.pe");
        envio(a, 1);

        assertThat((Map<String, Object>) monitor().get("outbox")).containsEntry("vencidos", 1).containsEntry("alerta", false);
    }

    @Test void unEnvioQueEsperaSuReintentoNoEstaVencidoNiDaAlerta() {
        Cliente a = cliente("ana@negocio.pe");
        envio(a, -120);

        assertThat((Map<String, Object>) monitor().get("outbox")).containsEntry("pendientes", 1).containsEntry("vencidos", 0).containsEntry("alerta", false);
    }

    @Test void unEnvioTomadoPorElTrabajoNoEstaVencido() {
        Cliente a = cliente("ana@negocio.pe");
        envio(a, 30);
        jdbc.update("UPDATE outbox SET locked_until = ?", Timestamp.from(Instant.now().plus(Duration.ofMinutes(2))));

        assertThat((Map<String, Object>) monitor().get("outbox")).containsEntry("vencidos", 0).containsEntry("alerta", false);
    }

    // --- SUNAT ------------------------------------------------------------------------------------------------------------------------------

    @Test void elEstadoDeSunatDiceQueServiciosContestan() {
        List<Map<String, Object>> sunat = (List<Map<String, Object>>) monitor().get("sunat");

        assertThat(sunat).extracting(s -> s.get("servicio")).containsExactly("ENVIO_PRODUCCION", "ENVIO_BETA", "CONSULTA_DE_CDR", "CONSULTA_DE_VALIDEZ");
        assertThat(sunat.get(0)).containsEntry("disponible", false).containsEntry("detalle", "HTTP 503").containsKey("milisegundos");
        assertThat(sunat.get(1)).containsEntry("disponible", true).containsKey("milisegundos").doesNotContainKey("detalle");
        assertThat(sunat.get(2)).containsEntry("disponible", false).containsEntry("detalle", "Sin conexión").doesNotContainKey("milisegundos");
        assertThat(sunat.get(3)).containsEntry("disponible", false).containsEntry("detalle", "Sin conexión");
    }

    // --- quién lo ve ------------------------------------------------------------------------------------------------------------------------

    /** El monitor mira a todas las empresas: ni un cliente, ni una API key, ni una clave errónea pueden verlo. */
    @Test void soloLaPlataformaPuedeVerElMonitor() {
        Cliente c = cliente("ana@negocio.pe");
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        for (HttpHeaders h : List.of(json(), conBearer(c.access()), conApiKey(c.apiKey()), conClaveErronea))
            assertThat(llamar(HttpMethod.GET, "/v1/admin/monitor", h, null).getStatusCode()).as("ver con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
