package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.application.service.ApiKeyGenerator;
import pe.factura.domain.tenant.ApiKey;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {
    UUID tenant = UUID.randomUUID();
    String key = "fk_valida";
    ApiKeyRepository repo = new ApiKeyRepository() {
        public void guardar(ApiKey k) {}
        public Optional<ApiKey> buscarPorHash(String h) {
            return h.equals(ApiKeyGenerator.hash(key, "pep")) ? Optional.of(new ApiKey(UUID.randomUUID(), tenant, h, "fk_valida", true)) : Optional.empty();
        }
    };
    ApiKeyFilter filter = new ApiKeyFilter(repo, "pep");

    @Test void keyValidaDejaPasarYExponeTenant() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/facturas");
        req.addHeader("X-Api-Key", key);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(TenantActual.id(req)).isEqualTo(tenant);
    }

    @Test void sinKeyResponde401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("\"estado\":\"error\"").contains("NO_AUTORIZADO");
    }

    @Test void keyInvalidaResponde401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        req.addHeader("X-Api-Key", "fk_otra");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test void rutasAdminYHealthNoRequierenApiKey() throws Exception {
        for (String uri : new String[]{"/v1/admin/tenants", "/health"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
            assertThat(chain.getRequest()).as(uri).isNotNull();
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }
}
