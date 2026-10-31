package pe.factura.adapters.sunatrest;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import pe.factura.adapters.sunatrest.CorridaDeValidez.Datos;
import pe.factura.adapters.sunatrest.CorridaDeValidez.Paso;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La corrida controlada de la validez (#318) contra un SUNAT simulado. Se prueba la corrida y no solo el gateway porque es lo que se ejecuta el día que se hace
 * la prueba real con credenciales de verdad: ese día no puede estrenar código. Aquí se comprueba que sabe preguntar, que reconoce un contrato roto y que su evidencia
 * no lleva el secreto, el token ni el «Id» entero.
 */
@WireMockTest
class CorridaDeValidezTest {
    static final String ID = "d20b421e-4ace-46d8-ac8c-4fbb44cfde17";
    static final String SECRETO = "HhWwZVo8ZaWm4BZ7n/xEzw==";
    static final String TOKEN = "eyJ0b2tlbi1kZS1wcnVlYmEifQ.firma-secreta";
    static final String RUTA_CONSULTA = "/v1/contribuyente/contribuyentes/20601234565/validarcomprobante";

    static Datos datos(WireMockRuntimeInfo wm, String clientId, String secreto) {
        return new Datos(wm.getHttpBaseUrl(), wm.getHttpBaseUrl(), "20601234565", clientId, secreto, "20601234565", "01", "F001", 15, LocalDate.of(2026, 10, 20), new BigDecimal("118.00"));
    }

    /** El SUNAT de los manuales: rechaza el cliente inventado, da token a las credenciales buenas y contesta cada consulta según lo que se le pregunta. */
    static void sunatQueRespondeComoElManual() {
        stubFor(post(urlPathEqualTo("/v1/clientesextranet/" + CorridaDeValidez.ID_INVENTADO + "/oauth2/token/"))
                .willReturn(badRequest().withBody("{\"error_description\":\"cliente no autorizado\",\"error\":\"unauthorized_client\"}")));
        stubFor(post(urlPathEqualTo("/v1/clientesextranet/" + ID + "/oauth2/token/"))
                .willReturn(okJson("{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        // El que existe (y el mismo sin monto): aceptado.
        stubFor(post(urlPathEqualTo(RUTA_CONSULTA)).withRequestBody(matchingJsonPath("$[?(@.numero == '15' && @.fechaEmision == '20/10/2026')]"))
                .willReturn(okJson("{\"success\":true,\"message\":\"Operation Success\",\"data\":{\"estadoCp\":\"1\",\"estadoRuc\":\"00\",\"condDomiRuc\":\"00\"}}")));
        // Fecha equivocada: SUNAT contesta que no existe con esa fecha, con una observación.
        stubFor(post(urlPathEqualTo(RUTA_CONSULTA)).withRequestBody(matchingJsonPath("$[?(@.numero == '15' && @.fechaEmision == '19/10/2026')]"))
                .willReturn(okJson("{\"success\":true,\"data\":{\"estadoCp\":\"0\",\"estadoRuc\":\"00\",\"condDomiRuc\":\"00\",\"observaciones\":[\"La fecha de emisión no coincide\"]}}")));
        // Correlativo inexistente: no existe.
        stubFor(post(urlPathEqualTo(RUTA_CONSULTA)).withRequestBody(matchingJsonPath("$[?(@.numero == '99999999')]"))
                .willReturn(okJson("{\"success\":true,\"data\":{\"estadoCp\":\"0\",\"estadoRuc\":\"00\",\"condDomiRuc\":\"00\"}}")));
        // Números JSON en vez de texto: este SUNAT simulado los rechaza.
        stubFor(post(urlPathEqualTo(RUTA_CONSULTA)).withRequestBody(matching(".*\"numero\":15,.*"))
                .willReturn(badRequest().withBody("{\"success\":false,\"message\":\"numero debe ser texto\",\"errorCode\":\"1033\"}")));
    }

    @Test
    void conUnSunatQueRespondeComoElManualTodosLosPasosCumplen(WireMockRuntimeInfo wm) {
        sunatQueRespondeComoElManual();
        Datos d = datos(wm, ID, SECRETO);

        List<Paso> pasos = CorridaDeValidez.correr(d);

        assertThat(pasos).extracting(Paso::nombre).containsExactly(
                "credenciales inventadas", "el comprobante existente", "la fecha de emisión equivocada (un día antes)", "un correlativo que no existe", "sin monto",
                "numero y monto como números JSON (no como texto)");
        assertThat(pasos).allSatisfy(p -> assertThat(p.cumple()).as(p.nombre() + " -> " + p.obtenido()).isTrue());
        assertThat(pasos.get(0).obtenido()).contains("SunatCredencialesException 400").contains("unauthorized_client");
        assertThat(pasos.get(1).obtenido()).contains("estadoCp=1").contains("estadoRuc=00").contains("condDomiRuc=00");
        assertThat(pasos.get(2).obtenido()).contains("estadoCp=0").contains("La fecha de emisión no coincide");
        assertThat(pasos.get(3).obtenido()).contains("estadoCp=0");
        assertThat(pasos.get(5).obtenido()).contains("lo rechaza").contains("texto del manual");
    }

    @Test
    void siSunatDiceQueElComprobanteExistenteNoExisteLaCorridaLoMarcaComoQueNoCumple(WireMockRuntimeInfo wm) {
        sunatQueRespondeComoElManual();
        // Con otro número, el «existente» cae en el stub del inexistente.
        Datos d = new Datos(wm.getHttpBaseUrl(), wm.getHttpBaseUrl(), "20601234565", ID, SECRETO, "20601234565", "01", "F001", 99_999_999L, LocalDate.of(2026, 10, 20), null);

        List<Paso> pasos = CorridaDeValidez.correr(d);

        Paso existente = pasos.get(1);
        assertThat(existente.cumple()).isFalse();
        assertThat(existente.obtenido()).contains("estadoCp=0");
        assertThat(CorridaDeValidez.resumen(d, pasos, Instant.parse("2026-10-31T15:00:00Z"))).contains("NO CUMPLE").contains("1 paso(s) no cumplen");
    }

    @Test
    void siSunatRechazaLaConsultaPorSuFormatoElPasoQueExigeUnEstadoNoCumple(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/v1/clientesextranet/" + CorridaDeValidez.ID_INVENTADO + "/oauth2/token/")).willReturn(badRequest().withBody("{\"error\":\"unauthorized_client\"}")));
        stubFor(post(urlPathEqualTo("/v1/clientesextranet/" + ID + "/oauth2/token/")).willReturn(okJson("{\"access_token\":\"" + TOKEN + "\",\"expires_in\":3600}")));
        stubFor(post(urlPathEqualTo(RUTA_CONSULTA)).willReturn(badRequest().withBody("{\"success\":false,\"message\":\"formato de numero inválido\",\"errorCode\":\"1033\"}")));

        List<Paso> pasos = CorridaDeValidez.correr(datos(wm, ID, SECRETO));

        // Es el caso que el pendiente del issue quiere detectar: el contrato tal como lo escribió el manual no es el que SUNAT acepta.
        assertThat(pasos.get(1).cumple()).isFalse();
        assertThat(pasos.get(1).obtenido()).contains("SunatRechazoException 1033").contains("formato de numero inválido");
        // El correlativo inexistente también se rechaza, y un rechazo es una de las formas válidas de decir «no existe» (F3): la puerta es el comprobante que sí existe.
        assertThat(pasos.get(3).cumple()).isTrue();
        // Los pasos informativos registran el rechazo sin romper la corrida.
        assertThat(pasos.get(2).cumple()).isTrue();
        assertThat(pasos.get(4).cumple()).isTrue();
    }

    @Test
    void sinCredencialesSoloCorreElPasoDeLasCredencialesInventadasYElRestoQuedaOmitido(WireMockRuntimeInfo wm) {
        sunatQueRespondeComoElManual();

        List<Paso> pasos = CorridaDeValidez.correr(datos(wm, "", ""));

        assertThat(pasos).hasSize(2);
        assertThat(pasos.get(0).cumple()).isTrue();
        assertThat(pasos.get(1).omitido()).isTrue();
        assertThat(pasos.get(1).obtenido()).contains("VALIDEZ_CLIENT_ID");
        assertThat(CorridaDeValidez.resumen(datos(wm, "", ""), pasos, Instant.parse("2026-10-31T15:00:00Z"))).contains("omitido").contains("La corrida cumple.");
    }

    @Test
    void sinElComprobanteLaCorridaDiceQueFaltaEnVezDeInventarlo(WireMockRuntimeInfo wm) {
        sunatQueRespondeComoElManual();
        Datos d = new Datos(wm.getHttpBaseUrl(), wm.getHttpBaseUrl(), "20601234565", ID, SECRETO, "20601234565", "01", "", 0, null, null);

        List<Paso> pasos = CorridaDeValidez.correr(d);

        assertThat(pasos.get(1).omitido()).isTrue();
        assertThat(pasos.get(1).obtenido()).contains("VALIDEZ_SERIE").contains("VALIDEZ_NUMERO");
    }

    @Test
    void siElServicioDeTokenEstaCaidoNoLoTomaPorCredencialesRechazadas(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathMatching("/v1/clientesextranet/.*")).willReturn(serverError()));

        List<Paso> pasos = CorridaDeValidez.correr(datos(wm, "", ""));

        // Un 5xx es un fallo pasajero: la corrida no puede dar por buena la URL del token con eso.
        assertThat(pasos.get(0).cumple()).isFalse();
        assertThat(pasos.get(0).obtenido()).contains("SunatTransientException").contains("HTTP 500");
    }

    @Test
    void conCredencialesPeroSinElRucPideElRucEnVezDeConsultarConElDelCertificadoDePrueba(WireMockRuntimeInfo wm) {
        sunatQueRespondeComoElManual();
        // El RUC de quien consulta es el de la empresa dueña de las credenciales: sin él, un 403 de SUNAT parecería un problema de permisos de la aplicación.
        Datos d = CorridaDeValidez.deEntorno(Map.of("VALIDEZ_URL_SEGURIDAD", wm.getHttpBaseUrl(), "VALIDEZ_URL_API", wm.getHttpBaseUrl(), "VALIDEZ_CLIENT_ID", ID, "VALIDEZ_CLIENT_SECRET", SECRETO,
                "VALIDEZ_SERIE", "F001", "VALIDEZ_NUMERO", "15", "VALIDEZ_FECHA", "2026-10-20"));

        List<Paso> pasos = CorridaDeValidez.correr(d);

        assertThat(pasos).hasSize(2);
        assertThat(pasos.get(1).omitido()).isTrue();
        assertThat(pasos.get(1).obtenido()).contains("VALIDEZ_RUC");
        assertThat(CorridaDeValidez.resumen(d, pasos, Instant.parse("2026-10-31T15:00:00Z"))).doesNotContain("20100066603").contains("(sin indicar)");
    }

    @Test
    void unaVariableQueNoSeEntiendeDiceCualEs() {
        for (Map.Entry<String, String> mala : Map.of("VALIDEZ_NUMERO", "abc", "VALIDEZ_FECHA", "20/10/2026", "VALIDEZ_MONTO", "doscientos").entrySet()) {
            assertThatThrownBy(() -> CorridaDeValidez.deEntorno(Map.of(mala.getKey(), mala.getValue())))
                    .as(mala.getKey()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(mala.getKey()).hasMessageContaining(mala.getValue());
        }
    }

    @Test
    void siSunatRechazaElCorrelativoInexistenteEsUnaRespuestaValidaYNoUnContratoRoto(WireMockRuntimeInfo wm) {
        sunatQueRespondeComoElManual();
        // «No existe» puede llegar como estadoCp 0 o como un rechazo con su mensaje: las dos son la respuesta de SUNAT. La puerta del contrato es el comprobante que sí existe.
        stubFor(post(urlPathEqualTo(RUTA_CONSULTA)).withRequestBody(matchingJsonPath("$[?(@.numero == '99999999')]"))
                .willReturn(badRequest().withBody("{\"success\":false,\"message\":\"El comprobante no existe\",\"errorCode\":\"1031\"}")));

        List<Paso> pasos = CorridaDeValidez.correr(datos(wm, ID, SECRETO));

        Paso inexistente = pasos.get(3);
        assertThat(inexistente.cumple()).isTrue();
        assertThat(inexistente.obtenido()).contains("SunatRechazoException 1031").contains("El comprobante no existe");
        assertThat(pasos.get(1).cumple()).isTrue();
    }

    @Test
    void siSunatDiceQueElCorrelativoInexistenteEstaAceptadoLaCorridaLoMarcaComoQueNoCumple(WireMockRuntimeInfo wm) {
        sunatQueRespondeComoElManual();
        stubFor(post(urlPathEqualTo(RUTA_CONSULTA)).withRequestBody(matchingJsonPath("$[?(@.numero == '99999999')]"))
                .willReturn(okJson("{\"success\":true,\"data\":{\"estadoCp\":\"1\",\"estadoRuc\":\"00\",\"condDomiRuc\":\"00\"}}")));

        assertThat(CorridaDeValidez.correr(datos(wm, ID, SECRETO)).get(3).cumple()).isFalse();
    }

    @Test
    void laEvidenciaNoLlevaElSecretoNiElTokenNiElIdEntero(WireMockRuntimeInfo wm) {
        sunatQueRespondeComoElManual();
        Datos d = datos(wm, ID, SECRETO);

        String md = CorridaDeValidez.resumen(d, CorridaDeValidez.correr(d), Instant.parse("2026-10-31T15:00:00Z"));

        assertThat(md).doesNotContain(SECRETO).doesNotContain(TOKEN).doesNotContain("Bearer").doesNotContain(ID);
        assertThat(md).contains("d20b421e…").contains("20601234565 01 F001-15 del 2026-10-20 por 118.00").contains("La corrida cumple.");
    }

    @Test
    void leeLaCorridaRealDelEntornoYSinNadaSoloPreguntaPorLasCredencialesInventadas() {
        Datos vacio = CorridaDeValidez.deEntorno(Map.of());
        assertThat(vacio.conCredenciales()).isFalse();
        assertThat(vacio.conComprobante()).isFalse();
        assertThat(vacio.urlSeguridad()).isEqualTo("https://api-seguridad.sunat.gob.pe");
        assertThat(vacio.urlApi()).isEqualTo("https://api.sunat.gob.pe");

        Datos completo = CorridaDeValidez.deEntorno(Map.of(
                "VALIDEZ_RUC", "20601234565", "VALIDEZ_CLIENT_ID", ID, "VALIDEZ_CLIENT_SECRET", SECRETO, "VALIDEZ_TIPO", "03",
                "VALIDEZ_SERIE", "B001", "VALIDEZ_NUMERO", "749", "VALIDEZ_FECHA", "2026-10-20", "VALIDEZ_MONTO", "236.00"));
        assertThat(completo.conCredenciales()).isTrue();
        assertThat(completo.conComprobante()).isTrue();
        // El emisor, si no se dice, es la propia empresa que consulta.
        assertThat(completo.rucEmisor()).isEqualTo("20601234565");
        assertThat(completo.tipo()).isEqualTo("03");
        assertThat(completo.numero()).isEqualTo(749);
        assertThat(completo.fecha()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(completo.monto()).isEqualByComparingTo("236.00");
    }
}
