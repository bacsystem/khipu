package pe.factura.bootstrap;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.adapters.sunat.ZipUtil;
import pe.factura.application.port.in.GestionarWebhooksUseCase;
import pe.factura.application.port.in.GestionarWebhooksUseCase.Creado;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.domain.webhook.FirmaWebhook;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static com.github.tomakehurst.wiremock.client.WireMock.okXml;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los webhooks (#19) de extremo a extremo: una factura que SUNAT acepta (WireMock) dispara una entrega firmada a un receptor real (un servidor HTTP del test), y se verifica
 * la firma como lo haría el integrador. Cubre además lo que importa en producción: que un receptor caído reintente y no pierda el evento, que la API de gestión exija la
 * sesión del portal y no muestre el secreto, y que una URL que apunta a la red interna se rechace cuando no se permiten redes privadas.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@WireMockTest(httpPort = 18097)
@AutoConfigureTestRestTemplate
class WebhooksE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16-alpine");

    record Recibido(Map<String, String> cabeceras, String cuerpo) {}

    static final List<Recibido> RECIBIDOS = new CopyOnWriteArrayList<>();
    static final AtomicInteger RESPUESTA = new AtomicInteger(200);
    static final HttpServer RECEPTOR = receptor();

    static HttpServer receptor() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/khipu", ex -> {
                Map<String, String> h = new LinkedHashMap<>();
                ex.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.get(0)));
                RECIBIDOS.add(new Recibido(h, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                ex.sendResponseHeaders(RESPUESTA.get(), -1);
                ex.close();
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String urlDelReceptor() { return "http://127.0.0.1:" + RECEPTOR.getAddress().getPort() + "/khipu"; }

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("app.sunat.beta-url", () -> "http://localhost:18097/billService");
        r.add("app.sunat.timeout-seconds", () -> "2");
        r.add("app.webhooks.permitir-http", () -> "true");
        r.add("app.webhooks.permitir-redes-privadas", () -> "true");
        r.add("app.webhooks.intervalo-ms", () -> "200");
        r.add("app.webhooks.inicial-ms", () -> "0");
        r.add("app.envio-al-cliente.intervalo-ms", () -> "200");
        r.add("app.envio-al-cliente.inicial-ms", () -> "0");
    }

    /** #325: el correo de mentira (el SMTP simulado de las demás pruebas E2E): anota lo que sale con sus adjuntos y puede decir que no entrega (sin SMTP). */
    record CorreoEnviado(String para, String asunto, List<String> adjuntos) {}
    static final List<CorreoEnviado> CORREOS = new CopyOnWriteArrayList<>();
    static final java.util.concurrent.atomic.AtomicBoolean SMTP = new java.util.concurrent.atomic.AtomicBoolean(true);

    @org.springframework.boot.test.context.TestConfiguration
    static class CorreoDePrueba {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary CorreoSender correoQueAnota() {
            return new CorreoSender() {
                public void enviar(String para, String asunto, String cuerpo) { enviar(para, asunto, cuerpo, List.of()); }
                public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) {
                    CORREOS.add(new CorreoEnviado(para, asunto, adjuntos.stream().map(Adjunto::nombre).toList()));
                }
                public boolean entregaDeVerdad() { return SMTP.get(); }
            };
        }
    }

    @AfterAll static void cerrar() { RECEPTOR.stop(0); }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired GestionarWebhooksUseCase webhooks;

    static final String CDR_OK = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
          xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
          xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
          <cbc:ID>x</cbc:ID>
          <cac:DocumentResponse><cac:Response><cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>La Factura numero F001-1, ha sido aceptada</cbc:Description></cac:Response></cac:DocumentResponse>
        </ar:ApplicationResponse>""";

    static String soapOk(String nombre) {
        byte[] zip = ZipUtil.comprimir("R-" + nombre + ".xml", CDR_OK.getBytes());
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendBillResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><applicationResponse>"
                + Base64.getEncoder().encodeToString(zip) + "</applicationResponse></ns2:sendBillResponse></soap-env:Body></soap-env:Envelope>";
    }

    static final String FACTURA = """
        {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN",
         "cliente":{"tipo_doc":"6","num_doc":"20601234565","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
         "items":[{"codigo":"P001","descripcion":"Laptop","unidad":"NIU","cantidad":1,"precio_unitario":2360.00,"tipo_afectacion_igv":"10"}]}
        """.formatted(java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")));

    @BeforeEach void limpiar() {
        RECIBIDOS.clear();
        RESPUESTA.set(200);
        CORREOS.clear();
        SMTP.set(true);
        jdbc.update("TRUNCATE webhook_entrega, webhook_endpoint, outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant CASCADE");
    }

    HttpHeaders conApiKey(String apiKey) { HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    UUID tenantDe(String ruc) { return jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = ?", UUID.class, ruc); }

    static void esperar(String que, BooleanSupplier condicion) {
        long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < limite) {
            if (condicion.getAsBoolean()) return;
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }
        throw new AssertionError("No ocurrió a tiempo: " + que);
    }

    /** Lo que hace el integrador: recalcular el HMAC con su copia del secreto sobre «t.cuerpo» y compararlo con el de la cabecera. */
    static boolean firmaValida(String secreto, Recibido r) {
        String cab = r.cabeceras().get("x-khipu-signature");
        long t = Long.parseLong(cab.substring(cab.indexOf("t=") + 2, cab.indexOf(',')));
        return cab.equals("t=" + t + ",v1=" + FirmaWebhook.hmacHex(secreto, t, r.cuerpo()));
    }

    @Test void unaFacturaAceptadaLlegaFirmadaAlReceptorYQuedaEnElLog(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = EmpresaDePrueba.provisionar(http);
        Creado creado = webhooks.crear(tenantDe("20100066603"), urlDelReceptor(), "ERP", List.of("comprobante.aceptado", "comprobante.rechazado"));

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, conApiKey(apiKey)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) ((Map<?, ?>) r.getBody().get("datos")).get("id");

        esperar("la entrega del comprobante aceptado", () -> RECIBIDOS.size() >= 1);
        Recibido recibido = RECIBIDOS.get(0);
        assertThat(RECIBIDOS).as("una sola entrega: FIRMADO y ENVIADO no se publican").hasSize(1);
        assertThat(recibido.cabeceras()).containsEntry("x-khipu-event", "comprobante.aceptado").containsEntry("content-type", "application/json; charset=utf-8");
        assertThat(recibido.cabeceras().get("x-khipu-delivery")).isNotBlank();
        assertThat(firmaValida(creado.secreto(), recibido)).as("la firma verifica con el secreto entregado al crear").isTrue();
        assertThat(firmaValida("whsec_otro", recibido)).as("y no con otro").isFalse();
        Map<String, Object> cuerpo = new com.fasterxml.jackson.databind.ObjectMapper().readValue(recibido.cuerpo(), Map.class);
        Map<String, Object> datos = (Map<String, Object>) cuerpo.get("datos");
        assertThat(cuerpo).containsEntry("evento", "comprobante.aceptado");
        assertThat(datos).containsEntry("id", id).containsEntry("serie", "F001").containsEntry("numero", 1).containsEntry("estado", "ACEPTADO").containsEntry("tipo", "01");
        assertThat((Map<String, Object>) datos.get("cdr")).containsEntry("codigo", "0");

        esperar("la entrega marcada exitosa", () -> jdbc.queryForObject("SELECT count(*) FROM webhook_entrega WHERE estado = 'EXITOSA'", Integer.class) == 1);
        ResponseEntity<Map> log = http.exchange("/v1/webhooks/" + creado.endpoint().id() + "/entregas", HttpMethod.GET, new HttpEntity<>(conApiKey(apiKey)), Map.class);
        assertThat(log.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(log.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        Map<String, Object> entrega = ((List<Map<String, Object>>) log.getBody().get("datos")).get(0);
        assertThat(entrega).containsEntry("estado", "EXITOSA").containsEntry("evento", "comprobante.aceptado").containsEntry("ultimo_http", 200).containsEntry("comprobante_id", id);
    }

    @Test void unReceptorCaidoSeReintentaYNoPierdeElEvento(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = EmpresaDePrueba.provisionar(http);
        Creado creado = webhooks.crear(tenantDe("20100066603"), urlDelReceptor(), null, List.of("*"));
        RESPUESTA.set(503);

        http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, conApiKey(apiKey)), Map.class);

        esperar("el primer intento fallido", () -> jdbc.queryForObject("SELECT count(*) FROM webhook_entrega WHERE intentos = 1 AND estado = 'PENDIENTE' AND ultimo_http = 503", Integer.class) == 1);
        assertThat(jdbc.queryForObject("SELECT siguiente_intento > now() + interval '30 seconds' FROM webhook_entrega", Boolean.class)).as("espera antes de reintentar").isTrue();
        assertThat(jdbc.queryForObject("SELECT fallos_consecutivos FROM webhook_endpoint WHERE id = ?", Integer.class, creado.endpoint().id())).isEqualTo(1);

        // El receptor se recupera y llega la hora del reintento.
        RESPUESTA.set(200);
        jdbc.update("UPDATE webhook_entrega SET siguiente_intento = now()");
        esperar("la entrega exitosa tras el reintento", () -> jdbc.queryForObject("SELECT count(*) FROM webhook_entrega WHERE estado = 'EXITOSA' AND intentos = 2", Integer.class) == 1);
        assertThat(RECIBIDOS).hasSize(2);
        assertThat(RECIBIDOS.get(0).cuerpo()).as("el reintento manda el mismo cuerpo").isEqualTo(RECIBIDOS.get(1).cuerpo());
        assertThat(RECIBIDOS.get(0).cabeceras().get("x-khipu-delivery")).isEqualTo(RECIBIDOS.get(1).cabeceras().get("x-khipu-delivery"));
        assertThat(firmaValida(creado.secreto(), RECIBIDOS.get(1))).isTrue();
        assertThat(jdbc.queryForObject("SELECT fallos_consecutivos FROM webhook_endpoint WHERE id = ?", Integer.class, creado.endpoint().id())).isZero();
    }

    @Test void laEntregaDePruebaYElReenvioSeVenEnElLog(WireMockRuntimeInfo wm) throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        UUID tenant = tenantDe("20100066603");
        Creado creado = webhooks.crear(tenant, urlDelReceptor(), null, List.of("*"));

        UUID entrega = webhooks.probar(tenant, creado.endpoint().id());

        esperar("la entrega de prueba", () -> RECIBIDOS.size() == 1);
        assertThat(RECIBIDOS.get(0).cabeceras()).containsEntry("x-khipu-event", "webhook.prueba").containsEntry("x-khipu-delivery", entrega.toString());
        assertThat(firmaValida(creado.secreto(), RECIBIDOS.get(0))).isTrue();
        esperar("cerrada", () -> jdbc.queryForObject("SELECT estado FROM webhook_entrega WHERE id = ?", String.class, entrega).equals("EXITOSA"));

        webhooks.reenviar(tenant, entrega);
        esperar("el reenvío", () -> RECIBIDOS.size() == 2);
        assertThat(RECIBIDOS.get(1).cuerpo()).isEqualTo(RECIBIDOS.get(0).cuerpo());
    }

    @Test void despuesDeRotarElSecretoLaFirmaVerificaConElNuevoYNoConElAnterior(WireMockRuntimeInfo wm) throws Exception {
        EmpresaDePrueba.provisionar(http);
        UUID tenant = tenantDe("20100066603");
        Creado creado = webhooks.crear(tenant, urlDelReceptor(), null, List.of("*"));
        String nuevo = webhooks.rotarSecreto(tenant, creado.endpoint().id());

        webhooks.probar(tenant, creado.endpoint().id());

        esperar("la entrega", () -> RECIBIDOS.size() == 1);
        assertThat(firmaValida(nuevo, RECIBIDOS.get(0))).isTrue();
        assertThat(firmaValida(creado.secreto(), RECIBIDOS.get(0))).isFalse();
    }

    @Test void conDiezFallosSeguidosElEndpointSePausaYAlReactivarloLasPendientesSalen(WireMockRuntimeInfo wm) throws Exception {
        EmpresaDePrueba.provisionar(http);
        UUID tenant = tenantDe("20100066603");
        Creado creado = webhooks.crear(tenant, urlDelReceptor(), null, List.of("*"));
        RESPUESTA.set(500);
        jdbc.update("UPDATE webhook_endpoint SET fallos_consecutivos = 9 WHERE id = ?", creado.endpoint().id());

        webhooks.probar(tenant, creado.endpoint().id());

        esperar("la pausa", () -> !jdbc.queryForObject("SELECT activo FROM webhook_endpoint WHERE id = ?", Boolean.class, creado.endpoint().id()));
        assertThat(jdbc.queryForObject("SELECT pausado_motivo FROM webhook_endpoint WHERE id = ?", String.class, creado.endpoint().id())).contains("10 entregas fallidas seguidas");
        int intentos = RECIBIDOS.size();
        Thread.sleep(800);
        assertThat(RECIBIDOS).as("un endpoint pausado no recibe más intentos").hasSize(intentos);

        RESPUESTA.set(200);
        webhooks.actualizar(tenant, creado.endpoint().id(), null, null, null, true);
        // Sin tocar siguiente_intento a mano: reactivar saca las pendientes ya, aunque su reintento estuviera a minutos u horas.
        esperar("la entrega pendiente al reactivar", () -> jdbc.queryForObject("SELECT count(*) FROM webhook_entrega WHERE estado = 'EXITOSA'", Integer.class) == 1);
        assertThat(jdbc.queryForObject("SELECT fallos_consecutivos FROM webhook_endpoint WHERE id = ?", Integer.class, creado.endpoint().id())).isZero();
    }

    @Test void laApiDeGestionExigeSesionYNuncaMuestraElSecretoEnElListado(WireMockRuntimeInfo wm) throws Exception {
        String apiKey = EmpresaDePrueba.provisionar(http);
        Creado creado = webhooks.crear(tenantDe("20100066603"), urlDelReceptor(), "ERP", List.of("*"));

        ResponseEntity<Map> crear = http.postForEntity("/v1/webhooks", new HttpEntity<>("{\"url\":\"" + urlDelReceptor() + "\",\"eventos\":[\"*\"]}", conApiKey(apiKey)), Map.class);
        assertThat(crear.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(crear.getBody().get("codigo")).isEqualTo("REQUIERE_SESION");

        ResponseEntity<String> lista = http.exchange("/v1/webhooks", HttpMethod.GET, new HttpEntity<>(conApiKey(apiKey)), String.class);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lista.getBody()).contains(creado.endpoint().id().toString()).doesNotContain("whsec_").doesNotContain("secreto");
        assertThat(http.exchange("/v1/webhooks", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class).getStatusCode()).as("sin credenciales").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- #325: envío automático del comprobante aceptado a su cliente ------------------------------------------------------------

    static final String FACTURA_CON_CORREO = FACTURA.replace("\"direccion\":\"AV. LIMA 1\"}", "\"direccion\":\"AV. LIMA 1\",\"email\":\"Compras@Cliente.pe\"}");

    ResponseEntity<Map> preferencia(String apiKey, boolean activar) {
        return http.exchange("/v1/empresa/envio-al-cliente", HttpMethod.PUT, new HttpEntity<>("{\"enviar_automaticamente\":" + activar + "}", conApiKey(apiKey)), Map.class);
    }

    @Test void conElEnvioActivadoLaFacturaAceptadaLlegaAlCorreoDelClienteUnaSolaVez(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = EmpresaDePrueba.provisionar(http);
        ResponseEntity<Map> activada = preferencia(apiKey, true);
        assertThat(activada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map<String, Object>) activada.getBody().get("datos")).containsEntry("enviar_automaticamente", true).containsEntry("correo_disponible", true);
        assertThat(FACTURA_CON_CORREO).as("el cuerpo de la prueba lleva el correo").contains("\"email\"");

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA_CON_CORREO, conApiKey(apiKey)), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> datos = (Map<String, Object>) r.getBody().get("datos");
        assertThat((Map<String, Object>) datos.get("receptor")).containsEntry("email", "compras@cliente.pe");
        String id = (String) datos.get("id");

        esperar("el correo al cliente", () -> CORREOS.size() >= 1);
        esperar("el envío anotado", () -> "ENVIADO".equals(jdbc.queryForObject("SELECT estado FROM envio_al_cliente WHERE documento_id = ?::uuid", String.class, id)));
        CorreoEnviado correo = CORREOS.get(0);
        assertThat(correo.para()).isEqualTo("compras@cliente.pe");
        assertThat(correo.asunto()).isEqualTo("Factura F001-1 - EMPRESA DE PRUEBA S.A.C.");
        assertThat(correo.adjuntos()).containsExactly("20100066603-01-F001-1.pdf", "20100066603-01-F001-1.xml", "R-20100066603-01-F001-1.zip");

        ResponseEntity<Map> detalle = http.exchange("/v1/facturas/" + id, HttpMethod.GET, new HttpEntity<>(conApiKey(apiKey)), Map.class);
        Map<String, Object> envio = (Map<String, Object>) ((Map<String, Object>) detalle.getBody().get("datos")).get("envio_al_cliente");
        assertThat(envio).containsEntry("destinatario", "compras@cliente.pe").containsEntry("estado", "ENVIADO").containsEntry("intentos", 1);
        assertThat(envio.get("enviado_en")).isNotNull();

        Thread.sleep(800);
        assertThat(CORREOS).as("una sola vez, aunque el worker siga pasando").hasSize(1);
    }

    @Test void sinLaPreferenciaNoSaleNadaYSinSmtpNoSePuedeActivar(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = EmpresaDePrueba.provisionar(http);
        ResponseEntity<Map> inicial = http.exchange("/v1/empresa/envio-al-cliente", HttpMethod.GET, new HttpEntity<>(conApiKey(apiKey)), Map.class);
        assertThat((Map<String, Object>) inicial.getBody().get("datos")).as("apagada por defecto").containsEntry("enviar_automaticamente", false);

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA_CON_CORREO, conApiKey(apiKey)), Map.class);
        assertThat(((Map<?, ?>) r.getBody().get("datos")).get("estado_documento")).isEqualTo("ACEPTADO");
        Thread.sleep(800);
        assertThat(CORREOS).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM envio_al_cliente", Integer.class)).isZero();

        SMTP.set(false);
        ResponseEntity<Map> sinSmtp = preferencia(apiKey, true);
        assertThat(sinSmtp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(sinSmtp.getBody().get("codigo")).isEqualTo("CORREO_NO_CONFIGURADO");
        assertThat(preferencia(apiKey, false).getStatusCode()).as("desactivar siempre se puede").isEqualTo(HttpStatus.OK);
    }

    /** Activado con SMTP, y el SMTP desaparece antes del envío: lo pendiente queda OMITIDO con el motivo, no se da por enviado. */
    @Test void siElServidorSeQuedaSinSmtpElEnvioPendienteSeOmite(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = EmpresaDePrueba.provisionar(http);
        assertThat(preferencia(apiKey, true).getStatusCode()).isEqualTo(HttpStatus.OK);
        SMTP.set(false);

        String id = (String) ((Map<?, ?>) http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA_CON_CORREO, conApiKey(apiKey)), Map.class).getBody().get("datos")).get("id");

        esperar("el envío omitido", () -> "OMITIDO".equals(jdbc.query("SELECT estado FROM envio_al_cliente WHERE documento_id = ?::uuid", (rs, i) -> rs.getString(1), id).stream().findFirst().orElse(null)));
        assertThat(jdbc.queryForObject("SELECT ultimo_error FROM envio_al_cliente WHERE documento_id = ?::uuid", String.class, id)).contains("no tiene correo configurado");
        assertThat(CORREOS).isEmpty();
    }

    @Test void elSecretoQuedaCifradoEnLaBase(WireMockRuntimeInfo wm) throws Exception {
        EmpresaDePrueba.provisionar(http);
        Creado creado = webhooks.crear(tenantDe("20100066603"), urlDelReceptor(), null, List.of("*"));

        byte[] guardado = jdbc.queryForObject("SELECT secreto_enc FROM webhook_endpoint WHERE id = ?", byte[].class, creado.endpoint().id());

        assertThat(new String(guardado, StandardCharsets.ISO_8859_1)).doesNotContain(creado.secreto());
        assertThat(guardado.length).isGreaterThan(creado.secreto().length());
    }
}
