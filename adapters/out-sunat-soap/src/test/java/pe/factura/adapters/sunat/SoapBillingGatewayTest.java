package pe.factura.adapters.sunat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.tenant.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@WireMockTest
class SoapBillingGatewayTest {
    Tenant tenant = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA,
            new CredencialesSol("MODDATOS", "moddatos"), new CertificadoDigital(new byte[0], "", LocalDate.of(2030, 1, 1)));

    private SoapBillingGateway gateway(WireMockRuntimeInfo wm) {
        return new SoapBillingGateway(new SunatUrls(wm.getHttpBaseUrl() + "/billService", wm.getHttpBaseUrl() + "/prod"), Duration.ofSeconds(2));
    }

    private static String respuestaOk(byte[] cdrZip) {
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body>"
                + "<ns2:sendBillResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><applicationResponse>"
                + Base64.getEncoder().encodeToString(cdrZip) + "</applicationResponse></ns2:sendBillResponse></soap-env:Body></soap-env:Envelope>";
    }
    private static String fault(String code, String msg) {
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><soap-env:Fault>"
                + "<faultcode>soap-env:Client." + code + "</faultcode><faultstring>" + msg + "</faultstring></soap-env:Fault></soap-env:Body></soap-env:Envelope>";
    }

    @Test void enviaZipConWsSecurityYDevuelveCdr(WireMockRuntimeInfo wm) {
        byte[] cdr = ZipUtil.comprimir("R-20100066603-01-F001-1.xml", "<cdr/>".getBytes());
        stubFor(post("/billService").willReturn(okXml(respuestaOk(cdr))));

        byte[] r = gateway(wm).sendBill(tenant, "20100066603-01-F001-1", "<Invoice/>".getBytes());

        assertThat(r).isEqualTo(cdr);
        verify(postRequestedFor(urlEqualTo("/billService"))
                .withHeader("Content-Type", containing("text/xml"))
                .withRequestBody(containing("<wsse:Username>20100066603MODDATOS</wsse:Username>"))
                .withRequestBody(containing("<wsse:Password>moddatos</wsse:Password>"))
                .withRequestBody(containing("<fileName>20100066603-01-F001-1.zip</fileName>"))
                .withRequestBody(matching("(?s).*<contentFile>[A-Za-z0-9+/=]+</contentFile>.*")));
    }

    @Test void faultMenorA2000EsTransitorio(WireMockRuntimeInfo wm) {
        stubFor(post("/billService").willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml").withBody(fault("0109", "El sistema no puede responder en este momento"))));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0]))
                .isInstanceOf(SunatTransientException.class).hasMessageContaining("no puede responder")
                .extracting("codigo").isEqualTo("0109");
    }

    @Test void faultMayorOIgualA2000EsRechazo(WireMockRuntimeInfo wm) {
        stubFor(post("/billService").willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml").withBody(fault("2324", "registrado previamente"))));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0]))
                .isInstanceOf(SunatRechazoException.class).extracting("codigo").isEqualTo("2324");
    }

    @Test void faultCodeSinPrefijoTambienSeParsea(WireMockRuntimeInfo wm) {
        String f = fault("1033", "ya fue registrado").replace("soap-env:Client.1033", "1033");
        stubFor(post("/billService").willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml").withBody(f)));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0])).extracting("codigo").isEqualTo("1033");
    }

    @Test void timeoutEsTransitorio(WireMockRuntimeInfo wm) {
        stubFor(post("/billService").willReturn(okXml("<x/>").withFixedDelay(3000)));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0])).isInstanceOf(SunatTransientException.class);
    }

    @Test void http503EsTransitorio(WireMockRuntimeInfo wm) {
        stubFor(post("/billService").willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> gateway(wm).sendBill(tenant, "n", new byte[0])).isInstanceOf(SunatTransientException.class);
    }

    @Test void urlPorEntorno() {
        SunatUrls u = new SunatUrls("http://beta", "http://prod");
        assertThat(u.para(Entorno.BETA)).isEqualTo("http://beta");
        assertThat(u.para(Entorno.PRODUCCION)).isEqualTo("http://prod");
    }
}
