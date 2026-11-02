package pe.factura.adapters.sunatrest;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.Test;
import pe.factura.adapters.sunatrest.CorridaDeSire.Datos;
import pe.factura.adapters.sunatrest.CorridaDeValidez.Paso;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La corrida controlada del SIRE (#322) contra un SUNAT simulado: así la corrida real no estrena código el día que se hace. Lo que cada paso concluye se prueba con lo
 * que SUNAT contestaría de acuerdo con el manual v31; qué contesta de verdad lo dirá {@code ./gradlew :adapters:out-sunat-rest:sireReal}.
 */
@WireMockTest
class CorridaDeSireTest {
    static final String ID = "d20b421e-4ace-46d8-ac8c-4fbb44cfde17";
    static final String SECRETO = "HhWwZVo8ZaWm4BZ7n/xEzw==";
    static final String CLAVE_SOL = "clave-sol-secreta";
    static final String RUC = "20100066603";
    static final String TICKET = "202600000123";
    static final String RUTA_TOKEN = "/v1/clientessol/" + ID + "/oauth2/token/";
    static final String RUTA_PEDIR = "/v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/202609/exportapropuesta";
    static final String RUTA_TICKETS = "/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets";
    static final String RUTA_ARCHIVO = "/v1/contribuyente/migeigv/libros/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte";

    Datos datos(WireMockRuntimeInfo wm, boolean conCredenciales, String espera) {
        return new Datos(wm.getHttpBaseUrl(), wm.getHttpBaseUrl(), RUC, conCredenciales ? "USUARIO1" : "", conCredenciales ? CLAVE_SOL : "", conCredenciales ? ID : "",
                conCredenciales ? SECRETO : "", "202609", Duration.ofMillis(Long.parseLong(espera)), Duration.ofMillis(20));
    }

    static String fila(String serie, String numero) {
        String[] f = new String[40];
        Arrays.fill(f, "");
        f[0] = RUC; f[2] = "202609"; f[4] = "01/09/2026"; f[6] = "01"; f[7] = serie; f[8] = numero;
        f[14] = "100.00"; f[16] = "18.00"; f[18] = "0.00"; f[19] = "0.00"; f[25] = "118.00"; f[26] = "PEN"; f[34] = "1";
        return String.join("|", f) + "|";
    }

    static byte[] zip(String... lineas) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry(RUC + "-CPF-202609-01.txt"));
            z.write(String.join("\r\n", lineas).getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return out.toByteArray();
    }

    static String registro(String estado, String descripcion, String archivos) {
        return "{\"registros\":[{\"perTributario\":\"202609\",\"numTicket\":\"" + TICKET + "\",\"codProceso\":\"13\",\"codEstadoProceso\":\"" + estado + "\","
                + "\"desEstadoProceso\":\"" + descripcion + "\",\"archivoReporte\":" + archivos + "}]}";
    }

    static void sunatQueCoopera(byte[] archivo) {
        stubFor(post(RUTA_TOKEN).willReturn(okJson("{\"access_token\":\"tk\",\"token_type\":\"JWT\",\"expires_in\":3600}")));
        stubFor(post("/v1/clientessol/" + CorridaDeSire.ID_INVENTADO + "/oauth2/token/").willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unauthorized_client\"}")));
        stubFor(get(urlPathEqualTo(RUTA_PEDIR)).willReturn(okJson("{\"numTicket\":\"" + TICKET + "\"}")));
        // La consulta sin los parámetros extra (paso informativo) gasta la primera respuesta; el seguimiento ve otro «en proceso» y luego el final.
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).inScenario("ticket").whenScenarioStateIs(Scenario.STARTED).willReturn(okJson(registro("05", "En proceso", "[]")))
                .willSetStateTo("segunda"));
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).inScenario("ticket").whenScenarioStateIs("segunda").willReturn(okJson(registro("05", "En proceso", "[]")))
                .willSetStateTo("terminado"));
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).inScenario("ticket").whenScenarioStateIs("terminado")
                .willReturn(okJson(registro("06", "Terminado", "[{\"codTipoAchivoReporte\":\"01\",\"nomArchivoReporte\":\"" + RUC + "-CPF-202609-01.zip\"}]"))));
        stubFor(get(urlPathEqualTo(RUTA_ARCHIVO)).willReturn(aResponse().withStatus(200).withBody(archivo)));
    }

    static Paso paso(List<Paso> pasos, String empieza) {
        return pasos.stream().filter(p -> p.nombre().startsWith(empieza)).findFirst().orElseThrow(() -> new AssertionError("no hay un paso «" + empieza + "» en " + pasos.stream().map(Paso::nombre).toList()));
    }

    @Test void sinVariablesSoloCorreElPasoDeLasCredencialesInventadasYLoDemasQuedaOmitido(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/clientessol/" + CorridaDeSire.ID_INVENTADO + "/oauth2/token/").willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unauthorized_client\"}")));

        List<Paso> pasos = CorridaDeSire.correr(datos(wm, false, "500"));

        assertThat(paso(pasos, "credenciales inventadas").cumple()).isTrue();
        assertThat(paso(pasos, "credenciales inventadas").obtenido()).contains("unauthorized_client");
        assertThat(pasos).filteredOn(Paso::omitido).hasSize(1);
        assertThat(pasos).filteredOn(p -> !p.cumple() && !p.informativo()).isEmpty();
    }

    @Test void siSunatAceptaLasCredencialesInventadasElPasoNoCumple(WireMockRuntimeInfo wm) {
        stubFor(post("/v1/clientessol/" + CorridaDeSire.ID_INVENTADO + "/oauth2/token/").willReturn(okJson("{\"access_token\":\"tk\",\"expires_in\":3600}")));
        stubFor(get(urlPathEqualTo(RUTA_PEDIR)).willReturn(okJson("{}")));
        stubFor(get(urlPathEqualTo("/v1/contribuyente/migeigv/libros/rvie/propuesta/web/propuesta/202601/exportapropuesta")).willReturn(okJson("{\"numTicket\":\"1\"}")));
        assertThat(paso(CorridaDeSire.correr(datos(wm, false, "500")), "credenciales inventadas").cumple()).isFalse();
    }

    @Test void conTodoLoQueHaceFaltaPideSigueBajaYLeeLaPropuesta(WireMockRuntimeInfo wm) throws Exception {
        sunatQueCoopera(zip(fila("F001", "1"), fila("F001", "2")));

        List<Paso> pasos = CorridaDeSire.correr(datos(wm, true, "5000"));

        assertThat(pasos).filteredOn(p -> !p.cumple() && !p.informativo()).as("pasos que no cumplen: %s", pasos).isEmpty();
        assertThat(pasos).extracting(Paso::nombre).anyMatch(n -> n.startsWith("pedir la propuesta")).anyMatch(n -> n.startsWith("seguir el ticket")).anyMatch(n -> n.startsWith("bajar"))
                .anyMatch(n -> n.startsWith("leer la propuesta"));
        assertThat(paso(pasos, "seguir el ticket").obtenido()).contains("05 En proceso -> 06 Terminado").contains("archivos: 1");
        assertThat(paso(pasos, "leer la propuesta").obtenido()).contains("2 fila(s) leída(s)").contains("fila de títulos: no").contains("41");
    }

    @Test void unaFilaQueElLectorNoEntiendeHaceFallarLaLecturaYNoImprimeLaFila(WireMockRuntimeInfo wm) throws Exception {
        sunatQueCoopera(zip(fila("F001", "1"), "CLIENTE-SECRETO" + "|".repeat(40)));

        List<Paso> pasos = CorridaDeSire.correr(datos(wm, true, "5000"));

        Paso leer = paso(pasos, "leer la propuesta");
        assertThat(leer.cumple()).isFalse();
        assertThat(leer.obtenido()).contains("línea 2", "campo 1").doesNotContain("CLIENTE-SECRETO");
    }

    @Test void unTicketQueNuncaTerminaAgotaLaEsperaYLoDice(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).willReturn(okJson("{\"access_token\":\"tk\",\"expires_in\":3600}")));
        stubFor(post("/v1/clientessol/" + CorridaDeSire.ID_INVENTADO + "/oauth2/token/").willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unauthorized_client\"}")));
        stubFor(get(urlPathEqualTo(RUTA_PEDIR)).willReturn(okJson("{\"numTicket\":\"" + TICKET + "\"}")));
        stubFor(get(urlPathEqualTo(RUTA_TICKETS)).willReturn(okJson(registro("05", "En proceso", "[]"))));

        List<Paso> pasos = CorridaDeSire.correr(datos(wm, true, "150"));

        Paso seguir = paso(pasos, "seguir el ticket");
        assertThat(seguir.cumple()).isFalse();
        assertThat(seguir.obtenido()).contains("no terminó").contains("SIRE_ESPERA_SEGUNDOS");
        assertThat(pasos).extracting(Paso::nombre).noneMatch(n -> n.startsWith("bajar"));
    }

    @Test void unPeriodoSinComprobantesNoRompeElContratoPeroNoDejaSeguir(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).willReturn(okJson("{\"access_token\":\"tk\",\"expires_in\":3600}")));
        stubFor(post("/v1/clientessol/" + CorridaDeSire.ID_INVENTADO + "/oauth2/token/").willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unauthorized_client\"}")));
        stubFor(get(urlPathEqualTo(RUTA_PEDIR)).willReturn(aResponse().withStatus(422).withBody("{\"cod\":\"422\",\"errors\":[{\"cod\":\"1070\",\"msg\":\"No se ha encontrado información\"}]}")));

        List<Paso> pasos = CorridaDeSire.correr(datos(wm, true, "500"));

        Paso pedir = paso(pasos, "pedir la propuesta");
        assertThat(pedir.informativo()).isTrue();
        assertThat(pedir.obtenido()).contains("1070").contains("repita con un mes que sí");
        assertThat(pasos).filteredOn(p -> !p.cumple() && !p.informativo()).isEmpty();
    }

    @Test void lasCredencialesQueSunatNoAceptaHacenFallarElPedido(WireMockRuntimeInfo wm) {
        stubFor(post(RUTA_TOKEN).willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unauthorized_client\"}")));
        stubFor(post("/v1/clientessol/" + CorridaDeSire.ID_INVENTADO + "/oauth2/token/").willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unauthorized_client\"}")));
        Paso pedir = paso(CorridaDeSire.correr(datos(wm, true, "500")), "pedir la propuesta");
        assertThat(pedir.cumple()).isFalse();
        assertThat(pedir.obtenido()).contains("SunatCredencialesException");
    }

    @Test void laEvidenciaNoLlevaSecretosNiClavesNiFilas(WireMockRuntimeInfo wm) throws Exception {
        sunatQueCoopera(zip(fila("F001", "1")));
        Datos d = datos(wm, true, "5000");

        String resumen = CorridaDeSire.resumen(d, CorridaDeSire.correr(d), Instant.parse("2026-10-31T12:00:00Z"));

        assertThat(resumen).doesNotContain(SECRETO).doesNotContain(CLAVE_SOL).doesNotContain("USUARIO1").doesNotContain(ID).doesNotContain("tk").doesNotContain("F001")
                .contains("d20b421e…").contains("Usuario SOL: indicado").contains("**La corrida cumple.**");
    }

    @Test void lasVariablesDeEntornoSeLeenYUnaQueNoSeEntiendeDiceCual() {
        Datos d = CorridaDeSire.deEntorno(Map.of("SIRE_RUC", " 20100066603 ", "SIRE_PERIODO", "202609", "SIRE_ESPERA_SEGUNDOS", "30"));
        assertThat(d.ruc()).isEqualTo("20100066603");
        assertThat(d.urlApi()).isEqualTo("https://api-sire.sunat.gob.pe");
        assertThat(d.urlSeguridad()).isEqualTo("https://api-seguridad.sunat.gob.pe");
        assertThat(d.espera()).isEqualTo(Duration.ofSeconds(30));
        assertThat(d.conCredenciales()).isFalse();
        assertThatThrownBy(() -> CorridaDeSire.deEntorno(Map.of("SIRE_ESPERA_SEGUNDOS", "mucho"))).hasMessageContaining("SIRE_ESPERA_SEGUNDOS").hasMessageContaining("mucho");
    }

    @Test void unPeriodoMalEscritoDiceQueVariableEs(WireMockRuntimeInfo wm) {
        Datos d = new Datos(wm.getHttpBaseUrl(), wm.getHttpBaseUrl(), RUC, "U", "C", ID, SECRETO, "2026-09", Duration.ofMillis(100), Duration.ofMillis(10));
        stubFor(post("/v1/clientessol/" + CorridaDeSire.ID_INVENTADO + "/oauth2/token/").willReturn(aResponse().withStatus(400).withBody("{\"error\":\"unauthorized_client\"}")));
        assertThatThrownBy(() -> CorridaDeSire.correr(d)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SIRE_PERIODO");
    }
}
