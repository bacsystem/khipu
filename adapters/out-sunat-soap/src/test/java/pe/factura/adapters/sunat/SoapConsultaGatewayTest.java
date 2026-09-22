package pe.factura.adapters.sunat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SunatConsultaGateway.Consulta;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** billConsultService (getStatus/getStatusCdr) y billValidService (validaCDPcriterios) según los WSDL de SUNAT (#36). */
@WireMockTest
class SoapConsultaGatewayTest {
    Tenant prod = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.PRODUCCION,
            new CredencialesSol("USUARIO1", "clave"), new CertificadoDigital(new byte[0], "", LocalDate.of(2030, 1, 1)));
    Tenant beta = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA, new CredencialesSol("MODDATOS", "moddatos"), null);

    private SoapConsultaGateway gateway(WireMockRuntimeInfo wm, String betaConsulta) {
        return new SoapConsultaGateway(wm.getHttpBaseUrl() + "/consulta", betaConsulta, wm.getHttpBaseUrl() + "/validez", null, Duration.ofSeconds(2));
    }

    private static String status(String wrapper, String inner, String code, String msg, byte[] content) {
        return "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body>"
                + "<ns0:" + wrapper + " xmlns:ns0=\"http://service.sunat.gob.pe\"><" + inner + ">"
                + (content == null ? "" : "<content>" + Base64.getEncoder().encodeToString(content) + "</content>")
                + "<statusCode>" + code + "</statusCode><statusMessage>" + msg + "</statusMessage></" + inner + "></ns0:" + wrapper + "></soap-env:Body></soap-env:Envelope>";
    }

    @Test void getStatusCdrDevuelveElCdrYPideConLasCredencialesDelTenant(WireMockRuntimeInfo wm) {
        byte[] cdr = ZipUtil.comprimir("R-20100066603-01-F001-12.xml", "<cdr/>".getBytes());
        stubFor(post("/consulta").willReturn(okXml(status("getStatusCdrResponse", "statusCdr", "0004", "El comprobante existe y está aceptado", cdr))));

        Consulta r = gateway(wm, null).getStatusCdr(prod, "20100066603", "01", "F001", 12);

        assertThat(r.statusCode()).isEqualTo("0004");
        assertThat(r.cdrZip()).isEqualTo(cdr);
        assertThat(r.conCdr()).isTrue();
        verify(postRequestedFor(urlEqualTo("/consulta"))
                .withHeader("SOAPAction", equalTo("urn:getStatusCdr"))
                .withRequestBody(containing("<wsse:Username>20100066603USUARIO1</wsse:Username>"))
                .withRequestBody(containing("<ser:getStatusCdr><rucComprobante>20100066603</rucComprobante><tipoComprobante>01</tipoComprobante><serieComprobante>F001</serieComprobante><numeroComprobante>12</numeroComprobante></ser:getStatusCdr>")));
    }

    @Test void getStatusInformaElEstadoSinCdr(WireMockRuntimeInfo wm) {
        stubFor(post("/consulta").willReturn(okXml(status("getStatusResponse", "status", "0003", "El comprobante existe pero está de baja.", null))));
        Consulta r = gateway(wm, null).getStatus(prod, "20100066603", "01", "F001", 12);
        assertThat(r.deBaja()).isTrue();
        assertThat(r.conCdr()).isFalse();
        assertThat(r.statusMessage()).contains("de baja");
        verify(postRequestedFor(urlEqualTo("/consulta")).withHeader("SOAPAction", equalTo("urn:getStatus")).withRequestBody(containing("<ser:getStatus>")));
    }

    @Test void validaCdpCriteriosEnviaSoloLosCriteriosDados(WireMockRuntimeInfo wm) {
        stubFor(post("/validez").willReturn(okXml(status("validaCDPcriteriosResponse", "cdpvalidado", "0001", "El comprobante existe y está aceptado.", null))));
        Consulta r = gateway(wm, null).validar(prod, "20601234565", "01", "F002", 7, "6", "20100066603", LocalDate.of(2026, 9, 10), new BigDecimal("118.00"));
        assertThat(r.aceptado()).isTrue();
        verify(postRequestedFor(urlEqualTo("/validez"))
                .withHeader("SOAPAction", equalTo("urn:validaCDPcriterios"))
                .withRequestBody(containing("<ser:validaCDPcriterios><rucEmisor>20601234565</rucEmisor><tipoCDP>01</tipoCDP><serieCDP>F002</serieCDP><numeroCDP>7</numeroCDP>"
                        + "<tipoDocIdReceptor>6</tipoDocIdReceptor><numeroDocIdReceptor>20100066603</numeroDocIdReceptor><fechaEmision>10/09/2026</fechaEmision><importeTotal>118.00</importeTotal></ser:validaCDPcriterios>")));
        // Sin criterios opcionales no viajan los tags.
        gateway(wm, null).validar(prod, "20601234565", "01", "F002", 7, null, null, null, null);
        verify(postRequestedFor(urlEqualTo("/validez")).withRequestBody(containing("<numeroCDP>7</numeroCDP></ser:validaCDPcriterios>")));
    }

    @Test void betaSinUrlConfiguradaSeRechazaYConUrlSeUsa(WireMockRuntimeInfo wm) {
        assertThatThrownBy(() -> gateway(wm, null).getStatusCdr(beta, "20100066603", "01", "F001", 1))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("NO_DISPONIBLE_EN_BETA");
        stubFor(post("/consulta-beta").willReturn(okXml(status("getStatusResponse", "status", "0001", "ok", null))));
        assertThat(gateway(wm, wm.getHttpBaseUrl() + "/consulta-beta").getStatus(beta, "20100066603", "01", "F001", 1).aceptado()).isTrue();
    }

    @Test void faultYRespuestaSinStatusSonErroresDeConsulta(WireMockRuntimeInfo wm) {
        stubFor(post("/consulta").willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/xml").withBody(
                "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap-env:Body><soap-env:Fault><faultcode>soap-env:Client.0109</faultcode><faultstring>caído</faultstring></soap-env:Fault></soap-env:Body></soap-env:Envelope>")));
        assertThatThrownBy(() -> gateway(wm, null).getStatus(prod, "20100066603", "01", "F001", 1)).isInstanceOf(SunatTransientException.class);
        stubFor(post("/consulta").willReturn(okXml("<x/>")));
        assertThatThrownBy(() -> gateway(wm, null).getStatus(prod, "20100066603", "01", "F001", 1)).isInstanceOf(SunatTransientException.class).hasMessageContaining("statusCode");
    }
}
