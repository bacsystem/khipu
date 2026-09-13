package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformKeyFilterTest {
    @Test void claveCorrectaPasa() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "secreta");
        var chain = new MockFilterChain();
        new PlatformKeyFilter("secreta").doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
    @Test void claveIncorrecta401() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "otra");
        var res = new MockHttpServletResponse();
        new PlatformKeyFilter("secreta").doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }
    @Test void sinClaveConfigurada404() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "x");
        var res = new MockHttpServletResponse();
        new PlatformKeyFilter("").doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(404);
    }
    @Test void otrasRutasNoSeFiltran() throws Exception {
        var req = new MockHttpServletRequest("GET", "/v1/facturas");
        var chain = new MockFilterChain();
        new PlatformKeyFilter("secreta").doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
}
