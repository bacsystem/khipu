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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La numeración de la emisión (#9), de punta a punta con la pila real: SUNAT exige los números correlativos, así que varias emisiones a la vez sobre la misma serie no pueden
 * repetir ni saltarse un número, y un fallo después de asignarlo (aquí, el almacenamiento) no puede dejar un hueco. El bloqueo de la serie se mantiene a propósito durante todo el
 * trabajo (ver `k6/README.md`); esto es lo que ese bloqueo garantiza.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class NumeracionDeLaEmisionE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Path RAIZ = crearRaiz();

    static Path crearRaiz() {
        try {
            return Files.createTempDirectory("khipu-numeracion");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("app.storage.fs-root", RAIZ::toString);
        r.add("app.sunat.timeout-seconds", () -> "2");
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    static final String FACTURA = """
        {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN","enviar_automatico":false,
         "cliente":{"tipo_doc":"6","num_doc":"20601234565","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
         "items":[{"codigo":"P001","descripcion":"Laptop","unidad":"NIU","cantidad":1,"precio_unitario":2360.00,"tipo_afectacion_igv":"10"}]}
        """.formatted(LocalDate.now(ZoneId.of("America/Lima")));

    @BeforeEach void limpiar() {
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant CASCADE");
    }

    HttpHeaders conApiKey(String apiKey) { HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    ResponseEntity<Map> emitir(String apiKey) { return http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, conApiKey(apiKey)), Map.class); }

    @Test void dieciseisEmisionesALaVezSobreLaMismaSerieNoRepitenNiSaltanUnNumero() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<ResponseEntity<Map>>> pedidos = new ArrayList<>();
            for (int i = 0; i < n; i++) pedidos.add(pool.submit(() -> { salida.await(); return emitir(apiKey); }));
            salida.countDown();

            TreeSet<Integer> numeros = new TreeSet<>();
            for (Future<ResponseEntity<Map>> f : pedidos) {
                ResponseEntity<Map> r = f.get(120, TimeUnit.SECONDS);
                assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.CREATED);
                numeros.add((Integer) ((Map<?, ?>) r.getBody().get("datos")).get("numero"));
            }

            assertThat(numeros).as("16 números distintos y correlativos").hasSize(n).first().isEqualTo(1);
            assertThat(numeros.last()).isEqualTo(n);
            assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = 'F001'", Long.class)).isEqualTo(n);
            assertThat(jdbc.queryForObject("SELECT count(DISTINCT numero) FROM documento WHERE serie = 'F001'", Integer.class)).isEqualTo(n);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Si guardar el XML falla después de asignar el número, todo se deshace: la serie no avanza y la siguiente emisión recibe ese mismo número, sin hueco. */
    @Test void siElAlmacenamientoFallaDespuesDeNumerarElNumeroNoSePierde() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        assertThat(emitir(apiKey).getStatusCode()).isEqualTo(HttpStatus.CREATED);   // F001-1
        long antes = jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = 'F001'", Long.class);
        // La raíz del almacenamiento pasa a ser un archivo: crear sus carpetas falla justo al guardar, después de numerar y firmar.
        borrarRecursivo(RAIZ);
        Files.writeString(RAIZ, "no es una carpeta");
        try {
            ResponseEntity<Map> fallida = emitir(apiKey);

            assertThat(fallida.getStatusCode().is5xxServerError()).as("%s", fallida.getBody()).isTrue();
            assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = 'F001'", Long.class)).as("la serie no avanzó").isEqualTo(antes);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM documento WHERE serie = 'F001'", Integer.class)).as("no quedó un documento a medias").isEqualTo(1);
        } finally {
            Files.deleteIfExists(RAIZ);
            Files.createDirectories(RAIZ);
        }

        ResponseEntity<Map> siguiente = emitir(apiKey);

        assertThat(siguiente.getStatusCode()).as("%s", siguiente.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) siguiente.getBody().get("datos")).get("numero")).as("el número del intento fallido se reutiliza: sin hueco").isEqualTo((int) antes + 1);
    }

    private static void borrarRecursivo(Path raiz) throws IOException {
        if (!Files.exists(raiz)) return;
        try (var arbol = Files.walk(raiz)) {
            arbol.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
