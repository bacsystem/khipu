package pe.factura.adapters.sunat;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SoapEnvelopeTest {
    @Test void textoDeNoConfundeNombresQueEmpiezanIgual() {
        String xml = "<a><applicationResponseData>x</applicationResponseData><applicationResponse>OK</applicationResponse></a>";
        assertThat(SoapEnvelope.textoDe(xml, "applicationResponse")).isEqualTo("OK");
    }

    @Test void textoDeIgnoraPrefijoYAtributos() {
        String xml = "<ns:faultcode xmlns:ns=\"u\">soap-env:Client.1033</ns:faultcode>";
        assertThat(SoapEnvelope.textoDe(xml, "faultcode")).isEqualTo("soap-env:Client.1033");
    }

    @Test void textoDeDevuelveNullSiNoExiste() {
        assertThat(SoapEnvelope.textoDe("<x/>", "faultcode")).isNull();
    }

    @Test void codigoDeFaultConPrefijoClient() {
        assertThat(SoapEnvelope.codigoDeFault("soap-env:Client.1033")).isEqualTo("1033");
    }

    @Test void codigoDeFaultSinPrefijo() {
        assertThat(SoapEnvelope.codigoDeFault("1033")).isEqualTo("1033");
    }

    @Test void codigoDeFaultServerSinNumero() {
        assertThat(SoapEnvelope.codigoDeFault("soap-env:Server")).isEqualTo("0000");
    }

    @Test void codigoDeFaultNulo() {
        assertThat(SoapEnvelope.codigoDeFault(null)).isEqualTo("0000");
    }
}
