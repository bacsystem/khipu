package pe.factura.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #363 de punta a punta, con la aplicación arrancada con {@code RELOJ_DESPLAZAMIENTO=-P8D} (aquí {@code app.reloj-desplazamiento}) y Postgres real: el reloj de
 * toda la aplicación va ocho días atrás, de modo que una boleta o factura de «hoy» sale con la fecha de hace ocho días; y una empresa en PRODUCCION no puede emitir ni
 * dejar arrancar la aplicación.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.reloj-desplazamiento=-P8D")
@ActiveProfiles("test")
@Testcontainers
// Su propiedad da un contexto propio y Spring los guarda todos en caché: cerrarlo al terminar evita sumar uno más al tope de memoria de la suite (ver bootstrap/build.gradle.kts).
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RelojDesplazadoE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired @Qualifier("relojDesplazadoAlArrancar") ApplicationRunner comprobacionDeArranque;

    @BeforeEach void limpiar() { jdbc.update("TRUNCATE tenant CASCADE"); }

    static String factura(LocalDate fecha) {
        return """
            {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN","enviar_automatico":false,
             "cliente":{"tipo_doc":"6","num_doc":"20601234565","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
             "items":[{"codigo":"P001","descripcion":"Laptop","unidad":"NIU","cantidad":1,"precio_unitario":2360.00,"tipo_afectacion_igv":"10"}]}
            """.formatted(fecha);
    }

    private HttpHeaders conKey(String apiKey) { HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    @Test void elRelojDeLaAplicacionVaOchoDiasAtras() {
        assertThat(Duration.between(Instant.now().minus(Duration.ofDays(8)), clock.instant()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    /** El desplazamiento llega al dominio: «hoy» del backend es hace ocho días, así que la fecha de hoy de verdad es futura. */
    @Test void laEmisionUsaLaFechaDelRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        LocalDate hoyDelBackend = LocalDate.now(clock);

        ResponseEntity<Map> deHoyDelBackend = http.postForEntity("/v1/facturas", new HttpEntity<>(factura(hoyDelBackend), conKey(apiKey)), Map.class);
        assertThat(deHoyDelBackend.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) deHoyDelBackend.getBody().get("datos");
        assertThat(datos.get("estado_documento")).isEqualTo("FIRMADO");
        assertThat(datos.get("fecha_emision")).isEqualTo(hoyDelBackend.toString());

        ResponseEntity<Map> deHoyDeVerdad = http.postForEntity("/v1/facturas",
                new HttpEntity<>(factura(hoyDelBackend.plusDays(8)), conKey(apiKey)), Map.class);
        assertThat(deHoyDeVerdad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(deHoyDeVerdad.getBody().get("codigo")).isEqualTo("FECHA_INVALIDA");
    }

    /** Una empresa pasada a PRODUCCION con la aplicación ya arrancada: no emite, y no gasta número. */
    @Test void unaEmpresaEnProduccionNoEmiteConElRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION'");

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(factura(LocalDate.now(clock)), conKey(apiKey)), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().get("codigo")).isEqualTo("RELOJ_DESPLAZADO");
        assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = 'F001'", Long.class)).as("no consumió numeración").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documento", Long.class)).isZero();
    }

    /** Lo ya firmado de una empresa que pasó a PRODUCCION tampoco sale hacia SUNAT: la pasarela de verdad está protegida, no solo la emisión. */
    @Test void unaEmpresaEnProduccionNoEnviaNadaASunatConElRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        ResponseEntity<Map> emitida = http.postForEntity("/v1/facturas", new HttpEntity<>(factura(LocalDate.now(clock)), conKey(apiKey)), Map.class);
        String id = (String) ((Map<?, ?>) emitida.getBody().get("datos")).get("id");
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION'");

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas/" + id + "/enviar", new HttpEntity<>(null, conKey(apiKey)), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().get("codigo")).isEqualTo("RELOJ_DESPLAZADO");
        assertThat(jdbc.queryForObject("SELECT estado FROM documento WHERE id = ?::uuid", String.class, id)).as("sigue firmado, no se intentó el envío").isEqualTo("FIRMADO");
    }

    /** La comprobación de arranque, con el SQL de verdad: sin empresas en PRODUCCION pasa; con una, aborta. */
    @Test void elArranqueAbortaSiHayEmpresasEnProduccion() throws Exception {
        EmpresaDePrueba.provisionar(http);
        assertThatCode(() -> comprobacionDeArranque.run(null)).doesNotThrowAnyException();

        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION'");

        assertThatThrownBy(() -> comprobacionDeArranque.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELOJ_DESPLAZAMIENTO").hasMessageContaining("PRODUCCION");
    }
}
