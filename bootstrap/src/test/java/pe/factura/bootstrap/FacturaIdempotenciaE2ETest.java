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
 * Idempotencia de la emisión (#115) de extremo a extremo: HTTP real y Postgres real. El caso que motivó el issue: la red se corta
 * después de que el backend emitió, el cliente no ve la respuesta y repite el pedido.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class FacturaIdempotenciaE2ETest {
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

    private ResponseEntity<Map> emitir(String clave, String cuerpo) {
        HttpHeaders conClave = new HttpHeaders();
        conClave.putAll(h);
        if (clave != null) conClave.set("Idempotency-Key", clave);
        return http.postForEntity("/v1/facturas", new HttpEntity<>(cuerpo, conClave), Map.class);
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
}
