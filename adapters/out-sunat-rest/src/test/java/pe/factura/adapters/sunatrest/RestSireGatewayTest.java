package pe.factura.adapters.sunatrest;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatCredencialesException;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatSireGateway.ArchivoDelTicket;
import pe.factura.application.port.out.SunatSireGateway.EstadoDelTicket;
import pe.factura.application.port.out.SunatSireGateway.Fase;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.sire.PeriodoSire;
import pe.factura.domain.tenant.CredencialesApiSunat;
import pe.factura.domain.tenant.CredencialesSol;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * #322: la propuesta del RVIE por la API del SIRE contra un SUNAT simulado (WireMock) que responde lo que dice el «Manual del servicio API del Registro de Ventas e
 * Información Electrónica» v31 (secciones 1, 5.1, 5.16, 5.17 y 5.18). No es una prueba contra SUNAT: no hay credenciales API reales (ver deploy/README.md §15 y la
 * corrida controlada {@code sireReal}).
 */
@WireMockTest
class RestSireGatewayTest {
    static final String ID = "d20b421e-4ace-46d8-ac8c-4fbb44cfde17";
    static final String SECRETO = "HhWwZVo8ZaWm4BZ7n/xEzw==";
    static final String CLAVE_SOL = "clave-sol-secreta";
    static final String TOKEN = "eyJ0b2tlbi1kZS1wcnVlYmEifQ.firma";
    static final String RUTA_TOKEN = "/v1/clientessol/" + ID + "/oauth2/token/";
    static final String TICKET = "202600000123";
    static final PeriodoSire PERIODO = PeriodoSire.de("202610");
    static final String RUTA_PEDIR = "/v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/202610/exportapropuesta";
    static final String RUTA_TICKETS = "/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets";
    static final String RUTA_ARCHIVO = "/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte";

    final Clock reloj = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Lima"));
    final Tenant empresa = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA DE PRUEBA S.A.C.", Entorno.PRODUCCION, new CredencialesSol("USUARIO1", CLAVE_SOL), null)
            .conCredencialesApiSunat(new CredencialesApiSunat(ID, SECRETO));

    RestSireGateway gateway(WireMockRuntimeInfo wm, boolean habilitada) {
        UrlPorEntorno url = new UrlPorEntorno(wm.getHttpBaseUrl(), wm.getHttpBaseUrl());
        return new RestSireGateway(habilitada, url, url, new ClienteApiSunat(Duration.ofSeconds(1), Duration.ofSeconds(2), reloj));
    }
    RestSireGateway gateway(WireMockRuntimeInfo wm) { return gateway(wm, true); }

    static void tokenOk() {
        stubFor(post(RUTA_TOKEN).willReturn(okJson("{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"JWT\",\"expires_in\":3600}")));
    }

    static String registro(String codigoEstado, String descripcion, String archivos) {
        return "{\"paginacion\":{\"page\":1,\"perPage\":20,\"totalRegistros\":1},\"registros\":[{\"showReportesDescarga\":1,\"perTributario\":\"202610\",\"numTicket\":\"" + TICKET + "\","
                + "\"codProceso\":\"13\",\"desProceso\":\"Exportar propuesta\",\"codEstadoProceso\":\"" + codigoEstado + "\",\"desEstadoProceso\":\"" + descripcion + "\","
                + "\"archivoReporte\":" + archivos + "}]}";
    }

    // --- pedir la propuesta -----------------------------------------------------------------------------------------------------------------

    @Test void pideLaPropuestaEnTextoConElTokenDelGrantPasswordYDevuelveElTicket(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_PEDIR)).willReturn(okJson("{\"numTicket\":\"" + TICKET + "\"}")));

        assertThat(gateway(wm).solicitarPropuesta(empresa, PERIODO)).isEqualTo(TICKET);

        LoggedRequest token = findAll(postRequestedFor(urlEqualTo(RUTA_TOKEN))).get(0);
        String form = URLDecoder.decode(token.getBodyAsString(), StandardCharsets.UTF_8);
        assertThat(form).contains("grant_type=password").contains("scope=https://api-sire.sunat.gob.pe").contains("client_id=" + ID)
                .contains("username=20100066603USUARIO1").contains("password=" + CLAVE_SOL);
        assertThat(token.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded");
        verify(getRequestedFor(urlEqualTo(RUTA_PEDIR + "?codTipoArchivo=0")).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test void unaRespuestaSinTicketEsTransitoria(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_PEDIR)).willReturn(okJson("{}")));
        assertThatThrownBy(() -> gateway(wm).solicitarPropuesta(empresa, PERIODO)).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("numTicket");
    }

    @Test void unPeriodoSinComprobantesEsUnRechazoConElCodigoDelDetalle(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_PEDIR)).willReturn(aResponse().withStatus(422).withBody("{\"cod\":\"422\",\"msg\":\"Unprocessable Entity\",\"errors\":[{\"cod\":\"1070\","
                + "\"msg\":\"No se ha encontrado información de comprobantes de pago en Periodo Seleccionado\"}]}")));
        assertThatThrownBy(() -> gateway(wm).solicitarPropuesta(empresa, PERIODO)).isInstanceOf(SunatRechazoException.class)
                .hasFieldOrPropertyWithValue("codigo", "1070").hasMessageContaining("No se ha encontrado información");
    }

    @Test void lasCredencialesQueSunatNoAceptaNoSeReintentanYNoSeFiltranLosSecretos(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unauthorized_client\",\"error_description\":\"clave " + CLAVE_SOL + " " + SECRETO + "\"}")));
        Throwable t = catchThrowable(() -> gateway(wm).solicitarPropuesta(empresa, PERIODO));
        assertThat(t).isInstanceOf(SunatCredencialesException.class).hasMessageContaining("unauthorized_client").hasMessageNotContaining(CLAVE_SOL).hasMessageNotContaining(SECRETO);
        verify(0, getRequestedFor(urlPathEqualTo(RUTA_PEDIR)));
    }

    @Test void unaAplicacionSinPermisoParaElSireEsUnProblemaDeCredenciales(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_PEDIR)).willReturn(aResponse().withStatus(403).withBody("{\"cod\":\"403\",\"msg\":\"Forbidden\"}")));
        assertThatThrownBy(() -> gateway(wm).solicitarPropuesta(empresa, PERIODO)).isInstanceOf(SunatCredencialesException.class).hasMessageContaining("403");
    }

    @Test void unaCaidaDeSunatEsTransitoria(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_PEDIR)).willReturn(aResponse().withStatus(503).withBody("{\"cod\":\"503\",\"msg\":\"Service Unavailable\"}")));
        assertThatThrownBy(() -> gateway(wm).solicitarPropuesta(empresa, PERIODO)).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("503");
    }

    // --- el estado del ticket ---------------------------------------------------------------------------------------------------------------

    @Test void consultaElTicketPorSuNumeroEnElPeriodo(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).willReturn(okJson(registro("05", "En proceso", "[]"))));

        EstadoDelTicket e = gateway(wm).consultar(empresa, PERIODO, TICKET);

        assertThat(e.fase()).isEqualTo(Fase.EN_PROCESO);
        assertThat(e.codigoSunat()).isEqualTo("05");
        assertThat(e.descripcionSunat()).isEqualTo("En proceso");
        assertThat(e.archivos()).isEmpty();
        verify(getRequestedFor(urlEqualTo(RUTA_TICKETS + "?perIni=202610&perFin=202610&page=1&perPage=20&numTicket=" + TICKET + "&codLibro=140000&codOrigenEnvio=2"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test void unTicketTerminadoTraeLosArchivosConLoQueLaDescargaPide(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).willReturn(okJson(registro("06", "Terminado", "[{\"codTipoAchivoReporte\":\"01\",\"nomArchivoReporte\":\"20100066603-CPF-202610-01.zip\","
                + "\"nomArchivoContenido\":\"20100066603-CPF-202610-01.txt\"},{\"codTipoAchivoReporte\":null,\"nomArchivoReporte\":\"20100066603-CPF-202610-02.zip\"},{\"nomArchivoReporte\":\"\"}]"))));

        EstadoDelTicket e = gateway(wm).consultar(empresa, PERIODO, TICKET);

        assertThat(e.fase()).isEqualTo(Fase.TERMINADO);
        assertThat(e.archivos()).containsExactly(
                new ArchivoDelTicket(TICKET, "20100066603-CPF-202610-01.zip", "01", "13", "202610"),
                new ArchivoDelTicket(TICKET, "20100066603-CPF-202610-02.zip", null, "13", "202610"));
    }

    @Test void elTipoDeArchivoTambienSeAceptaSinLaErrataDelManual(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).willReturn(okJson(registro("04", "Procesado sin errores", "[{\"codTipoArchivoReporte\":\"02\",\"nomArchivoReporte\":\"a.zip\"}]"))));
        EstadoDelTicket e = gateway(wm).consultar(empresa, PERIODO, TICKET);
        assertThat(e.fase()).isEqualTo(Fase.TERMINADO);
        assertThat(e.archivos()).extracting(ArchivoDelTicket::tipo).containsExactly("02");
    }

    @Test void unTicketProcesadoConErroresNoTieneArchivos(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).willReturn(okJson(registro("03", "Procesado con Errores", "[{\"nomArchivoReporte\":\"a.zip\"}]"))));
        EstadoDelTicket e = gateway(wm).consultar(empresa, PERIODO, TICKET);
        assertThat(e.fase()).isEqualTo(Fase.CON_ERRORES);
        assertThat(e.archivos()).isEmpty();
    }

    @Test void unEstadoQueElManualNoDefineEsTransitorioYNoSeDaPorTerminado(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).willReturn(okJson(registro("99", "Nuevo", "[]"))));
        assertThatThrownBy(() -> gateway(wm).consultar(empresa, PERIODO, TICKET)).isExactlyInstanceOf(SunatTransientException.class).hasMessageContaining("99");
    }

    @Test void unTicketQueSunatNoListaEsNoEncontrado(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).willReturn(okJson("{\"paginacion\":{\"page\":1,\"perPage\":20,\"totalRegistros\":0},\"registros\":[]}")));
        assertThatThrownBy(() -> gateway(wm).consultar(empresa, PERIODO, TICKET)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        // Otro ticket en la respuesta no es el que se preguntó.
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).willReturn(okJson(registro("06", "Terminado", "[]").replace(TICKET, "202600000999"))));
        assertThatThrownBy(() -> gateway(wm).consultar(empresa, PERIODO, TICKET)).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_ENCONTRADO");
    }

    @Test void unTicketConCaracteresRarosNoLlegaASunat(WireMockRuntimeInfo wm) {
        tokenOk();
        for (String raro : new String[]{"", "1&codLibro=1", "../x", "a b", "x".repeat(41)})
            assertThatThrownBy(() -> gateway(wm).consultar(empresa, PERIODO, raro)).as(raro).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("PARAMETRO_INVALIDO");
        verify(0, getRequestedFor(urlPathEqualTo(RUTA_TICKETS)));
    }

    // --- descargar un archivo ---------------------------------------------------------------------------------------------------------------

    @Test void descargaElArchivoConLosDatosDeLaConsultaDelTicket(WireMockRuntimeInfo wm) {
        tokenOk();
        byte[] zip = {'P', 'K', 3, 4, 0, 1, 2, (byte) 0xFF};
        stubFor(get(urlPathEqualTo(RUTA_ARCHIVO)).willReturn(aResponse().withStatus(200).withBody(zip)));

        byte[] bytes = gateway(wm).descargar(empresa, new ArchivoDelTicket(TICKET, "20100066603-CPF-202610-01.zip", "01", "13", "202610"), 1024);

        assertThat(bytes).isEqualTo(zip);
        verify(getRequestedFor(urlEqualTo(RUTA_ARCHIVO + "?nomArchivoReporte=20100066603-CPF-202610-01.zip&codTipoArchivoReporte=01&perTributario=202610&codProceso=13&numTicket="
                + TICKET + "&codLibro=140000")).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test void siElTipoDeArchivoEsNuloSeManda_null_comoPideElManual(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_ARCHIVO)).willReturn(aResponse().withStatus(200).withBody("x")));
        gateway(wm).descargar(empresa, new ArchivoDelTicket(TICKET, "a b.zip", null, "13", "202610"), 1024);
        verify(getRequestedFor(urlEqualTo(RUTA_ARCHIVO + "?nomArchivoReporte=a+b.zip&codTipoArchivoReporte=null&perTributario=202610&codProceso=13&numTicket=" + TICKET + "&codLibro=140000")));
    }

    @Test void unArchivoQueNoExisteEsUnRechazoConSuCodigo(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_ARCHIVO)).willReturn(aResponse().withStatus(422).withBody("{\"cod\":\"422\",\"msg\":\"Unprocessable Entity\",\"errors\":[{\"cod\":\"2244\",\"msg\":\"El archivo solicitado no existe\"}]}")));
        assertThatThrownBy(() -> gateway(wm).descargar(empresa, new ArchivoDelTicket(TICKET, "a.zip", "01", "13", "202610"), 1024)).isInstanceOf(SunatRechazoException.class)
                .hasFieldOrPropertyWithValue("codigo", "2244");
    }

    @Test void unArchivoMasGrandeQueElTopeSeCortaSinLeerloEntero(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_ARCHIVO)).willReturn(aResponse().withStatus(200).withBody(new byte[3 * 1024 * 1024])));
        assertThatThrownBy(() -> gateway(wm).descargar(empresa, new ArchivoDelTicket(TICKET, "a.zip", "01", "13", "202610"), 2L * 1024 * 1024)).isInstanceOf(DomainException.class)
                .hasMessageContaining("2 MB").extracting("codigo").isEqualTo("RVIE_PROPUESTA_DEMASIADO_GRANDE");
    }

    @Test void unArchivoJustoDelTamanoDelTopeSePermite(WireMockRuntimeInfo wm) {
        tokenOk();
        stubFor(get(urlPathEqualTo(RUTA_ARCHIVO)).willReturn(aResponse().withStatus(200).withBody(new byte[1024])));
        assertThat(gateway(wm).descargar(empresa, new ArchivoDelTicket(TICKET, "a.zip", "01", "13", "202610"), 1024)).hasSize(1024);
    }

    // --- apagado y credenciales de la empresa -----------------------------------------------------------------------------------------------

    @Test void apagadoNoLlamaANadie(WireMockRuntimeInfo wm) {
        RestSireGateway g = gateway(wm, false);
        assertThat(g.disponible()).isFalse();
        ArchivoDelTicket a = new ArchivoDelTicket(TICKET, "a.zip", "01", "13", "202610");
        assertThatThrownBy(() -> g.solicitarPropuesta(empresa, PERIODO)).extracting("codigo").isEqualTo("SIRE_NO_DISPONIBLE");
        assertThatThrownBy(() -> g.consultar(empresa, PERIODO, TICKET)).extracting("codigo").isEqualTo("SIRE_NO_DISPONIBLE");
        assertThatThrownBy(() -> g.descargar(empresa, a, 1024)).extracting("codigo").isEqualTo("SIRE_NO_DISPONIBLE");
        verify(0, postRequestedFor(urlEqualTo(RUTA_TOKEN)));
        assertThat(gateway(wm).disponible()).isTrue();
    }

    @Test void sinCredencialesApiSunatOSolNoSeLlamaANadie(WireMockRuntimeInfo wm) {
        Tenant sinApi = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA", Entorno.PRODUCCION, new CredencialesSol("USUARIO1", CLAVE_SOL), null);
        Tenant sinSol = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA", Entorno.PRODUCCION, null, null).conCredencialesApiSunat(new CredencialesApiSunat(ID, SECRETO));
        assertThatThrownBy(() -> gateway(wm).solicitarPropuesta(sinApi, PERIODO)).extracting("codigo").isEqualTo("CREDENCIALES_API_SUNAT_NO_CARGADAS");
        assertThatThrownBy(() -> gateway(wm).solicitarPropuesta(sinSol, PERIODO)).extracting("codigo").isEqualTo("CREDENCIALES_SOL_NO_CARGADAS");
        verify(0, postRequestedFor(urlEqualTo(RUTA_TOKEN)));
    }
}
