package pe.factura.bootstrap;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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

import java.util.*;
import java.util.concurrent.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotencia de la emisión de notas (#355) de extremo a extremo: HTTP real, Postgres real y SUNAT simulado. El caso: la red se corta después de que el
 * backend emitió la nota, el cliente no ve la respuesta y repite el pedido; sin la clave salía otra nota con otro correlativo.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@WireMockTest(httpPort = 18089)
class NotaIdempotenciaE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("app.sunat.beta-url", () -> "http://localhost:18089/billService");
        r.add("app.sunat.timeout-seconds", () -> "2");
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    HttpHeaders h;
    String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();

    static final String NOTA = """
        {"tipo":"%s","serie":"%s","fecha_emision":"%%s","documento_afectado":{"serie":"%s","numero":1},"motivo":"%s","descripcion":"Sustento"%s}
        """;
    static final String UNA_LECHE = ",\"items\":[{\"codigo\":\"P002\",\"descripcion\":\"Leche\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":4.72,\"tipo_afectacion_igv\":\"10\"}]";

    @BeforeEach void empresaConUnaFacturaYUnaBoletaAceptadas() throws Exception {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, idempotencia CASCADE");
        stubFor(post("/billService").withRequestBody(containing("sendBill")).willReturn(okXml(FacturaE2ETest.soapOk("20100066603-01-F001-1"))));
        h = new HttpHeaders();
        h.set("X-Api-Key", EmpresaDePrueba.provisionar(http));
        h.setContentType(MediaType.APPLICATION_JSON);
        for (String serie : new String[]{"{\"tipo\":\"03\",\"serie\":\"B001\"", "{\"tipo\":\"07\",\"serie\":\"BC01\"", "{\"tipo\":\"07\",\"serie\":\"FC01\"", "{\"tipo\":\"08\",\"serie\":\"FD01\""})
            assertThat(http.postForEntity("/v1/series", new HttpEntity<>(serie + ",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/v1/facturas", new HttpEntity<>("""
            {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN",
             "cliente":{"tipo_doc":"6","num_doc":"20601234565","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
             "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":10,"precio_unitario":0.50,"tipo_afectacion_igv":"10"},
                      {"codigo":"P002","descripcion":"Leche","unidad":"NIU","cantidad":2,"precio_unitario":4.72,"tipo_afectacion_igv":"10"}]}
            """.formatted(hoy), h), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/v1/facturas", new HttpEntity<>("""
            {"serie":"B001","fecha_emision":"%s","moneda":"PEN",
             "cliente":{"tipo_doc":"-","num_doc":"-","razon_social":"CLIENTES VARIOS"},
             "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":10,"precio_unitario":0.50,"tipo_afectacion_igv":"10"},
                      {"codigo":"P002","descripcion":"Leche","unidad":"NIU","cantidad":2,"precio_unitario":4.72,"tipo_afectacion_igv":"10"}]}
            """.formatted(hoy), h), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String nota(String tipo, String serie, String afectada, String motivo, String extra) {
        return NOTA.formatted(tipo, serie, afectada, motivo, extra).formatted(hoy);
    }

    private String ncTotalDeFactura() { return nota("07", "FC01", "F001", "01", ""); }

    private String ncTotalDeBoleta() { return nota("07", "BC01", "B001", "01", ""); }

    private ResponseEntity<Map> emitir(String clave, String cuerpo) { return emitir("/v1/notas", clave, cuerpo); }

    private ResponseEntity<Map> emitir(String ruta, String clave, String cuerpo) {
        HttpHeaders conClave = new HttpHeaders();
        conClave.putAll(h);
        if (clave != null) conClave.set("Idempotency-Key", clave);
        return http.postForEntity(ruta, new HttpEntity<>(cuerpo, conClave), Map.class);
    }

    private static Map<?, ?> datos(ResponseEntity<Map> r) { return (Map<?, ?>) r.getBody().get("datos"); }

    private int notas() { return jdbc.queryForObject("SELECT count(*) FROM documento WHERE tipo IN ('07','08')", Integer.class); }

    private long ultimoNumero(String serie) { return jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = ?", Long.class, serie); }

    @Test void elReintentoDeUnaNotaDeFacturaDevuelveLaMismaNotaYNoConsumeNumero() {
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> primero = emitir(clave, ncTotalDeFactura());
        int enviosDeLaNota = findAll(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("07-FC01-1"))).size();

        ResponseEntity<Map> reintento = emitir(clave, ncTotalDeFactura());

        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reintento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(datos(reintento).get("id")).isEqualTo(datos(primero).get("id"));
        assertThat(datos(reintento).get("numero")).isEqualTo(1);
        assertThat(datos(reintento).get("estado_documento")).isEqualTo("ACEPTADO");
        assertThat(notas()).isEqualTo(1);
        assertThat(ultimoNumero("FC01")).as("el reintento no tomó número").isEqualTo(1);
        assertThat(findAll(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("07-FC01-1"))).size()).as("ni se reenvió a SUNAT").isEqualTo(enviosDeLaNota);
    }

    @Test void elReintentoDeUnaNotaDeBoletaDevuelveLaMismaNotaYNoConsumeNumero() {
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> primero = emitir(clave, ncTotalDeBoleta());

        ResponseEntity<Map> reintento = emitir(clave, ncTotalDeBoleta());

        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reintento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(datos(reintento).get("id")).isEqualTo(datos(primero).get("id"));
        assertThat(((Map<?, ?>) datos(reintento).get("nota")).get("tipo_afectado")).isEqualTo("03");
        assertThat(notas()).isEqualTo(1);
        assertThat(ultimoNumero("BC01")).isEqualTo(1);
    }

    /** El criterio del issue: ocho pedidos idénticos a la vez → una sola nota y un solo número consumido, en una nota de boleta y en una de factura. */
    @Test void pedidosIdenticosSimultaneosEmitenUnaSolaNota() throws Exception {
        for (String[] caso : new String[][]{{"FC01", "FC01"}, {"BC01", "BC01"}}) {
            String cuerpo = caso[0].equals("FC01") ? ncTotalDeFactura() : ncTotalDeBoleta();
            String clave = UUID.randomUUID().toString();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<ResponseEntity<Map>>> pedidos = new ArrayList<>();
            for (int i = 0; i < 8; i++) pedidos.add(pool.submit(() -> { salida.await(); return emitir(clave, cuerpo); }));
            salida.countDown();
            List<ResponseEntity<Map>> respuestas = new ArrayList<>();
            for (Future<ResponseEntity<Map>> p : pedidos) respuestas.add(p.get(60, TimeUnit.SECONDS));
            pool.shutdown();

            assertThat(respuestas).as(caso[0]).extracting(ResponseEntity::getStatusCode).filteredOn(s -> s.equals(HttpStatus.CREATED)).hasSize(1);
            assertThat(respuestas).as(caso[0]).extracting(ResponseEntity::getStatusCode).filteredOn(s -> s.equals(HttpStatus.OK)).hasSize(7);
            assertThat(respuestas.stream().map(r -> datos(r).get("id")).distinct().toList()).as("todos la misma nota").hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM documento WHERE serie = ?", Integer.class, caso[1])).as("una sola nota " + caso[1]).isEqualTo(1);
            assertThat(ultimoNumero(caso[1])).as("un solo número consumido en " + caso[1]).isEqualTo(1);
        }
    }

    @Test void laMismaClaveConOtraNotaSeRechazaSinEmitir() {
        String clave = UUID.randomUUID().toString();
        emitir(clave, nota("07", "FC01", "F001", "07", UNA_LECHE));

        ResponseEntity<Map> otra = emitir(clave, nota("07", "FC01", "F001", "09", UNA_LECHE));

        assertThat(otra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(otra.getBody()).containsEntry("codigo", "IDEMPOTENCIA_INVALIDA");
        assertThat((String) otra.getBody().get("mensaje")).contains("otra nota");
        assertThat(notas()).isEqualTo(1);
    }

    /** Una nota rechazada por una regla de negocio no gasta la clave: el reintento corregido la usa. */
    @Test void unaNotaRechazadaNoGastaLaClave() {
        emitir(UUID.randomUUID().toString(), ncTotalDeFactura());
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> rechazada = emitir(clave, nota("07", "FC01", "F001", "07", UNA_LECHE));
        assertThat(rechazada.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) rechazada.getBody().get("mensaje")).startsWith("3286");

        ResponseEntity<Map> corregida = emitir(clave, nota("08", "FD01", "F001", "01", UNA_LECHE));

        assertThat(corregida.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** La misma cadena en una factura y en una nota son claves distintas: la factura no «responde» a la nota ni la rechaza por otro contenido. */
    @Test void laMismaClaveEnFacturasYEnNotasNoSeCruza() {
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> factura = emitir("/v1/facturas", clave, """
            {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN","enviar_automatico":false,
             "cliente":{"tipo_doc":"6","num_doc":"20601234565","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
             "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":1,"precio_unitario":0.50,"tipo_afectacion_igv":"10"}]}
            """.formatted(hoy));
        assertThat(factura.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> nota = emitir(clave, ncTotalDeFactura());

        assertThat(nota.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(datos(nota).get("tipo")).isEqualTo("07");
        assertThat(emitir(clave, ncTotalDeFactura()).getStatusCode()).as("y el reintento de la nota sí es el mismo").isEqualTo(HttpStatus.OK);
    }

    /** H1 de la revisión de #225, en la nota: el reintento se contesta antes de mirar el estado del documento que modifica. */
    @Test void elReintentoSeContestaAunqueElDocumentoYaEsteAnulado() {
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> primero = emitir(clave, ncTotalDeFactura());
        jdbc.update("UPDATE documento SET estado = 'ANULADO' WHERE serie = 'F001'");

        ResponseEntity<Map> reintento = emitir(clave, ncTotalDeFactura());

        assertThat(reintento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(datos(reintento).get("id")).isEqualTo(datos(primero).get("id"));
        assertThat(emitir(UUID.randomUUID().toString(), ncTotalDeFactura()).getStatusCode()).as("con otra clave sí rige el estado del documento").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test void unaClaveMalFormadaEs422ySinClaveCadaPedidoEsUnaNotaNueva() {
        assertThat(emitir("corta", ncTotalDeFactura()).getBody()).containsEntry("codigo", "IDEMPOTENCIA_INVALIDA");
        assertThat(notas()).isZero();

        emitir(null, nota("07", "FC01", "F001", "07", UNA_LECHE));
        emitir(null, nota("07", "FC01", "F001", "07", UNA_LECHE));
        assertThat(notas()).isEqualTo(2);
    }
}
