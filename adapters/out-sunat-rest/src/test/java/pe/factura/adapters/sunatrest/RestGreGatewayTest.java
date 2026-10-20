package pe.factura.adapters.sunatrest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatGreGateway.EstadoDelTicket;
import pe.factura.application.port.out.SunatGreGateway.Recepcion;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.CredencialesSol;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * #39: el envío de la GRE y la consulta del ticket contra un SUNAT simulado (WireMock) que responde lo que dice «manual_url_gre_rest» (hojas REST1 y REST2) y el
 * manual del token (grant password). No es una prueba contra SUNAT: no hay credenciales API reales (ver deploy/README.md §14).
 */
@WireMockTest
class RestGreGatewayTest {
    static final String ID = "d20b421e-4ace-46d8-ac8c-4fbb44cfde17";
    static final String SECRETO = "HhWwZVo8ZaWm4BZ7n/xEzw==";
    static final String CLAVE_SOL = "clave-sol-secreta";
    static final String TOKEN = "eyJ0b2tlbi1kZS1wcnVlYmEifQ.firma";
    static final String RUTA_TOKEN = "/v1/clientessol/" + ID + "/oauth2/token/";
    static final String ARCHIVO = "20100066603-09-T001-15";
    static final String RUTA_ENVIO = "/v1/contribuyente/gem/comprobantes/" + ARCHIVO;
    static final String TICKET = "7f7b00ab-4fee-4ae2-9e04-fbd16ca18677";
    static final String RUTA_TICKET = "/v1/contribuyente/gem/comprobantes/envios/" + TICKET;
    static final byte[] XML = "<DespatchAdvice>firmado</DespatchAdvice>".getBytes(StandardCharsets.UTF_8);

    final Clock reloj = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Lima"));
    final Tenant produccion = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA DE PRUEBA S.A.C.", Entorno.PRODUCCION, new CredencialesSol("USUARIO1", CLAVE_SOL), null)
            .conCredencialesApiSunat(new CredencialesApiSunat(ID, SECRETO));

    RestGreGateway gateway(WireMockRuntimeInfo wm, String beta) {
        return new RestGreGateway(true, wm.getHttpBaseUrl(), beta == null ? null : wm.getHttpBaseUrl() + "/beta-token", wm.getHttpBaseUrl(), beta,
                new ClienteApiSunat(Duration.ofSeconds(1), Duration.ofSeconds(2), reloj));
    }
    RestGreGateway gateway(WireMockRuntimeInfo wm) { return gateway(wm, null); }

    static void tokenOk() {
        stubFor(post(RUTA_TOKEN).willReturn(okJson("{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"JWT\",\"expires_in\":3600}")));
    }

    static byte[] cdr(String codigo, String enlace) throws Exception {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ar:ApplicationResponse xmlns:ar="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
              xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
              <cbc:ID>1</cbc:ID>
              <cac:DocumentResponse><cac:Response><cbc:ResponseCode>%s</cbc:ResponseCode><cbc:Description>La Guia numero T001-15, ha sido aceptada</cbc:Description></cac:Response>
              <cac:DocumentReference><cbc:ID>T001-15</cbc:ID><cbc:DocumentDescription>%s</cbc:DocumentDescription></cac:DocumentReference></cac:DocumentResponse>
            </ar:ApplicationResponse>""".formatted(codigo, enlace);
        return RestGreGateway.zip("R-" + ARCHIVO + ".xml", xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test void enviaElZipEnBase64ConSuHashYDevuelveElTicket(WireMockRuntimeInfo wm) throws Exception {
        tokenOk();
        stubFor(post(RUTA_ENVIO).willReturn(okJson("{\"numTicket\":\"" + TICKET + "\",\"fecRecepcion\":\"2022-04-11T15:37:41\"}")));

        Recepcion r = gateway(wm).enviar(produccion, ARCHIVO, XML);

        assertThat(r.ticket()).isEqualTo(TICKET);
        assertThat(r.fechaRecepcion()).isEqualTo(LocalDateTime.of(2022, 4, 11, 15, 37, 41));
        LoggedRequest token = findAll(postRequestedFor(urlEqualTo(RUTA_TOKEN))).get(0);
        String form = URLDecoder.decode(token.getBodyAsString(), StandardCharsets.UTF_8);
        assertThat(form).contains("grant_type=password").contains("scope=https://api-cpe.sunat.gob.pe").contains("client_id=" + ID)
                .contains("username=20100066603USUARIO1").contains("password=" + CLAVE_SOL);
        assertThat(token.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded");

        LoggedRequest envio = findAll(postRequestedFor(urlEqualTo(RUTA_ENVIO))).get(0);
        assertThat(envio.getHeader("Authorization")).isEqualTo("Bearer " + TOKEN);
        assertThat(envio.getHeader("Content-Type")).startsWith("application/json");
        JsonNode archivo = new ObjectMapper().readTree(envio.getBodyAsString()).path("archivo");
        assertThat(archivo.path("nomArchivo").asText()).isEqualTo(ARCHIVO + ".zip");
        byte[] zip = Base64.getDecoder().decode(archivo.path("arcGreZip").asText());
        assertThat(archivo.path("hashZip").asText()).as("SHA-256 del ZIP, en hexadecimal").isEqualTo(RestGreGateway.sha256(zip)).hasSize(64);
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e = in.getNextEntry();
            assertThat(e.getName()).as("159/161: el XML se llama como el ZIP").isEqualTo(ARCHIVO + ".xml");
            assertThat(in.readAllBytes()).isEqualTo(XML);
            assertThat(in.getNextEntry()).as("158: un solo comprobante por ZIP").isNull();
        }
    }

    @Test void conElTokenVencidoAntesDeTiempoLoRenuevaYReintentaUnaVez(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(post(RUTA_ENVIO).inScenario("401").whenScenarioStateIs(Scenario.STARTED).willReturn(aResponse().withStatus(401)).willSetStateTo("renovado"));
        stubFor(post(RUTA_ENVIO).inScenario("401").whenScenarioStateIs("renovado").willReturn(okJson("{\"numTicket\":\"" + TICKET + "\"}")));

        assertThat(gateway(wm).enviar(produccion, ARCHIVO, XML).ticket()).isEqualTo(TICKET);
        verify(2, postRequestedFor(urlEqualTo(RUTA_TOKEN)));
        verify(2, postRequestedFor(urlEqualTo(RUTA_ENVIO)));
    }

    @Test void un422DelEnvioEsUnRechazoConElCodigoDelDetalle(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(post(RUTA_ENVIO).willReturn(aResponse().withStatus(422).withBody("{\"cod\":422,\"msg\":\"Unprocessable Entity\",\"errors\":[{\"cod\":504,\"msg\":\"El campo nomArchivo no cumple con el formato establecido\"}]}")));
        assertThatThrownBy(() -> gateway(wm).enviar(produccion, ARCHIVO, XML)).isInstanceOf(SunatRechazoException.class)
                .hasFieldOrPropertyWithValue("codigo", "504").hasMessageContaining("504 El campo nomArchivo no cumple");
    }

    @Test void lasCredencialesQueSunatNoAceptaNoSeReintentanYNoSeFiltranLosSecretos(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid_grant\",\"error_description\":\"clave " + CLAVE_SOL + " incorrecta\"}")));
        Throwable t = catchThrowable(() -> gateway(wm).enviar(produccion, ARCHIVO, XML));
        assertThat(t).isInstanceOf(SunatCredencialesException.class).hasMessageContaining("invalid_grant").hasMessageNotContaining(CLAVE_SOL).hasMessageNotContaining(SECRETO);
        verify(0, postRequestedFor(urlEqualTo(RUTA_ENVIO)));
    }

    @Test void unaCaidaDeSunatEsTransitoria(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(post(RUTA_ENVIO).willReturn(aResponse().withStatus(503).withBody("{\"cod\":\"503\",\"msg\":\"Service Unavailable\"}")));
        assertThatThrownBy(() -> gateway(wm).enviar(produccion, ARCHIVO, XML)).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("503");
        stubFor(post(RUTA_ENVIO).willReturn(okJson("{\"fecRecepcion\":\"2022-04-11T15:37:41\"}")));
        assertThatThrownBy(() -> gateway(wm).enviar(produccion, ARCHIVO, XML)).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("numTicket");
    }

    @Test void elTicketEnProceso(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(RUTA_TICKET).willReturn(okJson("{\"codRespuesta\":\"98\"}")));
        EstadoDelTicket e = gateway(wm).consultar(produccion, TICKET);
        assertThat(e.enProceso()).isTrue();
        assertThat(e.conCdr()).isFalse();
        verify(getRequestedFor(urlEqualTo(RUTA_TICKET)).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test void elTicketCorrectoTraeElCdrYLaUrlDelQr(WireMockRuntimeInfo wm) throws Exception {
        tokenOk();
        byte[] cdr = cdr("0", "https://e-factura.sunat.gob.pe/v1/contribuyente/gre/comprobantes/descargaqr?hashqr=abc");
        stubFor(get(RUTA_TICKET).willReturn(okJson("{\"codRespuesta\":\"0\",\"arcCdr\":\"" + Base64.getEncoder().encodeToString(cdr) + "\",\"indCdrGenerado\":\"1\"}")));
        EstadoDelTicket e = gateway(wm).consultar(produccion, TICKET);
        assertThat(e.correcto()).isTrue();
        assertThat(e.cdrZip()).isEqualTo(cdr);
        assertThat(e.enlace()).isEqualTo("https://e-factura.sunat.gob.pe/v1/contribuyente/gre/comprobantes/descargaqr?hashqr=abc");
    }

    @Test void elTicketConErrorConYSinCdr(WireMockRuntimeInfo wm) throws Exception {
        tokenOk();
        stubFor(get(RUTA_TICKET).willReturn(okJson("{\"codRespuesta\":\"99\",\"error\":{\"numError\":\"1345\",\"desError\":\"El RUC no valido\"},\"indCdrGenerado\":\"0\"}")));
        EstadoDelTicket sin = gateway(wm).consultar(produccion, TICKET);
        assertThat(sin.conError()).isTrue();
        assertThat(sin.conCdr()).isFalse();
        assertThat(sin.numError()).isEqualTo("1345");
        assertThat(sin.desError()).isEqualTo("El RUC no valido");

        byte[] cdr = cdr("2555", "sin url");
        stubFor(get(RUTA_TICKET).willReturn(okJson("{\"codRespuesta\":\"99\",\"error\":{\"numError\":\"2555\",\"desError\":\"Destinatario no debe ser igual al remitente\"},\"arcCdr\":\""
                + Base64.getEncoder().encodeToString(cdr) + "\",\"indCdrGenerado\":\"1\"}")));
        EstadoDelTicket con = gateway(wm).consultar(produccion, TICKET);
        assertThat(con.conCdr()).isTrue();
        assertThat(con.enlace()).as("solo una URL https").isNull();
        assertThat(con.numError()).isEqualTo("2555");

        // Un CDR que se anuncia pero no se genera (indCdrGenerado 0) no se lee.
        stubFor(get(RUTA_TICKET).willReturn(okJson("{\"codRespuesta\":\"99\",\"arcCdr\":\"basura\",\"indCdrGenerado\":\"0\"}")));
        assertThat(gateway(wm).consultar(produccion, TICKET).conCdr()).isFalse();
    }

    @Test void unTicketDesconocidoEsUnRechazoConSuCodigo(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(RUTA_TICKET).willReturn(aResponse().withStatus(422).withBody("{\"cod\":\"422\",\"msg\":\"Unprocessable Entity\",\"exc\":null,\"errors\":[{\"codError\":\"0509\",\"desError\":\"Número de ticket no existe\"}]}")));
        assertThatThrownBy(() -> gateway(wm).consultar(produccion, TICKET)).isInstanceOf(SunatRechazoException.class).hasFieldOrPropertyWithValue("codigo", "0509");
        stubFor(get(RUTA_TICKET).willReturn(okJson("{\"indCdrGenerado\":\"0\"}")));
        assertThatThrownBy(() -> gateway(wm).consultar(produccion, TICKET)).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("codRespuesta");
        assertThatThrownBy(() -> gateway(wm).consultar(produccion, "../envios/x")).isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("codigo", "TICKET_INVALIDO");
    }

    @Test void unaEmpresaEnBetaNoEnviaSinUnaApiDePruebasConfigurada(WireMockRuntimeInfo wm) {
        Tenant beta = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA DE PRUEBA S.A.C.", Entorno.BETA, new CredencialesSol("USUARIO1", CLAVE_SOL), null)
                .conCredencialesApiSunat(new CredencialesApiSunat(ID, SECRETO));
        RestGreGateway sinBeta = gateway(wm);
        assertThat(sinBeta.disponible(Entorno.BETA)).isFalse();
        assertThat(sinBeta.disponible(Entorno.PRODUCCION)).isTrue();
        assertThatThrownBy(() -> sinBeta.enviar(beta, ARCHIVO, XML)).isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("codigo", "GRE_NO_DISPONIBLE");
        verify(0, postRequestedFor(anyUrl()));

        // H6: la beta pide el token a su propio servicio, no al de producción.
        stubFor(post("/beta-token" + RUTA_TOKEN).willReturn(okJson("{\"access_token\":\"tk-beta\",\"expires_in\":3600}")));
        stubFor(post("/beta" + RUTA_ENVIO).willReturn(okJson("{\"numTicket\":\"" + TICKET + "\"}")));
        RestGreGateway conBeta = gateway(wm, wm.getHttpBaseUrl() + "/beta/");
        assertThat(conBeta.disponible(Entorno.BETA)).isTrue();
        assertThat(conBeta.enviar(beta, ARCHIVO, XML).ticket()).isEqualTo(TICKET);
        verify(0, postRequestedFor(urlEqualTo(RUTA_TOKEN)));
        verify(postRequestedFor(urlEqualTo("/beta" + RUTA_ENVIO)).withHeader("Authorization", equalTo("Bearer tk-beta")));
    }

    /** H1: apagado en el servidor, no se envía nada (ni el token) aunque la empresa esté en PRODUCCION; la consulta de un ticket ya dado sigue. */
    @Test void apagadoNoEnviaNadaPeroConsultaLosTicketsQueYaHay(WireMockRuntimeInfo wm) {
        RestGreGateway apagado = new RestGreGateway(false, wm.getHttpBaseUrl(), null, wm.getHttpBaseUrl(), null, new ClienteApiSunat(Duration.ofSeconds(1), Duration.ofSeconds(2), reloj));
        assertThat(apagado.disponible(Entorno.PRODUCCION)).isFalse();
        assertThatThrownBy(() -> apagado.enviar(produccion, ARCHIVO, XML)).isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("codigo", "GRE_NO_DISPONIBLE")
                .hasMessageContaining("SUNAT_API_GRE_HABILITADA");
        verify(0, postRequestedFor(anyUrl()));
        tokenOk();
        stubFor(get(RUTA_TICKET).willReturn(okJson("{\"codRespuesta\":\"98\"}")));
        assertThat(apagado.consultar(produccion, TICKET).enProceso()).isTrue();
    }

    /** H5/H6: una beta que apunte a la API o al token reales de SUNAT no deja arrancar: las empresas en BETA mandarían guías reales o su bearer real a un tercero. */
    @Test void unaBetaQueApuntaASunatRealNoArranca() {
        ClienteApiSunat c = new ClienteApiSunat(Duration.ofSeconds(1), Duration.ofSeconds(2), reloj);
        String prod = "https://api-cpe.sunat.gob.pe", token = "https://api-seguridad.sunat.gob.pe";
        assertThatThrownBy(() -> new RestGreGateway(true, token, "https://gre-test.ejemplo.pe/token", prod, prod, c)).isInstanceOf(IllegalStateException.class).hasMessageContaining("SUNAT_API_GRE_BETA_URL");
        assertThatThrownBy(() -> new RestGreGateway(true, token, "https://gre-test.ejemplo.pe/token", prod, "https://api-cpe.sunat.gob.pe/otra", c)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RestGreGateway(true, token, null, prod, "https://gre-test.ejemplo.pe", c)).isInstanceOf(IllegalStateException.class).hasMessageContaining("SUNAT_API_GRE_SEGURIDAD_BETA_URL");
        assertThatThrownBy(() -> new RestGreGateway(true, token, token, prod, "https://gre-test.ejemplo.pe", c)).isInstanceOf(IllegalStateException.class).hasMessageContaining("token real");
        assertThatThrownBy(() -> new RestGreGateway(true, token, token + "/v2", prod, "https://gre-test.ejemplo.pe", c)).isInstanceOf(IllegalStateException.class);
        assertThat(new RestGreGateway(true, token, "https://gre-test.ejemplo.pe/token", prod, "https://gre-test.ejemplo.pe", c).disponible(Entorno.BETA)).isTrue();
        assertThat(new RestGreGateway(true, token, null, prod, "", c).disponible(Entorno.BETA)).isFalse();
    }

    @Test void sinCredencialesApiOSolNoSeLlamaASunat(WireMockRuntimeInfo wm) {
        Tenant sinApi = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA", Entorno.PRODUCCION, new CredencialesSol("USUARIO1", CLAVE_SOL), null);
        assertThatThrownBy(() -> gateway(wm).enviar(sinApi, ARCHIVO, XML)).hasFieldOrPropertyWithValue("codigo", "CREDENCIALES_API_SUNAT_NO_CARGADAS");
        Tenant sinSol = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA", Entorno.PRODUCCION, null, null).conCredencialesApiSunat(new CredencialesApiSunat(ID, SECRETO));
        assertThatThrownBy(() -> gateway(wm).consultar(sinSol, TICKET)).hasFieldOrPropertyWithValue("codigo", "CREDENCIALES_SOL_NO_CARGADAS");
        assertThatThrownBy(() -> gateway(wm).enviar(produccion, "20100066603-01-F001-1", XML)).isInstanceOf(IllegalArgumentException.class);
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test void laFechaDeRecepcionQueNoSeEntiendeNoPierdeElTicket() {
        assertThat(RestGreGateway.fecha("2022-04-11T15:37:41")).isEqualTo(LocalDateTime.of(2022, 4, 11, 15, 37, 41));
        assertThat(RestGreGateway.fecha("11/04/2022")).isNull();
        assertThat(RestGreGateway.fecha(null)).isNull();
        assertThat(RestGreGateway.enlace(new byte[]{1, 2, 3})).isNull();
        assertThat(List.of(RestGreGateway.sha256(new byte[0]))).containsExactly("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
