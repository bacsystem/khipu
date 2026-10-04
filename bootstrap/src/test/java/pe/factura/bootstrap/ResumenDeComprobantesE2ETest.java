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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El resumen de comprobantes de la empresa (#15) de extremo a extremo: HTTP real, filtros reales, Postgres real. Lo que importa: que cada empresa vea **solo lo suyo**
 * (con API key o con el JWT del portal), que lo emitido, lo aceptado, la atención requerida y lo facturado salgan de las reglas del dominio, y que nadie sin credenciales lo vea.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ResumenDeComprobantesE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    static final LocalDate DIA = LocalDate.of(2026, 9, 15);
    static final String[] RUCS = {"20100066603", "20100066611", "20100066620"};
    static final AtomicLong NUMERO = new AtomicLong(1);

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    record Cliente(String access, UUID empresa, String apiKey) {}

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = json(); h.set("X-Api-Key", key); return h; }

    private HttpHeaders conBearer(Cliente c) { HttpHeaders h = json(); h.setBearerAuth(c.access()); h.set("X-Empresa", c.empresa().toString()); return h; }

    private ResponseEntity<Map> llamar(HttpMethod m, String uri, HttpHeaders h, String cuerpo) { return http.exchange(uri, m, new HttpEntity<>(cuerpo, h), Map.class); }

    private int siguiente = 0;

    private Cliente cliente(String email) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        String ruc = RUCS[siguiente++];
        HttpHeaders bearer = json(); bearer.setBearerAuth(access);
        assertThat(llamar(HttpMethod.POST, "/v1/empresas", bearer, "{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s\",\"entorno\":\"BETA\"}".formatted(ruc, ruc)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
        HttpHeaders h = conBearer(new Cliente(access, empresa, null));
        String key = (String) ((Map<?, ?>) llamar(HttpMethod.POST, "/v1/empresa/api-keys", h, null).getBody().get("datos")).get("api_key");
        return new Cliente(access, empresa, key);
    }

    private void comprobante(Cliente c, String tipo, String estado, LocalDate fecha, String moneda, String total) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, ?, 'F001', ?, ?, ?, 'x')",
                id, c.empresa(), tipo, NUMERO.getAndIncrement(), java.sql.Date.valueOf(fecha), estado);
        jdbc.update("INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, total_gravado, total_exonerado, total_inafecto, total_igv, total) "
                + "VALUES (?, '0101', ?, '6', '20601234565', 'CLIENTE', 0, 0, 0, 0, ?)", id, moneda, new BigDecimal(total));
    }

    private void factura(Cliente c, String estado, String total) { comprobante(c, "01", estado, DIA, "PEN", total); }

    private ResponseEntity<Map> resumen(Cliente c, String query) { return llamar(HttpMethod.GET, "/v1/facturas/resumen" + query, conApiKey(c.apiKey()), null); }

    private Map<String, Object> datos(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("datos");
    }

    private double numero(Object n) { return ((Number) n).doubleValue(); }

    // --- el resumen -------------------------------------------------------------------------------------------------------------------------

    @Test void cuentaLoEmitidoLoAceptadoYLaAtencionConSuDesgloseSegunElDominio() {
        Cliente c = cliente("ana@negocio.pe");
        for (int i = 0; i < 6; i++) factura(c, "ACEPTADO", "100");
        for (int i = 0; i < 2; i++) factura(c, "ACEPTADO_CON_OBS", "100");
        for (int i = 0; i < 3; i++) factura(c, "RECHAZADO", "100");
        for (int i = 0; i < 2; i++) factura(c, "ERROR_ENVIO", "100");
        factura(c, "FUERA_DE_PLAZO", "100");
        factura(c, "ENVIADO", "100");
        factura(c, "ANULADO", "100");

        Map<String, Object> d = datos(resumen(c, ""));

        assertThat(d).containsEntry("emitidos", 16).containsEntry("aceptados_con_cdr", 8);
        assertThat((Map<String, Object>) d.get("atencion_requerida")).containsEntry("total", 6).containsEntry("rechazados", 3).containsEntry("errores_de_envio", 2).containsEntry("fuera_de_plazo", 1);
    }

    @Test void lasFacturasQueNuncaSeFirmaronNoCuentanComoEmitidas() {
        Cliente c = cliente("ana@negocio.pe");
        factura(c, "RECIBIDO", "100");
        factura(c, "INVALIDO", "100");
        factura(c, "ACEPTADO", "100");

        assertThat(datos(resumen(c, ""))).containsEntry("emitidos", 1);
    }

    @Test void lasNotasDeCreditoRestanLasDeDebitoSumanYCadaMonedaVaAparte() {
        Cliente c = cliente("ana@negocio.pe");
        comprobante(c, "01", "ACEPTADO", DIA, "PEN", "1000");
        comprobante(c, "03", "ACEPTADO", DIA, "PEN", "200");
        comprobante(c, "07", "ACEPTADO", DIA, "PEN", "300");
        comprobante(c, "08", "ACEPTADO", DIA, "PEN", "50");
        comprobante(c, "01", "ACEPTADO", DIA, "USD", "500.50");
        comprobante(c, "01", "RECHAZADO", DIA, "PEN", "99999");

        List<Map<String, Object>> facturado = (List<Map<String, Object>>) datos(resumen(c, "")).get("facturado");

        assertThat(facturado).extracting(f -> f.get("moneda")).containsExactly("PEN", "USD");
        assertThat(numero(facturado.get(0).get("total"))).isEqualTo(950.0);
        assertThat(numero(facturado.get(1).get("total"))).isEqualTo(500.5);
    }

    @Test void lasEmpresasNoSeVenEntreSi() {
        Cliente a = cliente("ana@negocio.pe");
        Cliente b = cliente("beto@otro.pe");
        factura(a, "ACEPTADO", "100");
        for (int i = 0; i < 4; i++) factura(b, "RECHAZADO", "999");

        Map<String, Object> deA = datos(resumen(a, ""));
        Map<String, Object> deB = datos(resumen(b, ""));

        assertThat(deA).containsEntry("emitidos", 1).containsEntry("aceptados_con_cdr", 1);
        assertThat((Map<String, Object>) deA.get("atencion_requerida")).containsEntry("total", 0);
        assertThat(deB).containsEntry("emitidos", 4).containsEntry("aceptados_con_cdr", 0);
        assertThat(numero(((Map<String, Object>) deB.get("atencion_requerida")).get("total"))).isEqualTo(4);
        assertThat((List<?>) deB.get("facturado")).isEmpty();
    }

    @Test void unaEmpresaSinComprobantesTieneTodoEnCero() {
        Cliente c = cliente("ana@negocio.pe");

        Map<String, Object> d = datos(resumen(c, ""));

        assertThat(d).containsEntry("emitidos", 0).containsEntry("aceptados_con_cdr", 0);
        assertThat((Map<String, Object>) d.get("atencion_requerida")).containsEntry("total", 0).containsEntry("rechazados", 0).containsEntry("errores_de_envio", 0).containsEntry("fuera_de_plazo", 0);
        assertThat((List<?>) d.get("facturado")).isEmpty();
        assertThat(d).doesNotContainKeys("desde", "hasta");
    }

    // --- el rango ---------------------------------------------------------------------------------------------------------------------------

    @Test void elRangoEsInclusivoYSeDevuelveTalCualSeLoPidio() {
        Cliente c = cliente("ana@negocio.pe");
        for (int dia : new int[]{9, 10, 15, 20, 21}) comprobante(c, "01", "ACEPTADO", LocalDate.of(2026, 9, dia), "PEN", String.valueOf(dia));

        Map<String, Object> d = datos(resumen(c, "?desde=2026-09-10&hasta=2026-09-20"));

        assertThat(d).containsEntry("emitidos", 3).containsEntry("desde", "2026-09-10").containsEntry("hasta", "2026-09-20");
        assertThat(numero(((List<Map<String, Object>>) d.get("facturado")).get(0).get("total"))).isEqualTo(45.0);
    }

    @Test void unLadoAbiertoNoAcotaEseExtremo() {
        Cliente c = cliente("ana@negocio.pe");
        for (int dia : new int[]{9, 15, 21}) comprobante(c, "01", "ACEPTADO", LocalDate.of(2026, 9, dia), "PEN", "1");

        assertThat(datos(resumen(c, "?desde=2026-09-15"))).containsEntry("emitidos", 2).doesNotContainKey("hasta");
        assertThat(datos(resumen(c, "?hasta=2026-09-15"))).containsEntry("emitidos", 2).doesNotContainKey("desde");
    }

    @Test void unRangoAlRevesEs400ConSuCodigoYUnaFechaMalEscritaTambien() {
        Cliente c = cliente("ana@negocio.pe");

        ResponseEntity<Map> alReves = resumen(c, "?desde=2026-09-30&hasta=2026-09-01");
        assertThat(alReves.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(alReves.getBody().get("codigo")).isEqualTo("RANGO_INVALIDO");
        for (String mala : List.of("?desde=01/09/2026", "?hasta=2026-02-30", "?desde=ayer")) assertThat(resumen(c, mala).getStatusCode()).as(mala).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- quién puede verlo ------------------------------------------------------------------------------------------------------------------

    /** La API key y el JWT del portal con `X-Empresa` resuelven a la misma empresa, así que dicen lo mismo. */
    @Test void conElJwtDelPortalSeVeLoMismoQueConLaApiKey() {
        Cliente c = cliente("ana@negocio.pe");
        factura(c, "ACEPTADO", "100");
        factura(c, "RECHAZADO", "100");

        Map<String, Object> conJwt = datos(llamar(HttpMethod.GET, "/v1/facturas/resumen", conBearer(c), null));

        assertThat(conJwt).isEqualTo(datos(resumen(c, "")));
        assertThat(conJwt).containsEntry("emitidos", 2);
    }

    @Test void sinCredencialesOConUnaClaveQueNoEsDeLaEmpresaNoSeVe() {
        Cliente c = cliente("ana@negocio.pe");
        HttpHeaders conClaveDePlataforma = json(); conClaveDePlataforma.set("X-Platform-Key", "plataforma-test");
        HttpHeaders conClaveErronea = conApiKey("khp_clave-que-no-existe");

        for (HttpHeaders h : List.of(json(), conClaveDePlataforma, conClaveErronea)) {
            assertThat(llamar(HttpMethod.GET, "/v1/facturas/resumen", h, null).getStatusCode()).as("con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(c.apiKey()).isNotBlank();
    }

    @Test void consultarElResumenNoEscribeNada() {
        Cliente c = cliente("ana@negocio.pe");
        factura(c, "ACEPTADO", "100");
        long documentos = jdbc.queryForObject("SELECT count(*) FROM documento", Long.class);

        resumen(c, "");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM documento", Long.class)).isEqualTo(documentos);
    }
}
