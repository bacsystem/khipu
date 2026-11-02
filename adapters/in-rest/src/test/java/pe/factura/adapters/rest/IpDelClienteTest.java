package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** #261: solo se cuenta por IP cuando la del cliente se conoce de verdad. */
class IpDelClienteTest {
    static MockHttpServletRequest desde(String ip) {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        return req;
    }

    @Test void sinProxiesDeConfianzaNoSeConoceNingunaIp() {
        var ip = new IpDelCliente("");
        assertThat(ip.de(desde("203.0.113.9"))).as("ni siquiera la de la conexión: puede ser la del portal").isNull();
    }

    @Test void conProxiesDeConfianzaEsLaQueResolvioTomcat() {
        assertThat(new IpDelCliente("10\\.0\\.0\\.5").de(desde("203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    /** Llegó del proxy sin reenviar la IP: es la del proxy, compartida por todos los clientes. */
    @Test void siEsLaDeUnProxyDeConfianzaNoCuenta() {
        assertThat(new IpDelCliente("10\\.0\\.0\\.5").de(desde("10.0.0.5"))).isNull();
    }
}
