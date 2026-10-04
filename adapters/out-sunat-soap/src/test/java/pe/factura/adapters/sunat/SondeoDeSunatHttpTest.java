package pe.factura.adapters.sunat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SondeoDeSunat.Resultado;
import pe.factura.application.port.out.SondeoDeSunat.Servicio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El sondeo de los servicios de SUNAT (#195): contesta o no contesta, sin credenciales ni comprobantes, a la vez y con el resultado guardado un rato para que el monitor no
 * llame a SUNAT en cada refresco.
 */
@WireMockTest
class SondeoDeSunatHttpTest {
    static final Duration PLAZO = Duration.ofMillis(700);
    static final Duration VIGENCIA = Duration.ofSeconds(30);

    /** Un reloj que se mueve a mano. */
    static class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-10-15T15:00:00Z");
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return ahora; }
    }

    Reloj reloj = new Reloj();

    Map<Servicio, String> urls(WireMockRuntimeInfo wm, Object... pares) {
        Map<Servicio, String> m = new EnumMap<>(Servicio.class);
        for (int i = 0; i < pares.length; i += 2) {
            String ruta = (String) pares[i + 1];
            m.put((Servicio) pares[i], ruta == null || ruta.isEmpty() ? ruta : wm.getHttpBaseUrl() + ruta);
        }
        return m;
    }

    SondeoDeSunatHttp sondeo(Map<Servicio, String> urls) { return new SondeoDeSunatHttp(urls, PLAZO, VIGENCIA, reloj); }

    Resultado de(List<Resultado> r, Servicio s) { return r.stream().filter(x -> x.servicio() == s).findFirst().orElseThrow(); }

    @Test void unServicioQueContestaConExitoEstaDisponibleYDiceCuantoTardo(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(ok("<wsdl/>").withFixedDelay(40)));

        Resultado r = sondeo(urls(wm, Servicio.ENVIO_PRODUCCION, "/envio")).sondear().get(0);

        assertThat(r.servicio()).isEqualTo(Servicio.ENVIO_PRODUCCION);
        assertThat(r.disponible()).isTrue();
        assertThat(r.milisegundos()).isBetween(30L, 600L);
        assertThat(r.detalle()).isNull();
    }

    @Test void sondeaElWsdlDelServicioSinCredencialesNiCuerpo(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(ok()));

        sondeo(urls(wm, Servicio.ENVIO_PRODUCCION, "/envio")).sondear();

        verify(1, getRequestedFor(urlEqualTo("/envio?wsdl")).withoutHeader("Authorization"));
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test void unaUrlQueYaTraeParametrosAgregaElWsdlConAmpersand(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?v=2&wsdl")).willReturn(ok()));

        assertThat(sondeo(urls(wm, Servicio.ENVIO_BETA, "/envio?v=2")).sondear().get(0).disponible()).isTrue();
    }

    @Test void unCodigoDeErrorEsNoDisponibleConSuCodigo(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(serviceUnavailable()));
        stubFor(get(urlEqualTo("/consulta?wsdl")).willReturn(notFound()));
        stubFor(get(urlEqualTo("/validez?wsdl")).willReturn(serverError()));

        List<Resultado> r = sondeo(urls(wm, Servicio.ENVIO_PRODUCCION, "/envio", Servicio.CONSULTA_DE_CDR, "/consulta", Servicio.CONSULTA_DE_VALIDEZ, "/validez")).sondear();

        assertThat(de(r, Servicio.ENVIO_PRODUCCION)).matches(x -> !x.disponible() && "HTTP 503".equals(x.detalle()));
        assertThat(de(r, Servicio.CONSULTA_DE_CDR)).matches(x -> !x.disponible() && "HTTP 404".equals(x.detalle()));
        assertThat(de(r, Servicio.CONSULTA_DE_VALIDEZ)).matches(x -> !x.disponible() && "HTTP 500".equals(x.detalle()));
        assertThat(de(r, Servicio.ENVIO_PRODUCCION).milisegundos()).as("un error también dice cuánto tardó").isNotNull();
    }

    @Test void unaRedireccionNoSeSigueYNoCuentaComoDisponible(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(temporaryRedirect("/otro")));

        Resultado r = sondeo(urls(wm, Servicio.ENVIO_PRODUCCION, "/envio")).sondear().get(0);

        assertThat(r.disponible()).isFalse();
        assertThat(r.detalle()).isEqualTo("HTTP 302");
        verify(0, getRequestedFor(urlEqualTo("/otro")));
    }

    @Test void unServicioQueNoContestaDentroDelPlazoDiceQueNoContesto(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(ok().withFixedDelay(3000)));

        Resultado r = sondeo(urls(wm, Servicio.ENVIO_PRODUCCION, "/envio")).sondear().get(0);

        assertThat(r.disponible()).isFalse();
        assertThat(r.milisegundos()).isNull();
        assertThat(r.detalle()).isEqualTo("No contestó en 0 s");
    }

    @Test void unServicioInalcanzableDiceSinConexionYNuncaLanza() {
        Map<Servicio, String> m = new EnumMap<>(Servicio.class);
        m.put(Servicio.ENVIO_PRODUCCION, "http://127.0.0.1:1/envio");

        Resultado r = sondeo(m).sondear().get(0);

        assertThat(r.disponible()).isFalse();
        assertThat(r.milisegundos()).isNull();
        assertThat(r.detalle()).isEqualTo("Sin conexión");
    }

    @Test void unaUrlMalEscritaTambienEsUnResultadoYNoUnaExcepcion() {
        Map<Servicio, String> m = new EnumMap<>(Servicio.class);
        m.put(Servicio.CONSULTA_DE_CDR, "no es una url con espacios");

        Resultado r = sondeo(m).sondear().get(0);

        assertThat(r.disponible()).isFalse();
        assertThat(r.detalle()).isEqualTo("Sin conexión");
    }

    @Test void losServiciosSinUrlNoSeSondeanYElOrdenEsElDelEnum(WireMockRuntimeInfo wm) {
        stubFor(get(anyUrl()).willReturn(ok()));

        List<Resultado> r = sondeo(urls(wm, Servicio.CONSULTA_DE_VALIDEZ, "/v", Servicio.ENVIO_PRODUCCION, "/e", Servicio.ENVIO_BETA, "", Servicio.CONSULTA_DE_CDR, null)).sondear();

        assertThat(r).extracting(Resultado::servicio).containsExactly(Servicio.ENVIO_PRODUCCION, Servicio.CONSULTA_DE_VALIDEZ);
        verify(2, getRequestedFor(anyUrl()));
    }

    /** Una URL pegada en la configuración con espacios o un salto de línea de más igual se sondea: con ellos, {@code URI.create} la rechazaría. */
    @Test void losEspaciosAlrededorDeLaUrlSeIgnoran(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(ok()));
        Map<Servicio, String> m = new EnumMap<>(Servicio.class);
        m.put(Servicio.ENVIO_PRODUCCION, "  " + wm.getHttpBaseUrl() + "/envio \n");

        Resultado r = sondeo(m).sondear().get(0);

        assertThat(r.disponible()).isTrue();
    }

    @Test void sinNingunaUrlNoHayNadaQueSondear() {
        assertThat(sondeo(new EnumMap<>(Servicio.class)).sondear()).isEmpty();
    }

    /** Los servicios se sondean a la vez: el peor caso es un plazo, no la suma de todos. */
    @Test void sondeaTodosALaVezYNoUnoDetrasDeOtro(WireMockRuntimeInfo wm) {
        stubFor(get(anyUrl()).willReturn(ok().withFixedDelay(400)));

        long inicio = System.nanoTime();
        List<Resultado> r = sondeo(urls(wm, Servicio.ENVIO_PRODUCCION, "/a", Servicio.ENVIO_BETA, "/b", Servicio.CONSULTA_DE_CDR, "/c", Servicio.CONSULTA_DE_VALIDEZ, "/d")).sondear();
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(r).hasSize(4).allMatch(Resultado::disponible);
        assertThat(ms).as("4 servicios de 400 ms cada uno, sondeados a la vez").isLessThan(1400);
    }

    // --- el resultado guardado ---------------------------------------------------------------------------------------------------------------

    @Test void dentroDeLaVigenciaNoVuelveALlamarASunat(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(ok()));
        SondeoDeSunatHttp s = sondeo(urls(wm, Servicio.ENVIO_PRODUCCION, "/envio"));

        List<Resultado> primero = s.sondear();
        reloj.ahora = reloj.ahora.plusSeconds(29);
        List<Resultado> segundo = s.sondear();

        assertThat(segundo).isSameAs(primero);
        verify(1, getRequestedFor(urlEqualTo("/envio?wsdl")));
    }

    @Test void alCumplirseLaVigenciaVuelveASondear(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(ok()));
        SondeoDeSunatHttp s = sondeo(urls(wm, Servicio.ENVIO_PRODUCCION, "/envio"));

        s.sondear();
        reloj.ahora = reloj.ahora.plusSeconds(30);
        s.sondear();

        verify(2, getRequestedFor(urlEqualTo("/envio?wsdl")));
    }

    @Test void siElServicioSeCaeSeVeEnElSiguienteSondeoYSiVuelveTambien(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(ok()));
        SondeoDeSunatHttp s = sondeo(urls(wm, Servicio.ENVIO_PRODUCCION, "/envio"));
        assertThat(s.sondear().get(0).disponible()).isTrue();

        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(serviceUnavailable()));
        reloj.ahora = reloj.ahora.plusSeconds(31);
        assertThat(s.sondear().get(0).disponible()).isFalse();

        stubFor(get(urlEqualTo("/envio?wsdl")).willReturn(ok()));
        reloj.ahora = reloj.ahora.plusSeconds(31);
        assertThat(s.sondear().get(0).disponible()).isTrue();
    }
}
