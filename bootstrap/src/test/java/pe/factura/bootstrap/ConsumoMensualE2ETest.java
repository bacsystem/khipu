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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El consumo mensual (#192) de extremo a extremo: HTTP real, filtros reales, Postgres real. Es código de dinero y lo que importa es lo que **no** cuenta:
 * rechazados, errores de envío, fuera de plazo, en camino, el borde de los meses y las empresas ajenas (y que dar de baja no devuelve el documento al cupo). Solo cuentan los comprobantes aceptados por
 * SUNAT, por su fecha de emisión, dentro del mes calendario en Lima.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ConsumoMensualE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
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
        List<UUID> empresas = new java.util.ArrayList<>();
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

    private int numero = 1;

    private void documento(UUID empresa, String tipo, String estado, LocalDate fecha, int intentos) {
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo, intentos)
                VALUES (?, ?, ?, 'F001', ?, ?, ?, 'archivo', ?)""", UUID.randomUUID(), empresa, tipo, numero++, java.sql.Date.valueOf(fecha), estado, intentos);
    }

    private void documento(UUID empresa, String estado, LocalDate fecha) { documento(empresa, "01", estado, fecha, 1); }

    private ResponseEntity<Map> deCuenta(UUID cuenta, String query) { return llamar(HttpMethod.GET, "/v1/admin/cuentas/" + cuenta + "/consumo" + query, conClaveDePlataforma(), null); }

    private ResponseEntity<Map> deEmpresa(UUID empresa, String query) { return llamar(HttpMethod.GET, "/v1/admin/empresas/" + empresa + "/consumo" + query, conClaveDePlataforma(), null); }

    private Map<String, Object> datos(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) r.getBody().get("datos");
    }

    private long contar(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class); }

    static final LocalDate MITAD = LocalDate.of(2026, 10, 15);

    // --- lo que cuenta ----------------------------------------------------------------------------------------------------------------------

    @Test void soloLosComprobantesAceptadosPorSunatCuentan() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        UUID e = c.empresas().get(0);
        documento(e, "ACEPTADO", MITAD);
        documento(e, "ACEPTADO_CON_OBS", MITAD);
        documento(e, "ANULADO", MITAD);
        for (String estado : List.of("RECIBIDO", "INVALIDO", "FIRMADO", "ERROR_ENVIO", "PENDIENTE_AGRUPACION", "ENVIADO", "RECHAZADO", "FUERA_DE_PLAZO"))
            documento(e, estado, MITAD);

        assertThat(datos(deEmpresa(e, "?mes=2026-10"))).containsEntry("documentos", 3);
        assertThat(datos(deCuenta(c.cuentaId(), "?mes=2026-10"))).containsEntry("documentos", 3);
    }

    @Test void losReintentosNoMultiplicanUnComprobanteAceptado() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        documento(c.empresas().get(0), "01", "ACEPTADO", MITAD, 7);

        assertThat(datos(deEmpresa(c.empresas().get(0), "?mes=2026-10"))).containsEntry("documentos", 1);
    }

    @Test void losRechazadosYLosErroresDeEnvioNoCuentanAunqueSeHayanReintentadoMuchasVeces() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        UUID e = c.empresas().get(0);
        documento(e, "01", "RECHAZADO", MITAD, 4);
        documento(e, "01", "ERROR_ENVIO", MITAD, 9);

        assertThat(datos(deEmpresa(e, "?mes=2026-10"))).containsEntry("documentos", 0);
    }

    @Test void losCuatroTiposDeDocumentoCuentan() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        for (String tipo : List.of("01", "03", "07", "08")) documento(c.empresas().get(0), tipo, "ACEPTADO", MITAD, 1);

        assertThat(datos(deEmpresa(c.empresas().get(0), "?mes=2026-10"))).containsEntry("documentos", 4);
    }

    /** Dar de baja no devuelve el documento al cupo: lo que SUNAT aceptó ya consumió. La comunicación de baja no es un documento y no suma otro. */
    @Test void elComprobanteDadoDeBajaSigueConsumiendo() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        UUID e = c.empresas().get(0);
        documento(e, "ACEPTADO", MITAD);
        documento(e, "ANULADO", MITAD);

        assertThat(datos(deEmpresa(e, "?mes=2026-10"))).containsEntry("documentos", 2);
    }

    // --- el mes -----------------------------------------------------------------------------------------------------------------------------

    @Test void elMesEsElCalendarioPorFechaDeEmisionConSusBordes() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        UUID e = c.empresas().get(0);
        for (LocalDate f : List.of(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), LocalDate.of(2025, 10, 15)))
            documento(e, "ACEPTADO", f);

        assertThat(datos(deEmpresa(e, "?mes=2026-10"))).containsEntry("documentos", 2).containsEntry("mes", "2026-10");
        assertThat(datos(deEmpresa(e, "?mes=2026-09"))).containsEntry("documentos", 1);
        assertThat(datos(deEmpresa(e, "?mes=2026-11"))).containsEntry("documentos", 1);
        assertThat(datos(deEmpresa(e, "?mes=2025-10"))).containsEntry("documentos", 1);
        assertThat(datos(deEmpresa(e, "?mes=2026-12"))).containsEntry("documentos", 0);
    }

    @Test void sinMesSeConsultaElMesEnCursoDeLima() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        UUID e = c.empresas().get(0);
        YearMonth hoy = YearMonth.now(CicloMensual.ZONA);
        documento(e, "ACEPTADO", LocalDate.now(CicloMensual.ZONA));
        documento(e, "ACEPTADO", hoy.minusMonths(1).atDay(15));

        Map<String, Object> d = datos(deEmpresa(e, ""));

        assertThat(d).containsEntry("mes", hoy.toString()).containsEntry("documentos", 1);
        assertThat(datos(deCuenta(c.cuentaId(), ""))).containsEntry("mes", hoy.toString()).containsEntry("documentos", 1);
    }

    // --- por cuenta y por empresa -----------------------------------------------------------------------------------------------------------

    @Test void laCuentaSumaSusEmpresasYLasDetallaAunqueUnaNoHayaEmitido() {
        Cliente c = cliente("ana@negocio.pe", "20100066603", "20100066611", "20100066620");
        documento(c.empresas().get(0), "ACEPTADO", MITAD);
        documento(c.empresas().get(0), "ACEPTADO", MITAD);
        documento(c.empresas().get(1), "ACEPTADO_CON_OBS", MITAD);
        documento(c.empresas().get(1), "RECHAZADO", MITAD);

        Map<String, Object> d = datos(deCuenta(c.cuentaId(), "?mes=2026-10"));

        assertThat(d).containsEntry("cuenta_id", c.cuentaId().toString()).containsEntry("documentos", 3);
        List<Map<String, Object>> empresas = (List<Map<String, Object>>) d.get("empresas");
        assertThat(empresas).extracting(x -> x.get("ruc")).containsExactly("20100066603", "20100066611", "20100066620");
        assertThat(empresas).extracting(x -> x.get("documentos")).containsExactly(2, 1, 0);
        assertThat(empresas.get(0)).containsEntry("razon_social", "EMPRESA 20100066603").containsEntry("empresa_id", c.empresas().get(0).toString());
    }

    @Test void unaCuentaNoCuentaLoDeOtra() {
        Cliente a = cliente("ana@negocio.pe", "20100066603");
        Cliente b = cliente("luis@otro.pe", "20100066611");
        documento(a.empresas().get(0), "ACEPTADO", MITAD);
        documento(b.empresas().get(0), "ACEPTADO", MITAD);
        documento(b.empresas().get(0), "ACEPTADO", MITAD);

        assertThat(datos(deCuenta(a.cuentaId(), "?mes=2026-10"))).containsEntry("documentos", 1);
        assertThat(datos(deCuenta(b.cuentaId(), "?mes=2026-10"))).containsEntry("documentos", 2);
        assertThat(datos(deEmpresa(a.empresas().get(0), "?mes=2026-10"))).containsEntry("documentos", 1);
    }

    @Test void unaCuentaSinEmpresasConsumeCeroYNoEsUnError() {
        Cliente c = cliente("ana@negocio.pe");

        Map<String, Object> d = datos(deCuenta(c.cuentaId(), "?mes=2026-10"));

        assertThat(d).containsEntry("documentos", 0);
        assertThat((List<?>) d.get("empresas")).isEmpty();
    }

    // --- rechazos ---------------------------------------------------------------------------------------------------------------------------

    @Test void unMesMalEscritoEs400() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");

        for (String malo : List.of("2026-13", "26-10", "octubre", "2026-10-15", "10-2026")) {
            ResponseEntity<Map> r = deCuenta(c.cuentaId(), "?mes=" + malo);
            assertThat(r.getStatusCode()).as(malo).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(r.getBody().get("codigo")).isEqualTo("PARAMETRO_INVALIDO");
            assertThat(deEmpresa(c.empresas().get(0), "?mes=" + malo).getStatusCode()).as(malo).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test void unaCuentaOEmpresaQueNoExistenSon404YUnIdMalFormadoEs400() {
        assertThat(deCuenta(UUID.randomUUID(), "?mes=2026-10").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deEmpresa(UUID.randomUUID(), "?mes=2026-10").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(llamar(HttpMethod.GET, "/v1/admin/cuentas/no-es-un-uuid/consumo", conClaveDePlataforma(), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Consultar no cambia nada ni deja registro: es solo lectura. */
    @Test void consultarNoEscribeNada() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        documento(c.empresas().get(0), "ACEPTADO", MITAD);
        long documentos = contar("documento");

        deCuenta(c.cuentaId(), "?mes=2026-10");
        deEmpresa(c.empresas().get(0), "?mes=2026-10");

        assertThat(contar("documento")).isEqualTo(documentos);
        assertThat(contar("auditoria_admin")).isZero();
    }

    /** El consumo de todos los clientes es del administrador: ni el dueño, ni una API key, ni una clave errónea pueden verlo. */
    @Test void nadieMasPuedeConsultarElConsumo() {
        Cliente c = cliente("ana@negocio.pe", "20100066603");
        HttpHeaders conClaveErronea = json(); conClaveErronea.set("X-Platform-Key", "clave-incorrecta");

        for (HttpHeaders h : List.of(json(), conBearer(c.access()), conApiKey(c.apiKey()), conClaveErronea)) {
            assertThat(llamar(HttpMethod.GET, "/v1/admin/cuentas/" + c.cuentaId() + "/consumo", h, null).getStatusCode()).as("cuenta con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(llamar(HttpMethod.GET, "/v1/admin/empresas/" + c.empresas().get(0) + "/consumo", h, null).getStatusCode()).as("empresa con %s", h).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
