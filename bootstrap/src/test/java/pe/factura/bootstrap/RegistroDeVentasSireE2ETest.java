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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El registro de ventas del SIRE (#322) de extremo a extremo: HTTP real, Postgres real, comprobantes completos (con ítems) rehidratados por el repositorio. Lo que
 * importa: que el archivo traiga solo lo del periodo pedido y solo lo de la empresa, solo lo que SUNAT tiene, con las notas y los anulados como dice el dominio; que
 * el recorrido por tramos no repita ni salte filas; que los errores lleguen antes del archivo; y que el archivo propio, vuelto a subir, concilie sin diferencias.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class RegistroDeVentasSireE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    static final String SEPTIEMBRE = "202609";
    static final String[] RUCS = {"20100066603", "20100066611"};
    private int siguiente = 0;

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, token_verificacion, sesion, usuario, cuenta, administrador, auditoria_admin CASCADE");
    }

    record Cliente(String access, UUID empresa, String apiKey, String ruc) {}

    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private HttpHeaders conApiKey(String key) { HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", key); return h; }

    private Cliente cliente(String email) {
        ResponseEntity<Map> r = http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Mi negocio\",\"email\":\"%s\",\"password\":\"Segura123\",\"telefono\":\"987654321\"}".formatted(email), json()), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE usuario SET correo_verificado_at = now() WHERE email = ?", email);
        String access = (String) ((Map<?, ?>) r.getBody().get("datos")).get("access");
        String ruc = RUCS[siguiente++];
        HttpHeaders bearer = json(); bearer.setBearerAuth(access);
        assertThat(http.exchange("/v1/empresas", HttpMethod.POST, new HttpEntity<>("{\"ruc\":\"%s\",\"razon_social\":\"EMPRESA %s S.A.C.\",\"entorno\":\"BETA\"}".formatted(ruc, ruc), bearer), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID empresa = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc);
        bearer.set("X-Empresa", empresa.toString());
        String key = (String) ((Map<?, ?>) http.exchange("/v1/empresa/api-keys", HttpMethod.POST, new HttpEntity<>(null, bearer), Map.class).getBody().get("datos")).get("api_key");
        return new Cliente(access, empresa, key, ruc);
    }

    /** Un comprobante con una línea de {@code precio} (IGV incluido si es gravada), tal como lo deja la emisión en las tablas. */
    private UUID comprobante(Cliente c, String tipo, String serie, long numero, LocalDate fecha, String estado, String moneda, BigDecimal tipoCambio, String afectacion, String precio,
                             String notaSerie, Long notaNumero) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, ?, ?, ?, ?, ?, 'x')",
                id, c.empresa(), tipo, serie, numero, Date.valueOf(fecha), estado);
        boolean boleta = "03".equals(tipo);
        jdbc.update("""
                INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, total_gravado, total_exonerado, total_inafecto, total_igv, total,
                  tipo_cambio, nota_tipo_afectado, nota_serie_afectada, nota_numero_afectado, nota_motivo, nota_descripcion)
                VALUES (?, '0101', ?, ?, ?, ?, 0, 0, 0, 0, 0, ?, ?, ?, ?, ?, ?)
                """, id, moneda, boleta ? "1" : "6", boleta ? "12345678" : "20601234565", boleta ? "JUAN PÉREZ | ÑAÑEZ" : "COMERCIAL ANDINA S.A.C.", tipoCambio,
                notaSerie == null ? null : "01", notaSerie, notaNumero, notaSerie == null ? null : "01", notaSerie == null ? null : "Anulación de la operación");
        jdbc.update("INSERT INTO comprobante_item (comprobante_id, orden, codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv) VALUES (?, 1, 'P1', 'Producto', 'NIU', 1, ?, ?)",
                id, new BigDecimal(precio), afectacion);
        return id;
    }

    private UUID factura(Cliente c, long numero, LocalDate fecha, String estado) {
        return comprobante(c, "01", "F001", numero, fecha, estado, "PEN", null, "10", "118.00", null, null);
    }

    private ResponseEntity<byte[]> descargar(String query, HttpHeaders h) {
        return http.exchange("/v1/sire/rvie" + query, HttpMethod.GET, new HttpEntity<>(null, h), byte[].class);
    }

    private ResponseEntity<Map> conciliar(Cliente c, String periodo, byte[] archivo, String tipo) {
        HttpHeaders h = conApiKey(c.apiKey());
        h.setContentType(MediaType.parseMediaType(tipo));
        return http.exchange("/v1/sire/rvie/conciliacion?periodo=" + periodo, HttpMethod.POST, new HttpEntity<>(archivo, h), Map.class);
    }

    private static String texto(byte[] zip, String nombreEsperado) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e = z.getNextEntry();
            assertThat(e).isNotNull();
            assertThat(e.getName()).isEqualTo(nombreEsperado);
            String t = new String(z.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(z.getNextEntry()).isNull();
            return t;
        }
    }

    private static List<String[]> filas(String texto) {
        List<String[]> r = new ArrayList<>();
        for (String l : texto.split("\r\n")) if (!l.isEmpty()) r.add(l.split("\\|", -1));
        return r;
    }

    // --- el archivo -------------------------------------------------------------------------------------------------------------------------

    @Test void elArchivoTraeSoloLoDelPeriodoDeLaEmpresaQueSunatTieneConNotasYAnulados() throws IOException {
        Cliente c = cliente("ana@negocio.pe");
        Cliente otra = cliente("beto@otro.pe");
        factura(c, 50, LocalDate.of(2026, 8, 31), "ACEPTADO");                       // agosto: otro periodo, pero la nota de septiembre lo modifica
        factura(c, 51, LocalDate.of(2026, 9, 1), "ACEPTADO");
        factura(c, 52, LocalDate.of(2026, 9, 2), "ACEPTADO_CON_OBS");
        factura(c, 53, LocalDate.of(2026, 9, 3), "ANULADO");
        factura(c, 54, LocalDate.of(2026, 9, 4), "RECHAZADO");                       // no es una venta
        factura(c, 55, LocalDate.of(2026, 9, 5), "ENVIADO");                         // en camino: no entra, se cuenta
        factura(c, 56, LocalDate.of(2026, 9, 6), "ERROR_ENVIO");                     // también
        factura(c, 57, LocalDate.of(2026, 9, 7), "INVALIDO");
        comprobante(c, "03", "B001", 7, LocalDate.of(2026, 9, 8), "ACEPTADO", "PEN", null, "10", "59.00", null, null);
        comprobante(c, "07", "F001", 1, LocalDate.of(2026, 9, 9), "ACEPTADO", "PEN", null, "10", "118.00", "F001", 50L);
        comprobante(c, "01", "F002", 1, LocalDate.of(2026, 9, 30), "ACEPTADO", "USD", new BigDecimal("3.745"), "20", "100.00", null, null);
        factura(c, 58, LocalDate.of(2026, 10, 1), "ACEPTADO");                       // octubre: otro periodo
        factura(otra, 51, LocalDate.of(2026, 9, 1), "ACEPTADO");                     // la misma serie y número en otra empresa

        ResponseEntity<byte[]> r = descargar("?periodo=" + SEPTIEMBRE, conApiKey(c.apiKey()));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        String nombre = "LE" + c.ruc() + SEPTIEMBRE + "00140400021112";
        assertThat(r.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/zip"));
        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=\"" + nombre + ".zip\"");
        assertThat(r.getHeaders().getFirst("X-Rvie-Registros")).isEqualTo("6");
        assertThat(r.getHeaders().getFirst("X-Rvie-Pendientes-Sunat")).isEqualTo("2");
        assertThat(r.getHeaders().getFirst("X-Rvie-Sin-Tipo-Cambio")).isEqualTo("0");

        String texto = texto(r.getBody(), nombre + ".txt");
        List<String[]> f = filas(texto);
        assertThat(texto).endsWith("|\r\n");
        assertThat(f).hasSize(6).allSatisfy(x -> assertThat(x).hasSize(41));
        assertThat(f).extracting(x -> x[6] + "-" + x[7] + "-" + x[8]).containsExactly("01-F001-51", "01-F001-52", "01-F001-53", "03-B001-7", "07-F001-1", "01-F002-1");
        assertThat(f).allSatisfy(x -> assertThat(x[0] + "|" + x[1] + "|" + x[2]).isEqualTo(c.ruc() + "|EMPRESA " + c.ruc() + " S.A.C.|" + SEPTIEMBRE));

        String[] gravada = f.get(0);
        assertThat(gravada[3]).isEqualTo(c.ruc() + "01F0010000000051");
        assertThat(gravada[4]).isEqualTo("01/09/2026");
        assertThat(new String[]{gravada[14], gravada[16], gravada[25], gravada[26], gravada[27], gravada[34]}).containsExactly("100.00", "18.00", "118.00", "PEN", "1.000", "1");

        String[] anulada = f.get(2);
        assertThat(anulada[34]).isEqualTo("2");
        assertThat(anulada[14] + anulada[16] + anulada[25]).isEqualTo("0.000.000.00");

        String[] boleta = f.get(3);
        assertThat(new String[]{boleta[10], boleta[11], boleta[12], boleta[25]}).containsExactly("1", "12345678", "JUAN PÉREZ ÑAÑEZ", "59.00");

        String[] nota = f.get(4);
        assertThat(new String[]{nota[14], nota[16], nota[25]}).containsExactly("-100.00", "-18.00", "-118.00");
        assertThat(Arrays.copyOfRange(nota, 28, 34)).containsExactly("31/08/2026", "01", "F001", "50", "", "01");

        String[] dolares = f.get(5);
        assertThat(new String[]{dolares[18], dolares[25], dolares[26], dolares[27]}).containsExactly("100.00", "100.00", "USD", "3.745");
    }

    /** Más de un tramo (500) con fechas repetidas y dos series: el recorrido por posición no repite ni salta filas, y van en orden. */
    @Test void unPeriodoDeVariosTramosSaleCompletoYEnOrden() throws IOException {
        Cliente c = cliente("ana@negocio.pe");
        int n = 1203;
        List<Object[]> docs = new ArrayList<>(), comps = new ArrayList<>(), items = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            UUID id = UUID.randomUUID();
            docs.add(new Object[]{id, c.empresa(), k % 2 == 0 ? "F001" : "F002", (long) k, Date.valueOf(LocalDate.of(2026, 9, 1 + k % 3))});
            comps.add(new Object[]{id});
            items.add(new Object[]{id});
        }
        jdbc.batchUpdate("INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo) VALUES (?, ?, '01', ?, ?, ?, 'ACEPTADO', 'x')", docs);
        jdbc.batchUpdate("INSERT INTO comprobante (documento_id, tipo_operacion, moneda, receptor_tipo_doc, receptor_num_doc, receptor_nombre, total_gravado, total_exonerado, total_inafecto, total_igv, total) "
                + "VALUES (?, '0101', 'PEN', '6', '20601234565', 'CLIENTE', 0, 0, 0, 0, 0)", comps);
        jdbc.batchUpdate("INSERT INTO comprobante_item (comprobante_id, orden, codigo, descripcion, unidad, cantidad, precio_unitario, tipo_afectacion_igv) VALUES (?, 1, 'P', 'P', 'NIU', 1, 11.80, '10')", items);

        ResponseEntity<byte[]> r = descargar("?periodo=" + SEPTIEMBRE, conApiKey(c.apiKey()));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String[]> f = filas(texto(r.getBody(), "LE" + c.ruc() + SEPTIEMBRE + "00140400021112.txt"));
        assertThat(f).hasSize(n);
        assertThat(f.stream().map(x -> x[7] + "-" + x[8]).distinct().count()).isEqualTo(n);
        List<String> claves = f.stream().map(x -> x[4].substring(0, 2) + "|" + x[7] + "|" + "%010d".formatted(Long.parseLong(x[8]))).toList();
        assertThat(claves).isSorted();
    }

    // --- antes de escribir ------------------------------------------------------------------------------------------------------------------

    @Test void losErroresLleganComoJsonAntesDelArchivo() {
        Cliente c = cliente("ana@negocio.pe");
        HttpHeaders h = conApiKey(c.apiKey());

        for (String malo : List.of("?periodo=2026-09", "?periodo=202613", "?periodo=209912", "")) {
            ResponseEntity<Map> r = http.exchange("/v1/sire/rvie" + malo, HttpMethod.GET, new HttpEntity<>(null, h), Map.class);
            assertThat(r.getStatusCode()).as(malo).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(r.getBody().get("codigo")).isEqualTo("PARAMETRO_INVALIDO");
        }
        ResponseEntity<Map> vacio = http.exchange("/v1/sire/rvie?periodo=" + SEPTIEMBRE, HttpMethod.GET, new HttpEntity<>(null, h), Map.class);
        assertThat(vacio.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(vacio.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
    }

    @Test void fueraDeSolesSinTipoDeCambioPideConfirmarYConEllaSaleElCampoVacio() throws IOException {
        Cliente c = cliente("ana@negocio.pe");
        comprobante(c, "01", "F001", 9, LocalDate.of(2026, 9, 10), "ACEPTADO", "USD", null, "10", "118.00", null, null);

        ResponseEntity<Map> rechazo = http.exchange("/v1/sire/rvie?periodo=" + SEPTIEMBRE, HttpMethod.GET, new HttpEntity<>(null, conApiKey(c.apiKey())), Map.class);
        assertThat(rechazo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rechazo.getBody().get("codigo")).isEqualTo("RVIE_SIN_TIPO_CAMBIO");
        assertThat((String) rechazo.getBody().get("mensaje")).contains("F001-9");

        ResponseEntity<byte[]> r = descargar("?periodo=" + SEPTIEMBRE + "&permitir_sin_tipo_cambio=true", conApiKey(c.apiKey()));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getFirst("X-Rvie-Sin-Tipo-Cambio")).isEqualTo("1");
        assertThat(filas(texto(r.getBody(), "LE" + c.ruc() + SEPTIEMBRE + "00140400021112.txt")).get(0)[27]).isEmpty();
    }

    // --- quién puede verlo ------------------------------------------------------------------------------------------------------------------

    @Test void conElJwtDelPortalEsElMismoArchivoYSinCredencialesNoSeVe() throws IOException {
        Cliente c = cliente("ana@negocio.pe");
        factura(c, 1, LocalDate.of(2026, 9, 1), "ACEPTADO");
        HttpHeaders bearer = new HttpHeaders(); bearer.setBearerAuth(c.access()); bearer.set("X-Empresa", c.empresa().toString());
        String nombre = "LE" + c.ruc() + SEPTIEMBRE + "00140400021112.txt";

        assertThat(texto(descargar("?periodo=" + SEPTIEMBRE, bearer).getBody(), nombre)).isEqualTo(texto(descargar("?periodo=" + SEPTIEMBRE, conApiKey(c.apiKey())).getBody(), nombre));
        assertThat(descargar("?periodo=" + SEPTIEMBRE, new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(descargar("?periodo=" + SEPTIEMBRE, conApiKey("khp_clave-que-no-existe")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- la conciliación --------------------------------------------------------------------------------------------------------------------

    @Test void elArchivoPropioConciliaSinDiferenciasYUnoCambiadoMuestraCadaDiferencia() throws IOException {
        Cliente c = cliente("ana@negocio.pe");
        factura(c, 1, LocalDate.of(2026, 9, 1), "ACEPTADO");
        factura(c, 2, LocalDate.of(2026, 9, 2), "ACEPTADO");
        factura(c, 3, LocalDate.of(2026, 9, 3), "ENVIADO");
        byte[] zip = descargar("?periodo=" + SEPTIEMBRE, conApiKey(c.apiKey())).getBody();

        ResponseEntity<Map> propio = conciliar(c, SEPTIEMBRE, zip, "application/zip");
        assertThat(propio.getStatusCode()).as("%s", propio.getBody()).isEqualTo(HttpStatus.OK);
        Map<String, Object> d = (Map<String, Object>) propio.getBody().get("datos");
        assertThat(d).containsEntry("cuadra", true).containsEntry("coinciden", 2).containsEntry("pendientes_de_sunat", 1).containsEntry("registros_sunat", 2);

        String sunat = texto(zip, "LE" + c.ruc() + SEPTIEMBRE + "00140400021112.txt")
                .replace("|F001|2||", "|F001|4||")
                .replace("|118.00|PEN|", "|120.00|PEN|");
        ResponseEntity<Map> cambiado = conciliar(c, SEPTIEMBRE, ("RUC|Razón social|Periodo\r\n" + sunat).getBytes(StandardCharsets.UTF_8), "text/plain");
        Map<String, Object> e = (Map<String, Object>) cambiado.getBody().get("datos");
        assertThat(e).containsEntry("cuadra", false);
        assertThat((List<String>) e.get("solo_en_sunat")).containsExactly("01-F001-4");
        assertThat((List<String>) e.get("solo_en_khipu")).containsExactly("01-F001-2");
        assertThat((List<Map<String, Object>>) e.get("difieren")).extracting(x -> x.get("comprobante") + " " + x.get("campo") + " " + x.get("khipu") + "→" + x.get("sunat"))
                .containsExactly("01-F001-1 total 118.00→120.00");
    }

    @Test void laPropuestaDeOtraEmpresaNoSeConcilia() throws IOException {
        Cliente c = cliente("ana@negocio.pe");
        Cliente otra = cliente("beto@otro.pe");
        factura(c, 1, LocalDate.of(2026, 9, 1), "ACEPTADO");
        factura(otra, 1, LocalDate.of(2026, 9, 1), "ACEPTADO");
        byte[] deOtra = descargar("?periodo=" + SEPTIEMBRE, conApiKey(otra.apiKey())).getBody();

        ResponseEntity<Map> r = conciliar(c, SEPTIEMBRE, deOtra, "application/zip");

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().get("codigo")).isEqualTo("RVIE_ARCHIVO_INVALIDO");
        assertThat((String) r.getBody().get("mensaje")).contains(otra.ruc());
    }

    /** Con Tomcat de verdad: un formulario pierde el cuerpo al leer `periodo`; se rechaza en vez de conciliar contra nada. */
    @Test void unaPropuestaEnviadaComoFormularioSeRechaza() {
        Cliente c = cliente("ana@negocio.pe");
        factura(c, 1, LocalDate.of(2026, 9, 1), "ACEPTADO");
        byte[] zip = descargar("?periodo=" + SEPTIEMBRE, conApiKey(c.apiKey())).getBody();
        ResponseEntity<Map> r = conciliar(c, SEPTIEMBRE, zip, "application/x-www-form-urlencoded");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) r.getBody().get("mensaje")).contains("Content-Type");
    }

    @Test void exportarYConciliarNoEscribenNada() {
        Cliente c = cliente("ana@negocio.pe");
        factura(c, 1, LocalDate.of(2026, 9, 1), "ACEPTADO");
        long eventos = jdbc.queryForObject("SELECT count(*) FROM evento_documento", Long.class);
        byte[] zip = descargar("?periodo=" + SEPTIEMBRE, conApiKey(c.apiKey())).getBody();
        conciliar(c, SEPTIEMBRE, zip, "application/zip");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_documento", Long.class)).isEqualTo(eventos);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documento", Long.class)).isEqualTo(1);
    }
}
