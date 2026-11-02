package pe.factura.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import pe.factura.application.port.in.ControlarPlazoEnvioUseCase;
import pe.factura.application.port.in.InformarEnResumenUseCase;
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.SunatConsultaGateway;
import pe.factura.domain.DomainException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureTestRestTemplate
class RelojDesplazadoE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired ControlarPlazoEnvioUseCase plazos;
    @Autowired InformarEnResumenUseCase resumenes;
    @Autowired SunatConsultaGateway consultas;
    @Autowired SunatBillingGateway envios;
    @Autowired @Qualifier("relojDesplazadoAlArrancar") ApplicationRunner comprobacionDeArranque;

    @BeforeEach void limpiar() { jdbc.update("TRUNCATE tenant CASCADE"); }

    static String factura(LocalDate fecha) {
        return """
            {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN","enviar_automatico":false,
             "cliente":{"tipo_doc":"6","num_doc":"20601234565","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
             "items":[{"codigo":"P001","descripcion":"Laptop","unidad":"NIU","cantidad":1,"precio_unitario":2360.00,"tipo_afectacion_igv":"10"}]}
            """.formatted(fecha);
    }

    static String boleta(LocalDate fecha) {
        return """
            {"serie":"B001","fecha_emision":"%s","moneda":"PEN","enviar_automatico":false,
             "cliente":{"tipo_doc":"1","num_doc":"12345678","razon_social":"CLIENTE DE PRUEBA"},
             "items":[{"descripcion":"Producto de prueba","unidad":"NIU","cantidad":1,"precio_unitario":118.00,"tipo_afectacion_igv":"10"}]}
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
        assertThat(deHoyDeVerdad.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(deHoyDeVerdad.getBody().get("codigo")).isEqualTo("FECHA_INVALIDA");
    }

    /** Una empresa pasada a PRODUCCION con la aplicación ya arrancada: no emite, y no gasta número. */
    @Test void unaEmpresaEnProduccionNoEmiteConElRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION'");

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(factura(LocalDate.now(clock)), conKey(apiKey)), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
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

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(r.getBody().get("codigo")).isEqualTo("RELOJ_DESPLAZADO");
        assertThat(jdbc.queryForObject("SELECT estado FROM documento WHERE id = ?::uuid", String.class, id)).as("sigue firmado, no se intentó el envío").isEqualTo("FIRMADO");
    }

    /** Una factura FIRMADO cuya fecha de emisión ya pasó el plazo de envío para el reloj de la aplicación: lo que el envío o el barrido cerrarían como FUERA_DE_PLAZO (terminal). */
    private String facturaVencida(String apiKey) {
        ResponseEntity<Map> emitida = http.postForEntity("/v1/facturas", new HttpEntity<>(factura(LocalDate.now(clock)), conKey(apiKey)), Map.class);
        String id = (String) ((Map<?, ?>) emitida.getBody().get("datos")).get("id");
        jdbc.update("UPDATE documento SET fecha_emision = fecha_emision - 10 WHERE id = ?::uuid", id);
        return id;
    }

    private String estado(String id) { return jdbc.queryForObject("SELECT estado FROM documento WHERE id = ?::uuid", String.class, id); }

    /**
     * H2: el envío mira el plazo con el reloj ANTES de llegar a la pasarela, y cerrar por plazo es un estado terminal. Con el reloj movido, una empresa en PRODUCCION no puede
     * quedarse con un comprobante real cerrado por una fecha falsa.
     */
    @Test void unaEmpresaEnProduccionNoCierraPorPlazoUnEnvioConElRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        String id = facturaVencida(apiKey);
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION'");

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas/" + id + "/enviar", new HttpEntity<>(null, conKey(apiKey)), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(r.getBody().get("codigo")).isEqualTo("RELOJ_DESPLAZADO");
        assertThat(estado(id)).as("no se cerró por plazo con una fecha falsa").isEqualTo("FIRMADO");
    }

    /** El control del anterior: una empresa de BETA sí se cierra por plazo (el reloj movido es justamente para probarlo), así que el test de arriba distingue. */
    @Test void unaEmpresaDeBetaSiSeCierraPorPlazoConElRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        String id = facturaVencida(apiKey);

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas/" + id + "/enviar", new HttpEntity<>(null, conKey(apiKey)), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().get("codigo")).isEqualTo("FUERA_DE_PLAZO");
        assertThat(estado(id)).isEqualTo("FUERA_DE_PLAZO");
    }

    /** H3: el barrido periódico cierra por plazo lo de TODAS las empresas con el reloj de la aplicación; con una empresa en PRODUCCION no corre. */
    @Test void elBarridoDePlazosNoCierraNadaSiHayUnaEmpresaEnProduccionConElRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        String id = facturaVencida(apiKey);
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION'");

        assertThat(plazos.marcarVencidos()).isEmpty();
        assertThat(estado(id)).isEqualTo("FIRMADO");
    }

    @Test void elBarridoDePlazosSiCierraLoDeUnaEmpresaDeBetaConElRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        String id = facturaVencida(apiKey);

        assertThat(plazos.marcarVencidos()).hasSize(1);
        assertThat(estado(id)).isEqualTo("FUERA_DE_PLAZO");
    }

    /** H1: dar de baja numera y firma un RA con la fecha del reloj movido; para una empresa en PRODUCCION ni se empieza. */
    @Test void unaEmpresaEnProduccionNoDaDeBajaNadaConElRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        ResponseEntity<Map> emitida = http.postForEntity("/v1/facturas", new HttpEntity<>(factura(LocalDate.now(clock)), conKey(apiKey)), Map.class);
        String id = (String) ((Map<?, ?>) emitida.getBody().get("datos")).get("id");
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION'");

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas/" + id + "/baja", new HttpEntity<>("{\"motivo\":\"Error en el RUC del cliente\"}", conKey(apiKey)), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(r.getBody().get("codigo")).isEqualTo("RELOJ_DESPLAZADO");
    }

    /**
     * H1: informar una boleta en un resumen diario genera, firma y guarda un RC con la fecha del reloj movido y gasta su correlativo; la pasarela lo frenaba solo al
     * enviar (y el error se guardaba como ERROR_ENVIO con su fila de outbox, para reenviarlo con la hora real). Para una empresa en PRODUCCION no se crea nada.
     */
    @Test void unaEmpresaEnProduccionNoGeneraUnResumenDiarioConElRelojDesplazado() throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"03\",\"serie\":\"B001\",\"correlativo_inicial\":0}", conKey(apiKey)), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> emitida = http.postForEntity("/v1/facturas", new HttpEntity<>(boleta(LocalDate.now(clock)), conKey(apiKey)), Map.class);
        assertThat(emitida.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) ((Map<?, ?>) emitida.getBody().get("datos")).get("id");
        jdbc.update("UPDATE documento SET fecha_emision = fecha_emision - 6 WHERE id = ?::uuid", id);   // pasó el envío individual (5 días): solo va en un resumen
        UUID tenantId = jdbc.queryForObject("SELECT id FROM tenant", UUID.class);
        jdbc.update("UPDATE tenant SET entorno = 'PRODUCCION'");

        assertThatThrownBy(() -> resumenes.informar(tenantId, UUID.fromString(id)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.codigo()).isEqualTo("RELOJ_DESPLAZADO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comunicacion_baja", Long.class)).as("no se numeró ni se guardó ningún RC").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox", Long.class)).as("ni quedó nada para reenviar con la hora real").isZero();
    }

    /**
     * H4: las pasarelas de SUNAT de la aplicación (envío y consulta) son las protegidas. No se llama a la de consulta con una empresa de PRODUCCION para verlo: sin la
     * protección esa llamada saldría de verdad hacia SUNAT, y un test no puede hacer eso.
     */
    @Test void lasPasarelasDeSunatDeLaAplicacionSonLasProtegidasConElRelojDesplazado() {
        assertThat(consultas.getClass().getEnclosingClass()).isEqualTo(RelojDeLaAplicacion.class);
        assertThat(envios.getClass().getEnclosingClass()).isEqualTo(RelojDeLaAplicacion.class);
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
