package pe.factura.adapters.sunatrest;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatApiValidezGateway.Validez;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.CredencialesSol;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * La consulta integrada de validez por la API REST de SUNAT (#318) contra un SUNAT simulado (WireMock): token OAuth2 con caché y renovación, la consulta y
 * cada error que decide si el servicio cae a SOAP. Las rutas y campos son los del manual de SUNAT (ver {@link RestValidezGateway}).
 */
@WireMockTest
class RestValidezGatewayTest {
    static final String ID = "d20b421e-4ace-46d8-ac8c-4fbb44cfde17";
    static final String SECRETO = "HhWwZVo8ZaWm4BZ7n/xEzw==";
    static final String TOKEN = "eyJ0b2tlbi1kZS1wcnVlYmEifQ.firma";
    static final String RUTA_TOKEN = "/v1/clientesextranet/" + ID + "/oauth2/token/";
    static final String RUTA_CONSULTA = "/v1/contribuyente/contribuyentes/20601234565/validarcomprobante";

    /** Reloj que avanza a mano: así se prueba el vencimiento del token sin esperar una hora. */
    static final class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-10-20T15:00:00Z");
        public ZoneId getZone() { return ZoneId.of("America/Lima"); }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return ahora; }
        void avanzar(Duration d) { ahora = ahora.plus(d); }
    }

    final Reloj reloj = new Reloj();
    final Tenant empresa = new Tenant(UUID.randomUUID(), "20601234565", "COMPRADORA SAC", Entorno.PRODUCCION, new CredencialesSol("USUARIO1", "clave-sol"), null)
            .conCredencialesApiSunat(new CredencialesApiSunat(ID, SECRETO));

    RestValidezGateway gateway(WireMockRuntimeInfo wm) { return gateway(wm, Duration.ofSeconds(2)); }

    RestValidezGateway gateway(WireMockRuntimeInfo wm, Duration espera) {
        UrlPorEntorno base = new UrlPorEntorno(wm.getHttpBaseUrl() + "/", wm.getHttpBaseUrl());
        return new RestValidezGateway(base, base, new ClienteApiSunat(Duration.ofSeconds(1), espera, reloj));
    }

    static void tokenOk(String token, long expiraEn) {
        stubFor(post(RUTA_TOKEN).willReturn(okJson("{\"access_token\":\"" + token + "\",\"token_type\":\"JWT\",\"expires_in\":" + expiraEn + "}")));
    }

    static String respuesta(String estadoCp, String estadoRuc, String cond, String obs) {
        return "{\"success\":true,\"message\":\"Operation Success! \",\"data\":{\"estadoCp\":\"" + estadoCp + "\",\"estadoRuc\":\"" + estadoRuc + "\",\"condDomiRuc\":\"" + cond
                + "\",\"observaciones\":[" + obs + "]}}";
    }

    Validez consultar(RestValidezGateway g) {
        return g.validar(empresa, "20100066603", "01", "E001", 749, LocalDate.of(2018, 6, 29), new BigDecimal("236"));
    }

    @Test void pideElTokenConClientCredentialsYConsultaConElBearer(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(okJson(respuesta("1", "00", "00",
                "\"- Fecha de emisión ingresada no coincide con la registrada en la SUNAT.\", \"- Receptor del comprobante de pago NO corresponde con el número de RUC que invoca al servicio web.\""))));

        Validez v = consultar(gateway(wm));

        assertThat(v.estadoCp()).isEqualTo("1");
        assertThat(v.estadoRuc()).isEqualTo("00");
        assertThat(v.condicionDomicilio()).isEqualTo("00");
        assertThat(v.observaciones()).hasSize(2).first().asString().startsWith("- Fecha de emisión");
        verify(postRequestedFor(urlEqualTo(RUTA_TOKEN))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("scope=https%3A%2F%2Fapi.sunat.gob.pe%2Fv1%2Fcontribuyente%2Fcontribuyentes"))
                .withRequestBody(containing("client_id=" + ID))
                .withRequestBody(containing("client_secret=HhWwZVo8ZaWm4BZ7n%2FxEzw%3D%3D")));
        // La ruta lleva el RUC de quien consulta (la empresa); el cuerpo, el del emisor del comprobante. Número y monto como texto, como el ejemplo del manual.
        verify(postRequestedFor(urlEqualTo(RUTA_CONSULTA))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"numRuc\":\"20100066603\",\"codComp\":\"01\",\"numeroSerie\":\"E001\",\"numero\":\"749\",\"fechaEmision\":\"29/06/2018\",\"monto\":\"236.00\"}")));
    }

    @Test void sinMontoNoLoManda(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(okJson(respuesta("3", "00", "00", ""))));

        gateway(wm).validar(empresa, "20100066603", "01", "0001", 15, LocalDate.of(2020, 1, 2), null);

        verify(postRequestedFor(urlEqualTo(RUTA_CONSULTA)).withRequestBody(equalToJson("{\"numRuc\":\"20100066603\",\"codComp\":\"01\",\"numeroSerie\":\"0001\",\"numero\":\"15\",\"fechaEmision\":\"02/01/2020\"}")));
    }

    @Test void cadaEstadoDelComprobanteLlegaComoLoDaSunat(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        RestValidezGateway g = gateway(wm);
        for (String estado : new String[]{"0", "1", "2", "3", "4"}) {
            stubFor(post(RUTA_CONSULTA).willReturn(okJson(respuesta(estado, "01", "12", ""))));
            Validez v = consultar(g);
            assertThat(v.estadoCp()).as(estado).isEqualTo(estado);
            assertThat(v.estadoRuc()).isEqualTo("01");
            assertThat(v.condicionDomicilio()).isEqualTo("12");
            assertThat(v.observaciones()).isEmpty();
        }
    }

    /** El manual escribe «data.Observaciones» en la tabla y «observaciones» en el ejemplo; y un estadoCp numérico (la tabla dice Integer) también vale. */
    @Test void aceptaObservacionesConMayusculaYEstadoNumerico(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(okJson("{\"success\":true,\"data\":{\"estadoCp\":2,\"Observaciones\":[\"- Comunicado en baja\"]}}")));

        Validez v = consultar(gateway(wm));

        assertThat(v.estadoCp()).isEqualTo("2");
        assertThat(v.estadoRuc()).isNull();
        assertThat(v.observaciones()).containsExactly("- Comunicado en baja");
    }

    @Test void elTokenSeReusaMientrasEsteVigente(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(okJson(respuesta("1", "00", "00", ""))));
        RestValidezGateway g = gateway(wm);

        consultar(g);
        reloj.avanzar(Duration.ofMinutes(50));
        consultar(g);
        consultar(g);

        verify(1, postRequestedFor(urlEqualTo(RUTA_TOKEN)));
        verify(3, postRequestedFor(urlEqualTo(RUTA_CONSULTA)));
    }

    /** Se renueva un minuto antes de que venza: un token que expira a mitad de la llamada haría fallar la consulta. */
    @Test void elTokenPorVencerSeRenueva(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(okJson(respuesta("1", "00", "00", ""))));
        RestValidezGateway g = gateway(wm);

        consultar(g);
        reloj.avanzar(Duration.ofSeconds(3600 - 59));
        tokenOk("token-nuevo", 3600);
        consultar(g);

        verify(2, postRequestedFor(urlEqualTo(RUTA_TOKEN)));
        verify(postRequestedFor(urlEqualTo(RUTA_CONSULTA)).withHeader("Authorization", equalTo("Bearer token-nuevo")));
    }

    /** Otra credencial (la empresa generó una clave nueva en SOL) no reusa el token de la anterior. */
    @Test void cambiarElSecretoPideOtroToken(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(okJson(respuesta("1", "00", "00", ""))));
        RestValidezGateway g = gateway(wm);

        consultar(g);
        g.validar(empresa.conCredencialesApiSunat(new CredencialesApiSunat(ID, "otro-secreto")), "20100066603", "01", "E001", 749, LocalDate.of(2018, 6, 29), null);

        verify(2, postRequestedFor(urlEqualTo(RUTA_TOKEN)));
    }

    /** SUNAT puede revocar un token antes de su expires_in: un 401 de la consulta descarta el token, pide otro y reintenta una sola vez. */
    @Test void unTokenRechazadoSeRenuevaUnaVez(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).inScenario("token").whenScenarioStateIs(Scenario.STARTED).willSetStateTo("renovado")
                .willReturn(okJson("{\"access_token\":\"viejo\",\"expires_in\":3600}")));
        stubFor(post(RUTA_TOKEN).inScenario("token").whenScenarioStateIs("renovado")
                .willReturn(okJson("{\"access_token\":\"nuevo\",\"expires_in\":3600}")));
        stubFor(post(RUTA_CONSULTA).withHeader("Authorization", equalTo("Bearer viejo")).willReturn(aResponse().withStatus(401)));
        stubFor(post(RUTA_CONSULTA).withHeader("Authorization", equalTo("Bearer nuevo")).willReturn(okJson(respuesta("1", "00", "00", ""))));

        assertThat(consultar(gateway(wm)).estadoCp()).isEqualTo("1");
        verify(2, postRequestedFor(urlEqualTo(RUTA_TOKEN)));
        verify(2, postRequestedFor(urlEqualTo(RUTA_CONSULTA)));
    }

    @Test void unTokenRechazadoAunRenovadoSonLasCredenciales(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(aResponse().withStatus(401).withBody("{\"cod\":\"401\",\"msg\":\"Unauthorized - Fallo en la autenticación del Cliente\"}")));

        assertThatThrownBy(() -> consultar(gateway(wm))).isInstanceOf(SunatCredencialesException.class).hasMessageContaining("Fallo en la autenticación");
        verify(2, postRequestedFor(urlEqualTo(RUTA_CONSULTA)));
    }

    @Test void credencialesRechazadasAlPedirElTokenNoSeReintentan(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid_client\",\"error_description\":\"cliente no autorizado\"}")));

        assertThatThrownBy(() -> consultar(gateway(wm))).isInstanceOf(SunatCredencialesException.class)
                .hasMessageContaining("invalid_client").hasMessageContaining("HTTP 401");
        verify(1, postRequestedFor(urlEqualTo(RUTA_TOKEN)));
        verify(0, postRequestedFor(urlEqualTo(RUTA_CONSULTA)));
    }

    @Test void unaAplicacionSinPermisoParaElServicioSonLasCredenciales(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(aResponse().withStatus(403).withBody("{\"cod\":\"403\",\"msg\":\"Forbidden\"}")));

        assertThatThrownBy(() -> consultar(gateway(wm))).isInstanceOf(SunatCredencialesException.class).hasMessageContaining("403");
    }

    @Test void un5xxDelServicioDeTokenOdeLaConsultaEsTransitorio(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).willReturn(aResponse().withStatus(503)));
        Throwable e = catchThrowable(() -> consultar(gateway(wm)));
        assertThat(e).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("503");

        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(aResponse().withStatus(500).withBody("{\"cod\":\"500\",\"msg\":\"Internal Server Error\",\"exc\":\"java.lang.NullPointerException\"}")));
        e = catchThrowable(() -> consultar(gateway(wm)));
        assertThat(e).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("HTTP 500").hasMessageContaining("Internal Server Error")
                .as("la traza de SUNAT no se repite").hasMessageNotContaining("NullPointerException");
    }

    @Test void unaConsultaQueNoRespondeATiempoEsTransitoria(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(okJson(respuesta("1", "00", "00", "")).withFixedDelay(1500)));

        assertThatThrownBy(() -> consultar(gateway(wm, Duration.ofMillis(300)))).isExactlyInstanceOf(SunatTransientException.class)
                .extracting("codigo").isEqualTo("0109");
    }

    @Test void sinServidorEsTransitorio() {
        UrlPorEntorno nadie = new UrlPorEntorno("http://127.0.0.1:1", "http://127.0.0.1:1");
        RestValidezGateway g = new RestValidezGateway(nadie, nadie, new ClienteApiSunat(Duration.ofMillis(500), Duration.ofSeconds(1), reloj));
        assertThatThrownBy(() -> consultar(g)).isExactlyInstanceOf(SunatTransientException.class);
    }

    /** Un 422 es la respuesta de SUNAT sobre los datos de la consulta: no es una caída, y el servicio no cae a SOAP por él. */
    @Test void un422EsUnRechazoConElDetalleDeSunat(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(aResponse().withStatus(422).withBody("{\"cod\":\"422\",\"msg\":\"Unprocessable Entity\",\"errors\":[{\"codError\":\"166\",\"desError\":\"Número de serie inválido.\"}]}")));

        assertThatThrownBy(() -> consultar(gateway(wm))).isInstanceOf(SunatRechazoException.class)
                .hasMessageContaining("166 Número de serie inválido.").extracting("codigo").isEqualTo("422");
    }

    @Test void successFalseEsUnRechazo(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(okJson("{\"success\":false,\"message\":\"Unexpected character ('}')\",\"errorCode\":\"00\"}")));

        assertThatThrownBy(() -> consultar(gateway(wm))).isInstanceOf(SunatRechazoException.class).extracting("codigo").isEqualTo("00");
    }

    @Test void unaRespuestaSinEstadoEsTransitoria(WireMockRuntimeInfo wm) {
        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(okJson("{\"success\":true,\"data\":{}}")));
        assertThatThrownBy(() -> consultar(gateway(wm))).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("estadoCp");

        stubFor(post(RUTA_CONSULTA).willReturn(aResponse().withStatus(200).withBody("<html>mantenimiento</html>")));
        assertThatThrownBy(() -> consultar(gateway(wm))).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("JSON");

        stubFor(post(RUTA_TOKEN).willReturn(okJson("{\"token_type\":\"JWT\"}")));
        assertThatThrownBy(() -> consultar(gateway(wm))).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("access_token");
    }

    /** Una redirección se llevaría el Authorization (o el secreto del formulario) a otro servidor: no se sigue. */
    @Test void noSigueRedirecciones(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).willReturn(aResponse().withStatus(302).withHeader("Location", wm.getHttpBaseUrl() + "/robado")));

        assertThatThrownBy(() -> consultar(gateway(wm))).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("302");
        verify(0, postRequestedFor(urlEqualTo("/robado")));
    }

    /** Ni el secreto ni el token salen en un error, aunque SUNAT los repita en su respuesta. */
    @Test void niElSecretoNiElTokenAparecenEnLosErrores(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid_client\",\"error_description\":\"client_secret " + SECRETO + " no válido\"}")));
        Throwable e = catchThrowable(() -> consultar(gateway(wm)));
        assertThat(e).hasMessageNotContaining(SECRETO).hasMessageContaining("***");

        tokenOk(TOKEN, 3600);
        stubFor(post(RUTA_CONSULTA).willReturn(aResponse().withStatus(422).withBody("{\"msg\":\"" + SECRETO + "\"}")));
        assertThat(catchThrowable(() -> consultar(gateway(wm)))).hasMessageNotContaining(SECRETO).hasMessageNotContaining(TOKEN);
        stubFor(post(RUTA_CONSULTA).willReturn(aResponse().withStatus(500)));
        assertThat(catchThrowable(() -> consultar(gateway(wm)))).hasMessageNotContaining(SECRETO).hasMessageNotContaining(TOKEN);
    }

    @Test void laUrlDelTokenYLaDeLaConsultaSiguenAlEntornoDeLaEmpresa(WireMockRuntimeInfo wm) {
        UrlPorEntorno seguridad = new UrlPorEntorno(wm.getHttpBaseUrl() + "/beta-seg", wm.getHttpBaseUrl() + "/prod-seg");
        UrlPorEntorno api = new UrlPorEntorno(wm.getHttpBaseUrl() + "/beta-api", wm.getHttpBaseUrl() + "/prod-api");
        stubFor(post("/beta-seg" + RUTA_TOKEN).willReturn(okJson("{\"access_token\":\"b\",\"expires_in\":3600}")));
        stubFor(post("/beta-api" + RUTA_CONSULTA).willReturn(okJson(respuesta("1", "00", "00", ""))));
        Tenant enBeta = new Tenant(empresa.id(), empresa.ruc(), empresa.razonSocial(), Entorno.BETA, null, null).conCredencialesApiSunat(empresa.apiSunat());

        new RestValidezGateway(seguridad, api, new ClienteApiSunat(Duration.ofSeconds(1), Duration.ofSeconds(2), reloj))
                .validar(enBeta, "20100066603", "01", "E001", 749, LocalDate.of(2018, 6, 29), null);

        verify(postRequestedFor(urlEqualTo("/beta-api" + RUTA_CONSULTA)));
    }
}
