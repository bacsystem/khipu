package pe.factura.bootstrap;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.adapters.scheduler.OutboxWorker;
import pe.factura.adapters.sunat.ZipUtil;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@WireMockTest(httpPort = 18089)
class FacturaE2ETest {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("app.sunat.beta-url", () -> "http://localhost:18089/billService");
        r.add("app.sunat.timeout-seconds", () -> "2");
        // #318: la API REST de SUNAT (token y consulta integrada de validez) en el mismo WireMock.
        r.add("app.sunat-api.seguridad-beta-url", () -> "http://localhost:18089/api-seguridad");
        r.add("app.sunat-api.consulta-beta-url", () -> "http://localhost:18089/api-sunat");
        r.add("app.sunat-api.timeout-seconds", () -> "2");
    }

    @Autowired TestRestTemplate http;

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

    String provisionarTenant() throws Exception { return EmpresaDePrueba.provisionar(http); }

    static final String FACTURA = """
        {"serie":"F001","fecha_emision":"%s","tipo_operacion":"0101","moneda":"PEN",
         "cliente":{"tipo_doc":"6","num_doc":"20601234565","razon_social":"CLIENTE SAC","direccion":"AV. LIMA 1"},
         "items":[{"codigo":"P001","descripcion":"Laptop","unidad":"NIU","cantidad":1,"precio_unitario":2360.00,"tipo_afectacion_igv":"10"}]}
        """.formatted(java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")));

    @Test void facturaAceptadaDeExtremoAExtremo(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        assertThat(datos.get("estado_documento")).isEqualTo("ACEPTADO");
        assertThat(datos.get("numero")).isEqualTo(1);
        assertThat((String) datos.get("hash")).isNotBlank();
        assertThat(((Map<?, ?>) datos.get("cdr")).get("codigo")).isEqualTo("0");

        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("<fileName>20100066603-01-F001-1.zip</fileName>")));

        String id = (String) datos.get("id");
        ResponseEntity<byte[]> xml = http.exchange("/v1/facturas/" + id + "/xml", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(xml.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(xml.getBody())).contains("<ds:Signature").contains("<cbc:ID>F001-1</cbc:ID>");
        ResponseEntity<byte[]> cdr = http.exchange("/v1/facturas/" + id + "/cdr", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(cdr.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(ZipUtil.extraerPrimero(cdr.getBody(), ".xml"))).contains("ha sido aceptada");
    }

    /** #321: la exportación con la pila real: Postgres, filtros del listado, CSV con BOM y XLSX, y la misma puerta de credenciales. */
    @Test void exportaLosComprobantesEnCsvYXlsx(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();

        ResponseEntity<byte[]> csv = http.exchange("/v1/facturas/exportacion?desde=" + hoy + "&cliente=cliente", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=\"comprobantes-" + hoy + "_" + hoy + ".csv\"");
        String texto = new String(csv.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(texto).startsWith("﻿tipo,serie,numero,fecha_emision,")
                .contains("\r\n01,F001,1," + hoy + ",6,20601234565,CLIENTE SAC,PEN,2000.00,0.00,0.00,0.00,0.00,360.00,0.00,2360.00,ACEPTADO,\r\n");

        ResponseEntity<byte[]> nadie = http.exchange("/v1/facturas/exportacion?cliente=nadie", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(new String(nadie.getBody(), java.nio.charset.StandardCharsets.UTF_8).split("\r\n")).as("sin resultados, solo la cabecera").hasSize(1);

        ResponseEntity<byte[]> xlsx = http.exchange("/v1/facturas/exportacion?formato=xlsx", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(xlsx.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xlsx.getHeaders().getContentType().toString()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(xlsx.getBody()).startsWith(new byte[]{'P', 'K'});

        HttpHeaders sin = new HttpHeaders();
        assertThat(http.exchange("/v1/facturas/exportacion", HttpMethod.GET, new HttpEntity<>(sin), byte[].class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** #405: `/calcular` con la pila real: los mismos importes que la emisión, sin consumir correlativo ni llamar a SUNAT, y con los mismos errores de dominio. */
    @Test void calcularDaLosImportesDeLaEmisionSinNumerarNiEnviar(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> calculado = http.postForEntity("/v1/facturas/calcular", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(calculado.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> datos = (Map<?, ?>) calculado.getBody().get("datos");
        assertThat(datos.get("numero")).isNull();
        assertThat(((Map<?, ?>) datos.get("totales")).get("total")).isEqualTo(2360.0);
        verify(0, postRequestedFor(urlEqualTo("/billService")));

        // El rechazo de dominio es el de la emisión, y la emisión que sigue arranca en el correlativo 1 (calcular no consumió ninguno).
        ResponseEntity<Map> malo = http.postForEntity("/v1/facturas/calcular", new HttpEntity<>(FACTURA.replace("\"unidad\":\"NIU\"", "\"unidad\":\"XYZ\""), h), Map.class);
        assertThat(malo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(malo.getBody().get("codigo")).isEqualTo("ITEM_INVALIDO");
        ResponseEntity<Map> emitida = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(((Map<?, ?>) emitida.getBody().get("datos")).get("numero")).isEqualTo(1);
        assertThat(((Map<?, ?>) ((Map<?, ?>) emitida.getBody().get("datos")).get("totales")).get("total")).isEqualTo(2360.0);

        // Sin credencial, la misma puerta que la emisión.
        HttpHeaders sin = new HttpHeaders(); sin.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/facturas/calcular", new HttpEntity<>(FACTURA, sin), Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** #308: una unidad que no existe en el catálogo 03 vuelve 422 (2936) con el código recibido, antes de numerar: la siguiente factura es la 1. */
    @Test void unaUnidadFueraDelCatalogo03SeRechazaSinConsumirNumero(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> rara = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA.replace("\"unidad\":\"NIU\"", "\"unidad\":\"XYZ\""), h), Map.class);
        assertThat(rara.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rara.getBody().get("codigo")).isEqualTo("ITEM_INVALIDO");
        assertThat((String) rara.getBody().get("mensaje")).startsWith("2936 - ").contains("XYZ").contains("catálogo 03");

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) r.getBody().get("datos")).get("numero")).isEqualTo(1);
    }

    /**
     * #354: las reglas de la línea que SUNAT rechaza con el número ya consumido (ICBPER 2892/4320, NRUS 3240, precio 0 2369/2370, más de 999 líneas 2023) vuelven
     * 422 ITEM_INVALIDO con el código SUNAT en el mensaje, en boleta y en factura, antes de numerar: la siguiente de cada serie sigue siendo la 1.
     */
    @Test void lasReglasDeLaLineaSeRechazanSinConsumirNumero(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-03-B001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"03\",\"serie\":\"B001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();
        String boleta = """
            {"serie":"B001","fecha_emision":"%s","moneda":"PEN","tipo_operacion":"%s",
             "cliente":{"tipo_doc":"1","num_doc":"12345678","razon_social":"JUAN PEREZ"},"items":[%s]}
            """;
        String bolsas = "{\"descripcion\":\"Bolsa\",\"unidad\":\"%s\",\"cantidad\":%s,\"precio_unitario\":%s,\"tipo_afectacion_igv\":\"10\",\"icbper\":true}";
        String pan = "{\"descripcion\":\"Pan\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":%s,\"tipo_afectacion_igv\":\"10\"}";
        String mil = String.join(",", java.util.Collections.nCopies(1000, pan.formatted("1.18")));

        Map<String, String> boletas = new java.util.LinkedHashMap<>();
        boletas.put("2892", boleta.formatted(hoy, "0101", bolsas.formatted("NIU", "1.5", "0.60")));
        boletas.put("2892 ", boleta.formatted(hoy, "0101", bolsas.formatted("NIU", "100000", "0.60")));
        boletas.put("4320", boleta.formatted(hoy, "0101", bolsas.formatted("KGM", "2", "0.60")));
        boletas.put("3240", boleta.formatted(hoy, "0113", bolsas.formatted("NIU", "2", "0.60")));
        boletas.put("2370", boleta.formatted(hoy, "0101", bolsas.formatted("NIU", "2", "0.50")));
        boletas.put("2369", boleta.formatted(hoy, "0101", pan.formatted("0")));
        boletas.put("2023", boleta.formatted(hoy, "0101", mil));
        boletas.forEach((regla, cuerpo) -> {
            ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(cuerpo, h), Map.class);
            assertThat(r.getStatusCode()).as(regla).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(r.getBody().get("codigo")).as(regla).isEqualTo("ITEM_INVALIDO");
            assertThat((String) r.getBody().get("mensaje")).as(regla).startsWith(regla.strip() + " - ");
        });
        String factura = FACTURA.replace("\"precio_unitario\":2360.00", "\"precio_unitario\":0");
        ResponseEntity<Map> sinPrecio = http.postForEntity("/v1/facturas", new HttpEntity<>(factura, h), Map.class);
        assertThat(sinPrecio.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) sinPrecio.getBody().get("mensaje")).startsWith("2369 - ");
        // H4 (revisión de #354): el negativo ya no es un 422 VALIDACION de Bean Validation sino ITEM_INVALIDO del dominio, con la misma regla que el cero.
        ResponseEntity<Map> negativo = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA.replace("\"precio_unitario\":2360.00", "\"precio_unitario\":-5"), h), Map.class);
        assertThat(negativo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(negativo.getBody().get("codigo")).isEqualTo("ITEM_INVALIDO");
        assertThat((String) negativo.getBody().get("mensaje")).startsWith("2369 - ");
        ResponseEntity<Map> deMas = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA.replaceAll("\"items\":\\[.*\\]", "\"items\":[" + String.join(",", java.util.Collections.nCopies(1000,
                pan.formatted("1.18"))) + "]"), h), Map.class);
        assertThat(deMas.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) deMas.getBody().get("mensaje")).startsWith("2023 - ");

        // Nada de lo anterior numeró: la primera boleta válida con bolsas es la B001-1, y la primera factura válida la F001-1.
        ResponseEntity<Map> ok = http.postForEntity("/v1/facturas", new HttpEntity<>(boleta.formatted(hoy, "0101", bolsas.formatted("NIU", "2", "0.60")), h), Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) ok.getBody().get("datos")).get("numero")).isEqualTo(1);
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        ResponseEntity<Map> f = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(f.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) f.getBody().get("datos")).get("numero")).isEqualTo(1);
    }

    /**
     * #362 B9: SUNAT beta observó con la 4009 las boletas con un documento relacionado 06 o 07 (la factura sí los admite). Vuelven 422 DOCUMENTO_RELACIONADO_INVALIDO con la
     * regla al inicio, antes de numerar: la B001-1 sigue libre. 04, 05, 08, 09 y 99 pasan.
     */
    @Test void unaBoletaConDocumentoRelacionado06o07SeRechazaSinConsumirNumero(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-03-B001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"03\",\"serie\":\"B001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();
        String boleta = """
            {"serie":"B001","fecha_emision":"%s","moneda":"PEN","tipo_operacion":"0101",
             "cliente":{"tipo_doc":"1","num_doc":"12345678","razon_social":"JUAN PEREZ"},
             "documentos_relacionados":[{"tipo":"%s","numero":"F001-123"}],
             "items":[{"descripcion":"Pan","unidad":"NIU","cantidad":1,"precio_unitario":1.18,"tipo_afectacion_igv":"10"}]}
            """;
        for (String tipo : new String[]{"06", "07"}) {
            ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(boleta.formatted(hoy, tipo), h), Map.class);
            assertThat(r.getStatusCode()).as(tipo).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(r.getBody().get("codigo")).as(tipo).isEqualTo("DOCUMENTO_RELACIONADO_INVALIDO");
            assertThat((String) r.getBody().get("mensaje")).as(tipo).startsWith("4009 - ").contains(tipo);
        }
        ResponseEntity<Map> ok = http.postForEntity("/v1/facturas", new HttpEntity<>(boleta.formatted(hoy, "05"), h), Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) ok.getBody().get("datos")).get("numero")).isEqualTo(1);
        // La factura sigue admitiendo el 06 y el 07.
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        ResponseEntity<Map> f = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"documentos_relacionados\":[{\"tipo\":\"06\",\"numero\":\"F001-123\"}],"), h), Map.class);
        assertThat(f.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * #20: una boleta pasa por el mismo camino que la factura (numeración de su serie B, UBL, firma real, XSD, sendBill) y se envía sola. Un comprador sin
     * documento por encima de S/ 700 no se emite ni consume número.
     */
    @Test void boletaAceptadaDeExtremoAExtremo(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-03-B001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"03\",\"serie\":\"B001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String boleta = """
            {"serie":"B001","fecha_emision":"%s","moneda":"PEN",
             "cliente":{"tipo_doc":"-","num_doc":"-","razon_social":"CLIENTES VARIOS"},
             "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":%s,"precio_unitario":0.50,"tipo_afectacion_igv":"10"}]}
            """;
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();

        ResponseEntity<Map> caro = http.postForEntity("/v1/facturas", new HttpEntity<>(boleta.formatted(hoy, "1401"), h), Map.class);
        assertThat(caro.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(caro.getBody().get("codigo")).isEqualTo("RECEPTOR_INVALIDO");

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(boleta.formatted(hoy, "10"), h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        assertThat(datos.get("tipo")).isEqualTo("03");
        assertThat(datos.get("numero")).isEqualTo(1);
        assertThat(datos.get("estado_documento")).isEqualTo("ACEPTADO");
        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("<fileName>20100066603-03-B001-1.zip</fileName>")));

        String id = (String) datos.get("id");
        String xml = new String(http.exchange("/v1/facturas/" + id + "/xml", HttpMethod.GET, new HttpEntity<>(h), byte[].class).getBody());
        assertThat(xml).contains("<ds:Signature", "<cbc:ID>B001-1</cbc:ID>", ">03</cbc:InvoiceTypeCode>", "schemeID=\"-\"").doesNotContain("FormaPago");
        ResponseEntity<byte[]> pdf = http.exchange("/v1/facturas/" + id + "/pdf", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(pdf.getBody(), 0, 5)).isEqualTo("%PDF-");

        // Anularla: un resumen diario (RC) firmado de verdad y validado contra el XSD oficial, por sendSummary + getStatus; la boleta queda ANULADA.
        stubFor(post("/billService").withRequestBody(containing("sendSummary")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendSummaryResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><ticket>1789768174685</ticket></ns2:sendSummaryResponse></soap-env:Body></soap-env:Envelope>")));
        byte[] cdrRc = ZipUtil.comprimir("R-20100066603-RC.xml", CDR_OK.getBytes());
        stubFor(post("/billService").withRequestBody(containing("getStatus")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><status><statusCode>0</statusCode><content>"
                        + Base64.getEncoder().encodeToString(cdrRc) + "</content></status></ns2:getStatusResponse></soap-env:Body></soap-env:Envelope>")));
        ResponseEntity<Map> baja = http.postForEntity("/v1/facturas/" + id + "/baja", new HttpEntity<>("{\"motivo\":\"Se cobró dos veces\"}", h), Map.class);
        assertThat(baja.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> rc = (Map<?, ?>) baja.getBody().get("datos");
        assertThat((String) rc.get("identificador")).matches("RC-\\d{8}-1");
        assertThat(rc.get("estado")).isEqualTo("ACEPTADA");
        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("sendSummary")).withRequestBody(matching("(?s).*<fileName>20100066603-RC-\\d{8}-1\\.zip</fileName>.*")));
        Map<?, ?> anulada = (Map<?, ?>) http.exchange("/v1/facturas/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat(anulada.get("estado_documento")).isEqualTo("ANULADO");
    }

    /**
     * 274-H1: una boleta firmada hace 6 días (el envío individual llega al 5.º, #290) y nunca enviada ya no va sola con sendBill (1079): al enviarla, khipu la informa en un resumen diario con su
     * línea en estado 1, firmado de verdad y validado contra el XSD, y al aceptarse la boleta queda ACEPTADO.
     */
    @Test void boletaPasadaDelEnvioIndividualSeInformaEnElResumenDiario(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").withRequestBody(containing("sendSummary")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendSummaryResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><ticket>1789768174686</ticket></ns2:sendSummaryResponse></soap-env:Body></soap-env:Envelope>")));
        stubFor(post("/billService").withRequestBody(containing("getStatus")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><status><statusCode>0</statusCode><content>"
                        + Base64.getEncoder().encodeToString(ZipUtil.comprimir("R-20100066603-RC.xml", CDR_OK.getBytes())) + "</content></status></ns2:getStatusResponse></soap-env:Body></soap-env:Envelope>")));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"03\",\"serie\":\"B001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String haceSeis = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).minusDays(6).toString();
        ResponseEntity<Map> emitida = http.postForEntity("/v1/facturas", new HttpEntity<>("""
            {"serie":"B001","fecha_emision":"%s","moneda":"PEN","enviar_automatico":false,
             "cliente":{"tipo_doc":"1","num_doc":"12345678","razon_social":"JUAN PEREZ"},
             "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":10,"precio_unitario":0.50,"tipo_afectacion_igv":"10"}]}
            """.formatted(haceSeis), h), Map.class);
        assertThat(emitida.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) ((Map<?, ?>) emitida.getBody().get("datos")).get("id");

        ResponseEntity<Map> enviada = http.postForEntity("/v1/facturas/" + id + "/enviar", new HttpEntity<>(h), Map.class);

        assertThat(enviada.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(((Map<?, ?>) enviada.getBody().get("datos")).get("estado_documento")).isEqualTo("ACEPTADO");
        verify(0, postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("sendBill")));
        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("sendSummary")).withRequestBody(matching("(?s).*<fileName>20100066603-RC-\\d{8}-1\\.zip</fileName>.*")));
        Map<String, Object> resumen = (Map<String, Object>) ((java.util.List<?>) http.exchange("/v1/facturas/" + id + "/bajas", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos")).get(0);
        assertThat(resumen).containsEntry("condicion", "ALTA").containsEntry("estado", "ACEPTADA");
    }

    /**
     * #301: tres boletas de la misma fecha, firmadas sin enviar, se informan al enviar una en <b>un</b> solo resumen diario de tres líneas: un sendSummary, un getStatus, un CDR para
     * las tres, que quedan ACEPTADO y cada una lista su línea (misma RC, línea 1, 2 y 3). Con Postgres de verdad: la consulta que las recoge bloquea las filas con SKIP LOCKED.
     */
    @Test void tresBoletasDeLaMismaFechaSeInformanEnUnSoloResumenDiario(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").withRequestBody(containing("sendSummary")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendSummaryResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><ticket>1789768174688</ticket></ns2:sendSummaryResponse></soap-env:Body></soap-env:Envelope>")));
        stubFor(post("/billService").withRequestBody(containing("getStatus")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><status><statusCode>0</statusCode><content>"
                        + Base64.getEncoder().encodeToString(ZipUtil.comprimir("R-20100066603-RC.xml", CDR_OK.getBytes())) + "</content></status></ns2:getStatusResponse></soap-env:Body></soap-env:Envelope>")));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"03\",\"serie\":\"B001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String haceSeis = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).minusDays(6).toString();
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String dni : java.util.List.of("12345678", "87654321", "11223344")) {
            ResponseEntity<Map> emitida = http.postForEntity("/v1/facturas", new HttpEntity<>("""
                {"serie":"B001","fecha_emision":"%s","moneda":"PEN","enviar_automatico":false,
                 "cliente":{"tipo_doc":"1","num_doc":"%s","razon_social":"COMPRADOR %s"},
                 "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":10,"precio_unitario":0.50,"tipo_afectacion_igv":"10"}]}
                """.formatted(haceSeis, dni, dni), h), Map.class);
            assertThat(emitida.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            ids.add((String) ((Map<?, ?>) emitida.getBody().get("datos")).get("id"));
        }

        ResponseEntity<Map> enviada = http.postForEntity("/v1/facturas/" + ids.get(0) + "/enviar", new HttpEntity<>(h), Map.class);

        assertThat(enviada.getStatusCode().is2xxSuccessful()).isTrue();
        verify(0, postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("sendBill")));
        verify(1, postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("sendSummary")));
        verify(1, postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("getStatus")));
        for (int i = 0; i < ids.size(); i++) {
            Map<?, ?> boleta = (Map<?, ?>) http.exchange("/v1/facturas/" + ids.get(i), HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
            assertThat(boleta.get("estado_documento")).as("boleta " + (i + 1)).isEqualTo("ACEPTADO");
            java.util.List<?> bajas = (java.util.List<?>) http.exchange("/v1/facturas/" + ids.get(i) + "/bajas", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
            assertThat(bajas).hasSize(1);
            assertThat((Map<String, Object>) bajas.get(0)).containsEntry("condicion", "ALTA").containsEntry("estado", "ACEPTADA").containsEntry("ticket", "1789768174688")
                    .containsEntry("linea", i + 1).containsEntry("lineas", 3);
            assertThat((String) ((Map<?, ?>) bajas.get(0)).get("identificador")).matches("RC-\\d{8}-1");
        }
    }

    /**
     * #293: notas sobre una boleta de extremo a extremo. Sobre una boleta aceptada: NC total (copia la boleta), NC parcial y ND, cada una firmada, validada
     * contra su XSD y enviada sola con sendBill; la boleta las lista. Una NC por descuento (04) se rechaza sin consumir número, y la NC total se anula en un
     * resumen diario (RC), nunca en un RA.
     */
    @Test void notasSobreUnaBoletaDeExtremoAExtremo(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").withRequestBody(containing("sendBill")).willReturn(okXml(soapOk("20100066603-03-B001-1"))));
        stubFor(post("/billService").withRequestBody(containing("sendSummary")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendSummaryResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><ticket>1789768174687</ticket></ns2:sendSummaryResponse></soap-env:Body></soap-env:Envelope>")));
        stubFor(post("/billService").withRequestBody(containing("getStatus")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><status><statusCode>0</statusCode><content>"
                        + Base64.getEncoder().encodeToString(ZipUtil.comprimir("R-20100066603-RC.xml", CDR_OK.getBytes())) + "</content></status></ns2:getStatusResponse></soap-env:Body></soap-env:Envelope>")));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        for (String serie : new String[]{"{\"tipo\":\"03\",\"serie\":\"B001\"", "{\"tipo\":\"07\",\"serie\":\"BC01\"", "{\"tipo\":\"08\",\"serie\":\"BD01\""})
            assertThat(http.postForEntity("/v1/series", new HttpEntity<>(serie + ",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();
        ResponseEntity<Map> boleta = http.postForEntity("/v1/facturas", new HttpEntity<>("""
            {"serie":"B001","fecha_emision":"%s","moneda":"PEN",
             "cliente":{"tipo_doc":"-","num_doc":"-","razon_social":"CLIENTES VARIOS"},
             "items":[{"codigo":"P001","descripcion":"Pan","unidad":"NIU","cantidad":10,"precio_unitario":0.50,"tipo_afectacion_igv":"10"},
                      {"codigo":"P002","descripcion":"Leche","unidad":"NIU","cantidad":2,"precio_unitario":4.72,"tipo_afectacion_igv":"10"}]}
            """.formatted(hoy), h), Map.class);
        assertThat(boleta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> b = (Map<?, ?>) boleta.getBody().get("datos");
        assertThat(b.get("estado_documento")).isEqualTo("ACEPTADO");
        String nota = """
            {"tipo":"%s","serie":"%s","fecha_emision":"%s","documento_afectado":{"serie":"B001","numero":1},"motivo":"%s","descripcion":"Sustento"%s}
            """;
        String unaLeche = ",\"items\":[{\"codigo\":\"P002\",\"descripcion\":\"Leche\",\"unidad\":\"NIU\",\"cantidad\":1,\"precio_unitario\":4.72,\"tipo_afectacion_igv\":\"10\"}]";

        ResponseEntity<Map> descuento = http.postForEntity("/v1/notas", new HttpEntity<>(nota.formatted("07", "BC01", hoy, "04", unaLeche), h), Map.class);
        assertThat(descuento.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) descuento.getBody().get("mensaje")).startsWith("4367");

        Map<?, ?> total = (Map<?, ?>) http.postForEntity("/v1/notas", new HttpEntity<>(nota.formatted("07", "BC01", hoy, "01", ""), h), Map.class).getBody().get("datos");
        assertThat(total.get("estado_documento")).isEqualTo("ACEPTADO");
        assertThat(total.get("numero")).as("el 04 rechazado no consumió número").isEqualTo(1);
        assertThat(((Map<?, ?>) total.get("nota")).get("tipo_afectado")).isEqualTo("03");
        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("<fileName>20100066603-07-BC01-1.zip</fileName>")));
        String xml = new String(http.exchange("/v1/facturas/" + total.get("id") + "/xml", HttpMethod.GET, new HttpEntity<>(h), byte[].class).getBody());
        assertThat(xml).contains("<ds:Signature", "<cbc:ID>BC01-1</cbc:ID>", "<cbc:ID>B001-1</cbc:ID>", ">03</cbc:DocumentTypeCode>", "schemeID=\"-\"");

        ResponseEntity<Map> excede = http.postForEntity("/v1/notas", new HttpEntity<>(nota.formatted("07", "BC01", hoy, "07", unaLeche), h), Map.class);
        assertThat(excede.getStatusCode()).as("la NC total ya acreditó la boleta").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) excede.getBody().get("mensaje")).startsWith("3286");

        Map<?, ?> debito = (Map<?, ?>) http.postForEntity("/v1/notas", new HttpEntity<>(nota.formatted("08", "BD01", hoy, "01", unaLeche), h), Map.class).getBody().get("datos");
        assertThat(debito.get("estado_documento")).isEqualTo("ACEPTADO");
        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("<fileName>20100066603-08-BD01-1.zip</fileName>")));

        // Anular la NC total devuelve el saldo: entonces cabe una NC parcial.
        ResponseEntity<Map> baja = http.postForEntity("/v1/facturas/" + total.get("id") + "/baja", new HttpEntity<>("{\"motivo\":\"Nota emitida por error\"}", h), Map.class);
        assertThat(baja.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> rc = (Map<?, ?>) baja.getBody().get("datos");
        assertThat((String) rc.get("identificador")).matches("RC-\\d{8}-1");
        assertThat(rc.get("estado")).isEqualTo("ACEPTADA");
        verify(0, postRequestedFor(urlEqualTo("/billService")).withRequestBody(matching("(?s).*<fileName>20100066603-RA-.*")));
        Map<?, ?> parcial = (Map<?, ?>) http.postForEntity("/v1/notas", new HttpEntity<>(nota.formatted("07", "BC01", hoy, "07", unaLeche), h), Map.class).getBody().get("datos");
        assertThat(parcial.get("estado_documento")).isEqualTo("ACEPTADO");

        java.util.List<?> notas = (java.util.List<?>) ((Map<?, ?>) http.exchange("/v1/facturas/" + b.get("id"), HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos")).get("notas");
        assertThat(notas).hasSize(3);
    }

    static final String LIQUIDACION = """
        {"serie":"L001","fecha_emision":"%s","moneda":"PEN",
         "vendedor":{"tipo_doc":"1","num_doc":"45678912","nombre":"JUAN QUISPE MAMANI","direccion":"Caserío Santa Rosa s/n","ubigeo":"060101","condicion":"02"},
         "lugar_operacion":{"direccion":"Fundo Santa Rosa, km 12","ubigeo":"060801","condicion":"03"},
         "items":[{"codigo":"CAF-01","descripcion":"Café pergamino","unidad":"KGM","cantidad":100,"precio_unitario":11.80,"tipo_afectacion_igv":"10","codigo_sunat":"50201706"},
                  {"descripcion":"Papa blanca","unidad":"KGM","cantidad":3,"precio_unitario":50,"tipo_afectacion_igv":"20","codigo_sunat":"50405700"}]}
        """.formatted(java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")));

    /**
     * #327: la liquidación de compra con la pila real: serie 04 L001, SelfBilledInvoice firmado y validado contra su XSD, sendBill con el nombre RUC-04-L001-1, la
     * respuesta con el vendedor, la retención y el neto, y la consulta, el XML, el PDF y el listado por los endpoints de siempre. Lo que el dominio rechaza no consume
     * número, `/calcular` tampoco, y no se da de baja ni se consulta por getStatusCdr.
     */
    @Test void liquidacionDeCompraDeExtremoAExtremo(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-04-L001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.exchange("/v1/empresa/datos-fiscales", HttpMethod.PUT, new HttpEntity<>("{\"domicilio\":{\"ubigeo\":\"060101\",\"direccion\":\"Jr. Del Comercio 450\"}}", h), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"04\",\"serie\":\"L001\",\"correlativo_inicial\":0}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/v1/series", new HttpEntity<>("{\"tipo\":\"04\",\"serie\":\"F002\",\"correlativo_inicial\":0}", h), Map.class).getStatusCode())
                .as("la serie de la liquidación empieza con L").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Un vendedor con RUC y el cálculo previo no gastan número: la liquidación emitida después es la 1.
        ResponseEntity<Map> conRuc = http.postForEntity("/v1/liquidaciones-compra", new HttpEntity<>(LIQUIDACION.replace("\"tipo_doc\":\"1\",\"num_doc\":\"45678912\"", "\"tipo_doc\":\"6\",\"num_doc\":\"20131312955\""), h), Map.class);
        assertThat(conRuc.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ResponseEntity<Map> sinUbigeo = http.postForEntity("/v1/liquidaciones-compra", new HttpEntity<>(LIQUIDACION.replace("\"ubigeo\":\"060801\"", "\"ubigeo\":\"999999\""), h), Map.class);
        assertThat(sinUbigeo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(sinUbigeo.getBody().get("codigo")).isEqualTo("LIQUIDACION_INVALIDA");
        assertThat((String) sinUbigeo.getBody().get("mensaje")).startsWith("2917 - ");
        ResponseEntity<Map> calculada = http.postForEntity("/v1/liquidaciones-compra/calcular", new HttpEntity<>(LIQUIDACION, h), Map.class);
        assertThat(calculada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) ((Map<?, ?>) calculada.getBody().get("datos")).get("liquidacion")).get("neto_pagar")).isEqualTo(1132.75);
        verify(0, postRequestedFor(urlEqualTo("/billService")));

        ResponseEntity<Map> r = http.postForEntity("/v1/liquidaciones-compra", new HttpEntity<>(LIQUIDACION, h), Map.class);
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        assertThat(datos.get("tipo")).isEqualTo("04");
        assertThat(datos.get("numero")).isEqualTo(1);
        assertThat(datos.get("estado_documento")).isEqualTo("ACEPTADO");
        assertThat(datos.get("nombre_archivo")).isEqualTo("20100066603-04-L001-1");
        assertThat(((Map<?, ?>) datos.get("totales")).get("total")).isEqualTo(1132.75);
        Map<?, ?> lc = (Map<?, ?>) datos.get("liquidacion");
        assertThat(lc.get("igv_retenido")).isEqualTo(180.0);
        assertThat(((Map<?, ?>) lc.get("retencion_renta")).get("monto")).isEqualTo(17.25);
        verify(postRequestedFor(urlEqualTo("/billService")).withRequestBody(containing("<fileName>20100066603-04-L001-1.zip</fileName>")));

        String id = (String) datos.get("id");
        String xml = new String(http.exchange("/v1/facturas/" + id + "/xml", HttpMethod.GET, new HttpEntity<>(h), byte[].class).getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(xml).contains("<SelfBilledInvoice", "<ds:Signature", "<cbc:ID>L001-1</cbc:ID>", ">04</cbc:InvoiceTypeCode>", "<cac:AccountingCustomerParty>", "<cbc:AddressTypeCode>02</cbc:AddressTypeCode>");
        ResponseEntity<Map> consultada = http.exchange("/v1/facturas/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(((Map<?, ?>) ((Map<?, ?>) consultada.getBody().get("datos")).get("liquidacion")).get("ubigeo_vendedor")).isEqualTo("060101");
        ResponseEntity<byte[]> pdf = http.exchange("/v1/facturas/" + id + "/pdf", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        ResponseEntity<Map> listado = http.exchange("/v1/facturas?tipo=04", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat((List<?>) listado.getBody().get("datos")).hasSize(1);

        ResponseEntity<Map> baja = http.postForEntity("/v1/facturas/" + id + "/baja", new HttpEntity<>("{\"motivo\":\"Error en el vendedor\"}", h), Map.class);
        assertThat(baja.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(baja.getBody().get("codigo")).isEqualTo("BAJA_INVALIDA");
        assertThat((String) baja.getBody().get("mensaje")).startsWith("2308 - ");
        ResponseEntity<Map> cdr = http.postForEntity("/v1/facturas/" + id + "/cdr/recuperar", new HttpEntity<>(h), Map.class);
        assertThat(cdr.getBody().get("codigo")).as("tiene su CDR: se descarga").isEqualTo("CDR_YA_DISPONIBLE");
    }

    @Autowired OutboxWorker worker;

    @Test void sunatCaidoDejaErrorEnvioYElOutboxReintenta() throws Exception {
        stubFor(post("/billService").inScenario("caida").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recuperado"));
        stubFor(post("/billService").inScenario("caida").whenScenarioStateIs("recuperado")
                .willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> r = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        assertThat(datos.get("estado_documento")).isEqualTo("ERROR_ENVIO");
        assertThat(datos.get("intentos")).isEqualTo(1);

        // El outbox programó el reintento para dentro de 2 min; lo adelantamos y ejecutamos el worker
        jdbcAdelantarOutbox();
        assertThat(worker.procesar()).isEqualTo(1);

        ResponseEntity<Map> despues = http.exchange("/v1/facturas/" + datos.get("id"), HttpMethod.GET, new HttpEntity<>(h), Map.class);
        Map<?, ?> d = (Map<?, ?>) despues.getBody().get("datos");
        assertThat(d.get("estado_documento")).isEqualTo("ACEPTADO");
        // Historial de intentos (#4): el error y la aceptación posterior, con su motivo, en orden.
        java.util.List<Map<?, ?>> eventos = (java.util.List<Map<?, ?>>) d.get("eventos");
        assertThat(eventos.stream().map(e -> (String) e.get("estado_resultante")).toList()).containsExactly("FIRMADO", "ERROR_ENVIO", "ENVIADO", "ACEPTADO");
        assertThat((String) eventos.get(1).get("mensaje")).isNotBlank();
        assertThat((String) eventos.get(2).get("mensaje")).isEqualTo("Enviado a SUNAT (intento 2)");
        assertThat((String) eventos.get(3).get("mensaje")).contains("ha sido aceptada");
    }

    /**
     * #107: con credenciales SOL que SUNAT no acepta, el comprobante queda pendiente con un mensaje que dice qué hacer, la empresa no sigue golpeando a
     * SUNAT y, al corregir las credenciales, el envío se reanuda solo.
     */
    @Test void credencialesSolRechazadasPausanLosEnviosHastaCorregirlas() throws Exception {
        stubFor(post("/billService").willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml").withBody(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><soap-env:Fault>"
                        + "<faultcode>soap-env:Client.0102</faultcode><faultstring>Usuario o contrasena incorrectos</faultstring></soap-env:Fault></soap-env:Body></soap-env:Envelope>")));
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        Map<?, ?> datos = (Map<?, ?>) http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class).getBody().get("datos");
        assertThat(datos.get("estado_documento")).isEqualTo("ERROR_ENVIO");
        assertThat((String) datos.get("ultimo_error")).contains("credenciales SOL").contains("0102").contains("Fiscal & certificado");

        Map<?, ?> empresa = (Map<?, ?>) http.exchange("/v1/empresa", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat((String) ((Map<?, ?>) empresa.get("credenciales_sol_rechazadas")).get("motivo")).startsWith("0102");

        // Pausada: aunque el reintento esté vencido, el outbox no vuelve a golpear a SUNAT con las mismas credenciales.
        jdbcAdelantarOutbox();
        int llamadas = getAllServeEvents().size();
        assertThat(worker.procesar()).isZero();
        assertThat(getAllServeEvents()).hasSize(llamadas);

        // Corregidas: la marca se levanta y el envío sale en la siguiente vuelta del worker, sin tocar la base.
        reset();
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        assertThat(http.exchange("/v1/empresa/credenciales-sol", HttpMethod.PUT, new HttpEntity<>("{\"usuario\":\"BIENBIEN\",\"clave\":\"correcta\"}", h), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(((Map<?, ?>) http.exchange("/v1/empresa", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos")).get("credenciales_sol_rechazadas")).isNull();
        assertThat(worker.procesar()).isEqualTo(1);
        Map<?, ?> d = (Map<?, ?>) http.exchange("/v1/facturas/" + datos.get("id"), HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat(d.get("estado_documento")).isEqualTo("ACEPTADO");
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private void jdbcAdelantarOutbox() { jdbc.update("UPDATE outbox SET siguiente_intento = now() - interval '1 second'"); }

    @Test void sinApiKeyEs401() {
        ResponseEntity<String> r = http.getForEntity("/v1/facturas", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void adminSinClaveDePlataformaEs401() {
        HttpHeaders sinClave = new HttpHeaders(); sinClave.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> sinHeader = http.postForEntity("/v1/admin/tenants",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", sinClave), String.class);
        assertThat(sinHeader.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders claveErronea = new HttpHeaders(); claveErronea.set("X-Platform-Key", "clave-incorrecta"); claveErronea.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> conClaveErronea = http.postForEntity("/v1/admin/tenants",
                new HttpEntity<>("{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}", claveErronea), String.class);
        assertThat(conClaveErronea.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Tomcat decodifica %xx, descarta ";param" y resuelve ".." antes de enrutar; los filtros deben decidir
     * sobre esa misma ruta y no sobre la URI cruda. Se usa URI.create para que el cliente no codifique nada.
     */
    @Test void bypassDeRutasNoFunciona() throws Exception {
        String cuerpoTenant = "{\"ruc\":\"20100066603\",\"razon_social\":\"EMPRESA DE PRUEBA S.A.C.\",\"entorno\":\"BETA\"}";
        HttpHeaders sinClave = new HttpHeaders(); sinClave.setContentType(MediaType.APPLICATION_JSON);
        for (String ruta : new String[]{"/v1;x/admin/tenants", "/v1/%61dmin/tenants", "/v1//admin/tenants", "/v1/facturas/../admin/tenants"}) {
            ResponseEntity<String> r = http.exchange(URI.create(ruta), HttpMethod.POST, new HttpEntity<>(cuerpoTenant, sinClave), String.class);
            assertThat(r.getStatusCode()).as(ruta + " sin clave de plataforma").isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        String apiKey = provisionarTenant();
        HttpHeaders conApiKey = new HttpHeaders(); conApiKey.set("X-Api-Key", apiKey); conApiKey.setContentType(MediaType.APPLICATION_JSON);
        for (String ruta : new String[]{"/v1/%61dmin/tenants", "/v1;x/admin/tenants", "/v1/facturas/../admin/tenants"}) {
            ResponseEntity<String> r = http.exchange(URI.create(ruta), HttpMethod.POST, new HttpEntity<>(cuerpoTenant, conApiKey), String.class);
            assertThat(r.getStatusCode()).as(ruta + " con API key de tenant pero sin clave de plataforma").isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant", Integer.class)).isEqualTo(1);

        for (String ruta : new String[]{"/v1;x/facturas", "/v1/%66acturas", "/v1/admin/../facturas"}) {
            ResponseEntity<String> r = http.exchange(URI.create(ruta), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
            assertThat(r.getStatusCode()).as(ruta + " sin API key").isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * #18: la empresa de una cuenta con el plan Gratis (30 documentos al mes) que ya tiene 29 en el mes emite el número 30 y el 31 se rechaza con 429 LIMITE_PLAN, sin
     * gastar correlativo ni llamar a SUNAT. Lo que está en camino también ocupa lugar.
     */
    @Test void enElTopeDelPlanLaEmisionSeRechazaCon429(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders json = new HttpHeaders(); json.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Tope SAC\",\"email\":\"tope@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\",\"acepta_terminos\":true}", json), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        java.util.UUID cuenta = jdbc.queryForObject("SELECT id FROM cuenta WHERE email = 'tope@negocio.pe'", java.util.UUID.class);
        assertThat(jdbc.queryForObject("SELECT p.nombre FROM suscripcion s JOIN plan p ON p.id = s.plan_id WHERE s.cuenta_id = ? AND s.termina_en IS NULL", String.class, cuenta)).isEqualTo("Gratis");
        jdbc.update("UPDATE tenant SET cuenta_id = ? WHERE ruc = '20100066603'", cuenta);
        java.time.LocalDate hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima"));
        // 28 aceptados y uno en camino, en serie aparte para no tocar la numeración de F001.
        jdbc.update("""
                INSERT INTO documento (id, tenant_id, tipo, serie, numero, fecha_emision, estado, nombre_archivo)
                SELECT gen_random_uuid(), t.id, '01', 'F900', g, ?, CASE WHEN g = 1 THEN 'ENVIADO' ELSE 'ACEPTADO' END, 'archivo'
                FROM tenant t, generate_series(1, 29) g WHERE t.ruc = '20100066603'""", java.sql.Date.valueOf(hoy));
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> trigesima = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(trigesima.getStatusCode()).as("%s", trigesima.getBody()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> rechazada = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(rechazada.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rechazada.getBody().toString()).contains("LIMITE_PLAN").contains("30 documentos").contains("Gratis");
        assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = 'F001'", Long.class)).as("el rechazo no gastó el número 2").isEqualTo(1L);
        verify(1, postRequestedFor(urlEqualTo("/billService")));
    }

    /**
     * #285: con el plan en gracia la cuenta sigue emitiendo; pasada la gracia, la emisión se rechaza con 402 PLAN_VENCIDO sin gastar correlativo ni llamar a SUNAT, y
     * lo ya emitido se sigue consultando y dando de baja.
     */
    @Test void conElPlanVencidoLaEmisionSeRechazaCon402(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        String apiKey = provisionarTenant();
        HttpHeaders json = new HttpHeaders(); json.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/auth/registro", new HttpEntity<>(
                "{\"nombre\":\"Vencida SAC\",\"email\":\"vencida@negocio.pe\",\"password\":\"Segura123\",\"telefono\":\"987654321\",\"acepta_terminos\":true}", json), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        java.util.UUID cuenta = jdbc.queryForObject("SELECT id FROM cuenta WHERE email = 'vencida@negocio.pe'", java.util.UUID.class);
        jdbc.update("UPDATE tenant SET cuenta_id = ? WHERE ruc = '20100066603'", cuenta);
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);

        // Venció hace un día con 3 de gracia: en gracia.
        jdbc.update("UPDATE suscripcion SET inicia_en = now() - interval '40 days', vence_en = now() - interval '1 day', dias_de_gracia = 3 WHERE cuenta_id = ? AND termina_en IS NULL", cuenta);
        ResponseEntity<Map> enGracia = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(enGracia.getStatusCode()).as("%s", enGracia.getBody()).isEqualTo(HttpStatus.CREATED);

        // Venció hace cuatro días con 3 de gracia: vencida.
        jdbc.update("UPDATE suscripcion SET vence_en = now() - interval '4 days' WHERE cuenta_id = ? AND termina_en IS NULL", cuenta);
        ResponseEntity<Map> rechazada = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(rechazada.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(rechazada.getBody().toString()).contains("PLAN_VENCIDO").contains("Gratis");
        assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = 'F001'", Long.class)).as("el rechazo no gastó el número 2").isEqualTo(1L);
        verify(1, postRequestedFor(urlEqualTo("/billService")));

        String id = (String) ((Map<?, ?>) enGracia.getBody().get("datos")).get("id");
        assertThat(http.exchange("/v1/facturas/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class).getStatusCode()).as("lo ya emitido se consulta").isEqualTo(HttpStatus.OK);
        stubFor(post("/billService").withRequestBody(containing("sendSummary")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:sendSummaryResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><ticket>1789768174685</ticket></ns2:sendSummaryResponse></soap-env:Body></soap-env:Envelope>")));
        byte[] cdrRa = ZipUtil.comprimir("R-20100066603-RA.xml", CDR_OK.getBytes());
        stubFor(post("/billService").withRequestBody(containing("getStatus")).willReturn(okXml(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><status><statusCode>0</statusCode><content>"
                        + Base64.getEncoder().encodeToString(cdrRa) + "</content></status></ns2:getStatusResponse></soap-env:Body></soap-env:Envelope>")));
        ResponseEntity<Map> baja = http.postForEntity("/v1/facturas/" + id + "/baja", new HttpEntity<>("{\"motivo\":\"Se cobró dos veces\"}", h), Map.class);
        assertThat(baja.getStatusCode()).as("la comunicación de baja sigue funcionando: %s", baja.getBody()).isEqualTo(HttpStatus.CREATED);
    }

    /** #361: el padrón cargado desde un archivo (el formato real de SUNAT, en ISO-8859-1) se consulta por la API con la clave de la empresa. */
    @Test void consultaDeRucDelPadronDeExtremoAExtremo(@org.junit.jupiter.api.io.TempDir java.nio.file.Path carpeta) throws Exception {
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey);
        jdbc.update("TRUNCATE padron_ruc, carga_padron_ruc");

        ResponseEntity<Map> sinPadron = http.exchange("/v1/consultas/ruc/20100066603", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(sinPadron.getStatusCode()).as("sin carga no se afirma que el RUC no existe").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(sinPadron.getBody().get("codigo")).isEqualTo("PADRON_NO_CARGADO");

        java.nio.file.Path archivo = carpeta.resolve("padron_reducido_ruc.txt");
        java.nio.file.Files.writeString(archivo, """
                RUC|NOMBRE O RAZÓN SOCIAL|ESTADO DEL CONTRIBUYENTE|CONDICIÓN DE DOMICILIO|UBIGEO|TIPO DE VÍA|NOMBRE DE VÍA|CÓDIGO DE ZONA|TIPO DE ZONA|NÚMERO|INTERIOR|LOTE|DEPARTAMENTO|MANZANA|KILÓMETRO|
                20100066603|MUÑOZ HERMANOS S.A.C.|ACTIVO|HABIDO|150101|AV.|REPUBLICA DE PANAMA|-|-|2457|-|-|-|-|-|
                20160109441|GARAMENDI ALARCON ANTONIO|BAJA DE OFICIO|NO HABIDO|-|-|-|-|-|-|-|-|-|-|-|
                """, java.nio.charset.StandardCharsets.ISO_8859_1);
        var carga = new pe.factura.application.service.CargarPadronRucService(1, new pe.factura.adapters.sunat.ArchivoDelPadronRuc(archivo.toString(), java.time.Duration.ofSeconds(5)), padron, java.time.Clock.systemUTC()).cargar();
        assertThat(carga.filas()).isEqualTo(2);

        ResponseEntity<Map> r = http.exchange("/v1/consultas/ruc/20100066603", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> d = (Map<?, ?>) r.getBody().get("datos");
        assertThat(d.get("razon_social")).isEqualTo("MUÑOZ HERMANOS S.A.C.");
        assertThat(d.get("activo")).isEqualTo(true);
        assertThat(d.get("direccion")).isEqualTo("AV. REPUBLICA DE PANAMA NRO. 2457");
        assertThat(d.get("actualizado_al")).isNotNull();

        Map<?, ?> baja = (Map<?, ?>) http.exchange("/v1/consultas/ruc/20160109441", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("datos");
        assertThat(baja.get("estado")).isEqualTo("BAJA DE OFICIO");
        assertThat(baja.get("activo")).isEqualTo(false);
        assertThat(baja.get("habido")).isEqualTo(false);

        assertThat(http.exchange("/v1/consultas/ruc/20601234565", HttpMethod.GET, new HttpEntity<>(h), Map.class).getStatusCode()).as("válido pero no está").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/v1/consultas/ruc/20601234567", HttpMethod.GET, new HttpEntity<>(h), Map.class).getStatusCode()).as("dígito verificador errado").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.exchange("/v1/consultas/ruc/20100066603", HttpMethod.GET, HttpEntity.EMPTY, Map.class).getStatusCode()).as("sin credenciales").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * #378: con el padrón al día, un comprador cuyo RUC no existe se rechaza con 422 RECEPTOR_INVALIDO (1083) sin gastar correlativo ni llamar a SUNAT; uno de baja y no
     * habido se emite (SUNAT lo acepta con observación) y la respuesta trae los avisos 4013 y 4014; y sin padrón cargado la emisión es la de siempre.
     */
    @Test void laEmisionValidaElRucDelCompradorContraElPadron(WireMockRuntimeInfo wm, @org.junit.jupiter.api.io.TempDir java.nio.file.Path carpeta) throws Exception {
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        java.nio.file.Path archivo = carpeta.resolve("padron_reducido_ruc.txt");
        java.nio.file.Files.writeString(archivo, """
                RUC|NOMBRE O RAZÓN SOCIAL|ESTADO DEL CONTRIBUYENTE|CONDICIÓN DE DOMICILIO|UBIGEO|TIPO DE VÍA|NOMBRE DE VÍA|CÓDIGO DE ZONA|TIPO DE ZONA|NÚMERO|INTERIOR|LOTE|DEPARTAMENTO|MANZANA|KILÓMETRO|
                20100066603|MUÑOZ HERMANOS S.A.C.|ACTIVO|HABIDO|150101|AV.|REPUBLICA DE PANAMA|-|-|2457|-|-|-|-|-|
                20160109441|GARAMENDI ALARCON ANTONIO|BAJA DE OFICIO|NO HABIDO|-|-|-|-|-|-|-|-|-|-|-|
                """, java.nio.charset.StandardCharsets.ISO_8859_1);
        new pe.factura.application.service.CargarPadronRucService(1, new pe.factura.adapters.sunat.ArchivoDelPadronRuc(archivo.toString(), java.time.Duration.ofSeconds(5)), padron, java.time.Clock.systemUTC()).cargar();

        // 20601234565 es un RUC válido que el padrón no tiene.
        ResponseEntity<Map> inexistente = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(inexistente.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(inexistente.getBody().get("codigo")).isEqualTo("RECEPTOR_INVALIDO");
        assertThat((String) inexistente.getBody().get("mensaje")).contains("1083").contains("20601234565");
        assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = 'F001'", Long.class)).as("el rechazo no gastó el número 1").isZero();
        verify(0, postRequestedFor(urlEqualTo("/billService")));

        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-1"))));
        ResponseEntity<Map> deBaja = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA.replace("20601234565", "20160109441"), h), Map.class);
        assertThat(deBaja.getStatusCode()).as("%s", deBaja.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) deBaja.getBody().get("datos")).get("numero")).isEqualTo(1);
        assertThat((java.util.List<Map<String, Object>>) deBaja.getBody().get("avisos")).extracting(a -> a.get("codigo")).containsExactly("4013", "4014");

        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-2"))));
        ResponseEntity<Map> enRegla = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA.replace("20601234565", "20100066603"), h), Map.class);
        assertThat(enRegla.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(enRegla.getBody()).as("sin nada que avisar, la respuesta no trae el campo").doesNotContainKey("avisos");

        jdbc.update("TRUNCATE padron_ruc, carga_padron_ruc");
        stubFor(post("/billService").willReturn(okXml(soapOk("20100066603-01-F001-3"))));
        ResponseEntity<Map> sinPadron = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA, h), Map.class);
        assertThat(sinPadron.getStatusCode()).as("sin padrón cargado no se rechaza nada: %s", sinPadron.getBody()).isEqualTo(HttpStatus.CREATED);
    }

    // --- Numeración de la emisión (#9) ----------------------------------------------------------------------------------------------------------------------
    // SUNAT exige números correlativos: varias emisiones a la vez sobre la misma serie no pueden repetir ni saltarse uno, y un fallo después de asignar el número (aquí, el
    // almacenamiento) no puede dejar un hueco. El bloqueo de la serie se mantiene a propósito durante todo el trabajo (ver k6/README.md); esto es lo que ese bloqueo garantiza.

    private static final String FACTURA_SIN_ENVIO = FACTURA.replace("\"moneda\":\"PEN\",", "\"moneda\":\"PEN\",\"enviar_automatico\":false,");

    @org.springframework.beans.factory.annotation.Value("${app.storage.fs-root}") String raizDelAlmacenamiento;
    @org.springframework.beans.factory.annotation.Value("${app.storage.type:fs}") String tipoDeAlmacenamiento;

    @Test void dieciseisEmisionesALaVezSobreLaMismaSerieNoRepitenNiSaltanUnNumero(WireMockRuntimeInfo wm) throws Exception {
        String apiKey = provisionarTenant();
        int n = 16;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        try {
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<ResponseEntity<Map>>> pedidos = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) pedidos.add(pool.submit(() -> {
                salida.await();
                HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
                return http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA_SIN_ENVIO, h), Map.class);
            }));
            salida.countDown();

            java.util.TreeSet<Integer> numeros = new java.util.TreeSet<>();
            for (java.util.concurrent.Future<ResponseEntity<Map>> f : pedidos) {
                ResponseEntity<Map> r = f.get(120, java.util.concurrent.TimeUnit.SECONDS);
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

    /**
     * Si guardar el XML falla después de asignar el número, todo se deshace: la serie no avanza y la siguiente emisión recibe ese mismo número, sin hueco. El fallo se provoca
     * poniendo un archivo donde el almacenamiento necesita la carpeta de la empresa; el orden de {@code EmitirComprobanteService} (numerar, firmar, guardar) hace que sea después.
     */
    @Test void siElAlmacenamientoFallaDespuesDeNumerarElNumeroNoSePierde(WireMockRuntimeInfo wm) throws Exception {
        // FacturaS3E2ETest hereda esta clase con el almacenamiento en S3: el fallo se provoca sobre carpetas de disco.
        org.junit.jupiter.api.Assumptions.assumeTrue("fs".equals(tipoDeAlmacenamiento), "solo con el almacenamiento en disco");
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA_SIN_ENVIO, h), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);   // F001-1
        UUID tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE ruc = '20100066603'", UUID.class);
        java.nio.file.Path carpeta = java.nio.file.Path.of(raizDelAlmacenamiento).resolve(tenant.toString());
        java.nio.file.Path aparte = java.nio.file.Path.of(raizDelAlmacenamiento).resolve(tenant + ".aparte");
        java.nio.file.Files.move(carpeta, aparte);
        java.nio.file.Files.writeString(carpeta, "no es una carpeta");
        try {
            ResponseEntity<Map> fallida = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA_SIN_ENVIO, h), Map.class);

            assertThat(fallida.getStatusCode().is5xxServerError()).as("%s", fallida.getBody()).isTrue();
            assertThat(jdbc.queryForObject("SELECT ultimo_numero FROM serie WHERE codigo = 'F001'", Long.class)).as("la serie no avanzó").isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM documento WHERE serie = 'F001'", Integer.class)).as("no quedó un documento a medias").isEqualTo(1);
        } finally {
            java.nio.file.Files.delete(carpeta);
            java.nio.file.Files.move(aparte, carpeta);
        }

        ResponseEntity<Map> siguiente = http.postForEntity("/v1/facturas", new HttpEntity<>(FACTURA_SIN_ENVIO, h), Map.class);

        assertThat(siguiente.getStatusCode()).as("%s", siguiente.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) siguiente.getBody().get("datos")).get("numero")).as("el número del intento fallido se reutiliza: sin hueco").isEqualTo(2);
    }

    // --- Validez por la API REST de SUNAT (#318), con la pila real y SUNAT simulado ------------------------------------------------------------------------------

    @Test void laValidezSeConsultaPorLaApiRestConLasCredencialesApiSunat(WireMockRuntimeInfo wm) throws Exception {
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        String consulta = "/v1/consultas/validez?ruc=20100066603&tipo=01&serie=F001&numero=12&fecha=2026-09-10&monto=118";

        // Solo con clave SOL, en BETA no hay billValidService.
        ResponseEntity<Map> sinApi = http.exchange(consulta, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(sinApi.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(sinApi.getBody().get("codigo")).isEqualTo("NO_DISPONIBLE_EN_BETA");

        String id = "d20b421e-4ace-46d8-ac8c-4fbb44cfde17";
        assertThat(http.exchange("/v1/empresa/credenciales-api-sunat", HttpMethod.PUT,
                new HttpEntity<>("{\"client_id\":\"" + id + "\",\"client_secret\":\"secreto-e2e\"}", h), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> empresa = http.exchange("/v1/empresa", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(((Map<?, ?>) empresa.getBody().get("datos")).get("tiene_credenciales_api_sunat")).isEqualTo(true);
        assertThat(empresa.getBody().toString()).doesNotContain("secreto-e2e");

        stubFor(post("/api-seguridad/v1/clientesextranet/" + id + "/oauth2/token/").willReturn(okJson("{\"access_token\":\"tk-e2e\",\"token_type\":\"JWT\",\"expires_in\":3600}")));
        stubFor(post(urlMatching("/api-sunat/v1/contribuyente/contribuyentes/\\d{11}/validarcomprobante")).withHeader("Authorization", equalTo("Bearer tk-e2e"))
                .willReturn(okJson("{\"success\":true,\"message\":\"Operation Success! \",\"data\":{\"estadoCp\":\"1\",\"estadoRuc\":\"00\",\"condDomiRuc\":\"00\",\"observaciones\":[]}}")));

        ResponseEntity<Map> r = http.exchange(consulta, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).as("%s", r.getBody()).isEqualTo(HttpStatus.OK);
        Map<?, ?> datos = (Map<?, ?>) r.getBody().get("datos");
        assertThat(datos.get("estado")).isEqualTo("ACEPTADO");
        assertThat(datos.get("fuente")).isEqualTo("API_REST");
        assertThat(datos.get("estado_ruc")).isEqualTo("00");
        verify(postRequestedFor(urlMatching("/api-sunat/.*")).withRequestBody(equalToJson(
                "{\"numRuc\":\"20100066603\",\"codComp\":\"01\",\"numeroSerie\":\"F001\",\"numero\":\"12\",\"fechaEmision\":\"10/09/2026\",\"monto\":\"118.00\"}")));

        // La API caída y sin SOAP en BETA: un 503 que dice que SUNAT no respondió, no un error interno.
        stubFor(post(urlMatching("/api-sunat/.*")).willReturn(aResponse().withStatus(503)));
        ResponseEntity<Map> caida = http.exchange(consulta, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(caida.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(caida.getBody().get("codigo")).isEqualTo("SUNAT_NO_DISPONIBLE");
        assertThat(caida.getBody().toString()).doesNotContain("secreto-e2e").doesNotContain("tk-e2e");

        assertThat(http.exchange("/v1/empresa/credenciales-api-sunat", HttpMethod.DELETE, new HttpEntity<>(h), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.exchange(consulta, HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody().get("codigo")).isEqualTo("NO_DISPONIBLE_EN_BETA");
    }

    // --- Clientes guardados (#319), por API key y con la pila real ---------------------------------------------------------------------------------------------

    @Test void losClientesGuardadosSeCreanBuscanActualizanYBorranPorApiKey(WireMockRuntimeInfo wm) throws Exception {
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        String andina = "{\"tipo_doc\":\"6\",\"num_doc\":\"20601234565\",\"razon_social\":\"PESQUERA PACÍFICO S.A.C.\",\"direccion\":\"Av. Lima 1\",\"email\":\"Facturas@Andina.PE\"}";

        ResponseEntity<Map> creado = http.postForEntity("/v1/clientes", new HttpEntity<>(andina, h), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) creado.getBody().get("datos");
        String id = (String) datos.get("id");
        assertThat(datos.get("email")).as("el correo se guarda en minúsculas").isEqualTo("facturas@andina.pe");

        ResponseEntity<Map> repetido = http.postForEntity("/v1/clientes", new HttpEntity<>(andina, h), Map.class);
        assertThat(repetido.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repetido.getBody().get("codigo")).isEqualTo("DUPLICADO");
        ResponseEntity<Map> invalido = http.postForEntity("/v1/clientes", new HttpEntity<>("{\"tipo_doc\":\"1\",\"num_doc\":\"123\",\"razon_social\":\"JUAN PEREZ\"}", h), Map.class);
        assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalido.getBody().get("codigo")).isEqualTo("RECEPTOR_INVALIDO");

        ResponseEntity<Map> busqueda = http.exchange("/v1/clientes?q=pacifico", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(busqueda.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(((java.util.List<Map<String, Object>>) busqueda.getBody().get("datos")).get(0).get("razon_social")).isEqualTo("PESQUERA PACÍFICO S.A.C.");
        assertThat(http.exchange("/v1/clientes?q=zzz", HttpMethod.GET, new HttpEntity<>(h), Map.class).getHeaders().getFirst("X-Total-Count")).isEqualTo("0");

        ResponseEntity<Map> guardado = http.exchange("/v1/clientes/documento/6/20601234565", HttpMethod.PUT, new HttpEntity<>("{\"razon_social\":\"PESQUERA PACIFICO SUR S.A.C.\"}", h), Map.class);
        assertThat(guardado.getStatusCode()).as("ya existía: se actualiza").isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) guardado.getBody().get("datos")).get("id")).isEqualTo(id);
        ResponseEntity<Map> nuevo = http.exchange("/v1/clientes/documento/1/12345678", HttpMethod.PUT, new HttpEntity<>("{\"razon_social\":\"JUAN PEREZ\"}", h), Map.class);
        assertThat(nuevo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.exchange("/v1/clientes", HttpMethod.GET, new HttpEntity<>(h), Map.class).getHeaders().getFirst("X-Total-Count")).isEqualTo("2");

        assertThat(http.exchange("/v1/clientes/" + id, HttpMethod.DELETE, new HttpEntity<>(h), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.exchange("/v1/clientes/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/v1/clientes", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class).getStatusCode()).as("sin credenciales").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Productos guardados (#320), por API key y con la pila real ---------------------------------------------------------------------------------------------

    @Test void losProductosGuardadosSeCreanBuscanActualizanYBorranPorApiKey(WireMockRuntimeInfo wm) throws Exception {
        String apiKey = provisionarTenant();
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", apiKey); h.setContentType(MediaType.APPLICATION_JSON);
        String consultoria = "{\"codigo\":\"SKU-1\",\"descripcion\":\"Consultoría tributaria\",\"unidad\":\"zz\",\"tipo_afectacion_igv\":\"10\",\"precio_unitario\":118,\"codigo_sunat\":\"80101500\"}";

        ResponseEntity<Map> creado = http.postForEntity("/v1/productos", new HttpEntity<>(consultoria, h), Map.class);
        assertThat(creado.getStatusCode()).as("%s", creado.getBody()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> datos = (Map<?, ?>) creado.getBody().get("datos");
        String id = (String) datos.get("id");
        assertThat(datos.get("unidad")).as("la unidad del catálogo 03 se guarda en mayúsculas").isEqualTo("ZZ");
        assertThat(datos.get("precio_unitario").toString()).isEqualTo("118.0");

        ResponseEntity<Map> repetido = http.postForEntity("/v1/productos", new HttpEntity<>(consultoria, h), Map.class);
        assertThat(repetido.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repetido.getBody().get("codigo")).isEqualTo("DUPLICADO");
        ResponseEntity<Map> invalido = http.postForEntity("/v1/productos", new HttpEntity<>("{\"descripcion\":\"Algo\",\"unidad\":\"XYZ\",\"tipo_afectacion_igv\":\"10\",\"precio_unitario\":10}", h), Map.class);
        assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalido.getBody().get("codigo")).isEqualTo("ITEM_INVALIDO");
        ResponseEntity<Map> enCero = http.postForEntity("/v1/productos", new HttpEntity<>("{\"descripcion\":\"Algo\",\"unidad\":\"ZZ\",\"tipo_afectacion_igv\":\"10\",\"precio_unitario\":0}", h), Map.class);
        assertThat(enCero.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> busqueda = http.exchange("/v1/productos?q=consultoria", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(busqueda.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
        assertThat(((java.util.List<Map<String, Object>>) busqueda.getBody().get("datos")).get(0).get("codigo_sunat")).isEqualTo("80101500");
        assertThat(http.exchange("/v1/productos?q=zzz", HttpMethod.GET, new HttpEntity<>(h), Map.class).getHeaders().getFirst("X-Total-Count")).isEqualTo("0");

        ResponseEntity<Map> cambiado = http.exchange("/v1/productos/" + id, HttpMethod.PUT, new HttpEntity<>("{\"codigo\":\"SKU-1\",\"descripcion\":\"Consultoría tributaria mensual\",\"unidad\":\"HUR\",\"tipo_afectacion_igv\":\"10\",\"precio_unitario\":236.5}", h), Map.class);
        assertThat(cambiado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) cambiado.getBody().get("datos")).containsKey("codigo_sunat")).as("lo opcional que no se envía se borra y el JSON no lo trae").isFalse();

        assertThat(http.exchange("/v1/productos/" + id, HttpMethod.DELETE, new HttpEntity<>(h), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.exchange("/v1/productos/" + id, HttpMethod.GET, new HttpEntity<>(h), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/v1/productos", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class).getStatusCode()).as("sin credenciales").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Autowired pe.factura.application.port.out.PadronRucRepository padron;

    @org.junit.jupiter.api.BeforeEach void limpiar() {
        jdbc.update("TRUNCATE padron_ruc, carga_padron_ruc");
        jdbc.update("TRUNCATE outbox, evento_documento, comprobante_item, comprobante, documento, serie, api_key, tenant, token_recuperacion, sesion, usuario, cuenta CASCADE");
    }
}
