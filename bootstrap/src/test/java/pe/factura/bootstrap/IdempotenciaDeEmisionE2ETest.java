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
import pe.factura.application.port.in.LimpiarIdempotenciaUseCase;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotencia de la emisión de extremo a extremo —facturas (#115) y notas (#355)—: HTTP real y Postgres real. El caso que motivó los issues: la red se
 * corta después de que el backend emitió, el cliente no ve la respuesta y repite el pedido.
 * <p>
 * Un solo contexto de Spring para los dos: cada clase E2E con su propio contexto (y su Postgres) queda en la caché de Spring durante toda la corrida, y
 * con ellos el heap de las pruebas del módulo se agota. Por eso las notas no tienen su clase aparte y se emiten sin envío automático (el estado de los
 * documentos afectados se fija en la base): lo que se prueba es la clave, no SUNAT; el «no se reenvía» lo cubre {@code EmitirNotaServiceTest}.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class IdempotenciaDeEmisionE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired LimpiarIdempotenciaUseCase limpieza;
    HttpHeaders h;

    /** Sin envío automático: lo que se prueba es la emisión, no SUNAT. */
    static final String FACTURA = """
        {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN","enviar_automatico":false,
         "cliente":{"tipo_doc":"6","num_doc":"20601234565","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
         "items":[{"codigo":"P001","descripcion":"Laptop","unidad":"NIU","cantidad":1,"precio_unitario":2360.00,"tipo_afectacion_igv":"10"}]}
        """.formatted(java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")));

    @BeforeEach void empresa() throws Exception {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, idempotencia CASCADE");
        h = new HttpHeaders();
        h.set("X-Api-Key", EmpresaDePrueba.provisionar(http));
        h.setContentType(MediaType.APPLICATION_JSON);
    }

    private ResponseEntity<Map> emitir(String clave, String cuerpo) { return emitir("/v1/facturas", clave, cuerpo); }

    private ResponseEntity<Map> emitir(String ruta, String clave, String cuerpo) {
        HttpHeaders conClave = new HttpHeaders();
        conClave.putAll(h);
        if (clave != null) conClave.set("Idempotency-Key", clave);
        return http.postForEntity(ruta, new HttpEntity<>(cuerpo, conClave), Map.class);
    }

    private static Map<?, ?> datos(ResponseEntity<Map> r) { return (Map<?, ?>) r.getBody().get("datos"); }

    private int comprobantes() { return jdbc.queryForObject("SELECT count(*) FROM comprobante", Integer.class); }

    @Test void elReintentoDevuelveLaMismaFacturaYNoConsumeNumero() {
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> primero = emitir(clave, FACTURA);
        ResponseEntity<Map> reintento = emitir(clave, FACTURA);

        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reintento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(datos(reintento).get("id")).isEqualTo(datos(primero).get("id"));
        assertThat(datos(reintento).get("numero")).isEqualTo(1);
        assertThat(comprobantes()).isEqualTo(1);
        assertThat(datos(emitir(UUID.randomUUID().toString(), FACTURA)).get("numero")).as("el número 2 sigue libre").isEqualTo(2);
    }

    /** El criterio del issue: dos (aquí, ocho) pedidos idénticos a la vez → una sola factura. */
    @Test void pedidosIdenticosSimultaneosEmitenUnaSolaFactura() throws Exception {
        String clave = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> pedidos = new ArrayList<>();
        for (int i = 0; i < 8; i++) pedidos.add(pool.submit(() -> { salida.await(); return emitir(clave, FACTURA); }));
        salida.countDown();
        List<ResponseEntity<Map>> respuestas = new ArrayList<>();
        for (Future<ResponseEntity<Map>> p : pedidos) respuestas.add(p.get(60, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(respuestas).extracting(ResponseEntity::getStatusCode).filteredOn(s -> s.equals(HttpStatus.CREATED)).hasSize(1);
        assertThat(respuestas).extracting(ResponseEntity::getStatusCode).filteredOn(s -> s.equals(HttpStatus.OK)).hasSize(7);
        assertThat(respuestas.stream().map(r -> datos(r).get("id")).distinct().toList()).as("todos el mismo comprobante").hasSize(1);
        assertThat(comprobantes()).isEqualTo(1);
    }

    @Test void laMismaClaveConOtraFacturaSeRechaza() {
        String clave = UUID.randomUUID().toString();
        emitir(clave, FACTURA);

        ResponseEntity<Map> otra = emitir(clave, FACTURA.replace("Laptop", "Monitor"));

        assertThat(otra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(otra.getBody()).containsEntry("codigo", "IDEMPOTENCIA_INVALIDA");
        assertThat(comprobantes()).isEqualTo(1);
    }

    /** Una emisión que falla no deja la clave tomada: el reintento corregido la usa. */
    @Test void unaEmisionRechazadaNoGastaLaClave() {
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> rechazada = emitir(clave, FACTURA.replace("\"serie\":\"F001\"", "\"serie\":\"F999\""));
        assertThat(rechazada.getStatusCode().is4xxClientError()).isTrue();

        assertThat(emitir(clave, FACTURA).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test void pasadas24HorasLaClaveVuelveASerNueva() {
        String clave = UUID.randomUUID().toString();
        emitir(clave, FACTURA);
        jdbc.update("UPDATE idempotencia SET creado_at = now() - interval '25 hours'");

        assertThat(limpieza.limpiar()).isEqualTo(1);
        ResponseEntity<Map> otraVez = emitir(clave, FACTURA);
        assertThat(otraVez.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(datos(otraVez).get("numero")).isEqualTo(2);
    }

    @Test void sinClaveCadaPedidoEsUnaFacturaNueva() {
        emitir(null, FACTURA);
        emitir(null, FACTURA);
        assertThat(comprobantes()).isEqualTo(2);
    }

    // --- Notas (#355) ---------------------------------------------------------------------------------------------------------------

    static final String HOY = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();
    static final String BOLETA = """
        {"serie":"B001","fecha_emision":"%s","moneda":"PEN","enviar_automatico":false,
         "cliente":{"tipo_doc":"-","num_doc":"-","razon_social":"CLIENTES VARIOS"},
         "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":10,"precio_unitario":0.50,"tipo_afectacion_igv":"10"}]}
        """.formatted(HOY);
    static final String NOTA = """
        {"tipo":"%s","serie":"%s","fecha_emision":"%s","documento_afectado":{"serie":"%s","numero":1},"motivo":"%s","descripcion":"Sustento","enviar_automatico":false%s}
        """;
    static final String UNA_LECHE = ",\"items\":[{\"codigo\":\"P002\",\"descripcion\":\"Leche\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":4.72,\"tipo_afectacion_igv\":\"10\"}]";

    /** Una factura (F001-1) y una boleta (B001-1) ya aceptadas por SUNAT, con las series de sus notas. */
    private void conUnaFacturaYUnaBoletaAceptadas() {
        for (String serie : new String[]{"{\"tipo\":\"03\",\"serie\":\"B001\"", "{\"tipo\":\"07\",\"serie\":\"BC01\"", "{\"tipo\":\"07\",\"serie\":\"FC01\"", "{\"tipo\":\"08\",\"serie\":\"FD01\""})
            assertThat(http.postForEntity("/v1/series", new HttpEntity<>(serie + ",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(emitir(null, FACTURA).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(emitir(null, BOLETA).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("UPDATE documento SET estado = 'ACEPTADO' WHERE tipo IN ('01','03')");
    }

    private static String nota(String tipo, String serie, String afectada, String motivo, String extra) { return NOTA.formatted(tipo, serie, HOY, afectada, motivo, extra); }

    private static String ncTotalDeFactura() { return nota("07", "FC01", "F001", "01", ""); }

    private static String ncTotalDeBoleta() { return nota("07", "BC01", "B001", "01", ""); }

    private int notas() { return jdbc.queryForObject("SELECT count(*) FROM documento WHERE tipo IN ('07','08')", Integer.class); }

    private long ultimoNumero(String serie) { return jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = ?", Long.class, serie); }

    @Test void elReintentoDeUnaNotaDeFacturaDevuelveLaMismaNotaYNoConsumeNumero() {
        conUnaFacturaYUnaBoletaAceptadas();
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> primero = emitir("/v1/notas", clave, ncTotalDeFactura());

        ResponseEntity<Map> reintento = emitir("/v1/notas", clave, ncTotalDeFactura());

        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reintento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(datos(reintento).get("id")).isEqualTo(datos(primero).get("id"));
        assertThat(datos(reintento).get("numero")).isEqualTo(1);
        assertThat(notas()).isEqualTo(1);
        assertThat(ultimoNumero("FC01")).as("el reintento no tomó número").isEqualTo(1);
    }

    @Test void elReintentoDeUnaNotaDeBoletaDevuelveLaMismaNotaYNoConsumeNumero() {
        conUnaFacturaYUnaBoletaAceptadas();
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> primero = emitir("/v1/notas", clave, ncTotalDeBoleta());

        ResponseEntity<Map> reintento = emitir("/v1/notas", clave, ncTotalDeBoleta());

        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reintento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(datos(reintento).get("id")).isEqualTo(datos(primero).get("id"));
        assertThat(((Map<?, ?>) datos(reintento).get("nota")).get("tipo_afectado")).isEqualTo("03");
        assertThat(notas()).isEqualTo(1);
        assertThat(ultimoNumero("BC01")).isEqualTo(1);
    }

    /** El criterio de #355: ocho pedidos idénticos a la vez → una sola nota y un solo número consumido, en una nota de boleta y en una de factura. */
    @Test void pedidosIdenticosSimultaneosEmitenUnaSolaNota() throws Exception {
        conUnaFacturaYUnaBoletaAceptadas();
        for (String[] caso : new String[][]{{"FC01", ncTotalDeFactura()}, {"BC01", ncTotalDeBoleta()}}) {
            String clave = UUID.randomUUID().toString();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                CountDownLatch salida = new CountDownLatch(1);
                List<Future<ResponseEntity<Map>>> pedidos = new ArrayList<>();
                for (int i = 0; i < 8; i++) pedidos.add(pool.submit(() -> { salida.await(); return emitir("/v1/notas", clave, caso[1]); }));
                salida.countDown();
                List<ResponseEntity<Map>> respuestas = new ArrayList<>();
                for (Future<ResponseEntity<Map>> p : pedidos) respuestas.add(p.get(60, TimeUnit.SECONDS));

                assertThat(respuestas).as(caso[0]).extracting(ResponseEntity::getStatusCode).filteredOn(s -> s.equals(HttpStatus.CREATED)).hasSize(1);
                assertThat(respuestas).as(caso[0]).extracting(ResponseEntity::getStatusCode).filteredOn(s -> s.equals(HttpStatus.OK)).hasSize(7);
                assertThat(respuestas.stream().map(r -> datos(r).get("id")).distinct().toList()).as("todos la misma nota").hasSize(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM documento WHERE serie = ?", Integer.class, caso[0])).as("una sola nota " + caso[0]).isEqualTo(1);
                assertThat(ultimoNumero(caso[0])).as("un solo número consumido en " + caso[0]).isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test void laMismaClaveConOtraNotaSeRechazaSinEmitir() {
        conUnaFacturaYUnaBoletaAceptadas();
        String clave = UUID.randomUUID().toString();
        emitir("/v1/notas", clave, nota("07", "FC01", "F001", "07", UNA_LECHE));

        ResponseEntity<Map> otra = emitir("/v1/notas", clave, nota("07", "FC01", "F001", "09", UNA_LECHE));

        assertThat(otra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(otra.getBody()).containsEntry("codigo", "IDEMPOTENCIA_INVALIDA");
        assertThat((String) otra.getBody().get("mensaje")).contains("otra nota");
        assertThat(notas()).isEqualTo(1);
    }

    /** Una nota rechazada por una regla de negocio dentro de la transacción (ya reservada la clave) no gasta la clave: el reintento corregido la usa. */
    @Test void unaNotaRechazadaNoGastaLaClave() {
        conUnaFacturaYUnaBoletaAceptadas();
        emitir("/v1/notas", UUID.randomUUID().toString(), ncTotalDeFactura());
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> rechazada = emitir("/v1/notas", clave, nota("07", "FC01", "F001", "07", UNA_LECHE));
        assertThat(rechazada.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) rechazada.getBody().get("mensaje")).startsWith("3286");

        ResponseEntity<Map> corregida = emitir("/v1/notas", clave, nota("08", "FD01", "F001", "01", UNA_LECHE));

        assertThat(corregida.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** La misma cadena en una factura y en una nota son claves distintas: la factura no «responde» a la nota ni la rechaza por otro contenido. */
    @Test void laMismaClaveEnFacturasYEnNotasNoSeCruza() {
        conUnaFacturaYUnaBoletaAceptadas();
        String clave = UUID.randomUUID().toString();
        assertThat(emitir(clave, FACTURA.replace("Laptop", "Monitor")).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> nota = emitir("/v1/notas", clave, ncTotalDeFactura());

        assertThat(nota.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(datos(nota).get("tipo")).isEqualTo("07");
        assertThat(emitir("/v1/notas", clave, ncTotalDeFactura()).getStatusCode()).as("y el reintento de la nota sí es el mismo").isEqualTo(HttpStatus.OK);
    }

    /** H1 de la revisión de #225, en la nota: el reintento se contesta antes de mirar el estado del documento que modifica. */
    @Test void elReintentoSeContestaAunqueElDocumentoYaEsteAnulado() {
        conUnaFacturaYUnaBoletaAceptadas();
        String clave = UUID.randomUUID().toString();
        ResponseEntity<Map> primero = emitir("/v1/notas", clave, ncTotalDeFactura());
        jdbc.update("UPDATE documento SET estado = 'ANULADO' WHERE serie = 'F001'");

        ResponseEntity<Map> reintento = emitir("/v1/notas", clave, ncTotalDeFactura());

        assertThat(reintento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(datos(reintento).get("id")).isEqualTo(datos(primero).get("id"));
        assertThat(emitir("/v1/notas", UUID.randomUUID().toString(), ncTotalDeFactura()).getStatusCode()).as("con otra clave sí rige el estado del documento").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test void unaClaveMalFormadaDeUnaNotaEs422ySinClaveCadaPedidoEsUnaNotaNueva() {
        conUnaFacturaYUnaBoletaAceptadas();
        assertThat(emitir("/v1/notas", "corta", ncTotalDeFactura()).getBody()).containsEntry("codigo", "IDEMPOTENCIA_INVALIDA");
        assertThat(notas()).isZero();

        emitir("/v1/notas", null, nota("07", "FC01", "F001", "07", UNA_LECHE));
        emitir("/v1/notas", null, nota("07", "FC01", "F001", "07", UNA_LECHE));
        assertThat(notas()).isEqualTo(2);
    }
}
