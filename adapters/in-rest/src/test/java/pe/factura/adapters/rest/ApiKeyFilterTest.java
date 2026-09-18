package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.application.service.ApiKeyGenerator;
import pe.factura.domain.tenant.ApiKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {
    UUID tenant = UUID.randomUUID();
    String key = "fk_valida";
    String keyInactiva = "fk_inactiva";
    ApiKeyRepository repo = new ApiKeyRepository() {
        public void guardar(ApiKey k) {}
        public Optional<ApiKey> buscarPorHash(String h) {
            if (h.equals(ApiKeyGenerator.hash(key, "pep"))) return Optional.of(new ApiKey(UUID.randomUUID(), tenant, h, "fk_valida", true, null, null));
            if (h.equals(ApiKeyGenerator.hash(keyInactiva, "pep"))) return Optional.of(new ApiKey(UUID.randomUUID(), tenant, h, "fk_inactiva", false, null, null));
            return Optional.empty();
        }
        public Optional<ApiKey> buscar(UUID id) { return Optional.empty(); }
        public List<ApiKey> listarPorTenant(UUID tenantId) { return List.of(); }
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

    @Test void keyInactivaResponde401() throws Exception {
        MockHttpServletRequest reqInvalida = new MockHttpServletRequest("GET", "/v1/facturas");
        reqInvalida.addHeader("X-Api-Key", "fk_otra");
        MockHttpServletResponse resInvalida = new MockHttpServletResponse();
        filter.doFilter(reqInvalida, resInvalida, new MockFilterChain());

        MockHttpServletRequest reqInactiva = new MockHttpServletRequest("GET", "/v1/facturas");
        reqInactiva.addHeader("X-Api-Key", keyInactiva);
        MockHttpServletResponse resInactiva = new MockHttpServletResponse();
        filter.doFilter(reqInactiva, resInactiva, new MockFilterChain());

        assertThat(resInactiva.getStatus()).isEqualTo(401);
        assertThat(resInactiva.getContentAsString()).isEqualTo(resInvalida.getContentAsString());
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

    /** MockHttpServletRequest no decodifica ni limpia la URI: es el filtro (vía UrlPathHelper) quien debe hacerlo. */
    @Test void rutaAdminDisfrazadaSeTrataComoAdminYNoExigeApiKey() throws Exception {
        for (String uri : new String[]{"/v1;x/admin/tenants", "/v1/%61dmin/tenants", "/v1//admin/tenants", "/v1/facturas/../admin/tenants"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
            assertThat(chain.getRequest()).as(uri).isNotNull();
            assertThat(res.getStatus()).as(uri).isEqualTo(200);
        }
    }

    @Test void rutasPublicasDeAuthNoExigenApiKey() throws Exception {
        for (String uri : new String[]{"/v1/auth/registro", "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/recuperar", "/v1/auth/restablecer"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
            assertThat(chain.getRequest()).as(uri).isNotNull();
        }
    }

    /** Los catálogos SUNAT son referencia pública: un integrador los consulta antes de tener API key. */
    @Test void catalogosSonPublicos() throws Exception {
        for (String uri : new String[]{"/v1/catalogos", "/v1/catalogos/07"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
            assertThat(chain.getRequest()).as(uri).isNotNull();
        }
    }

    @Test void peticionYaAutenticadaPorJwtNoExigeApiKey() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        req.setAttribute(CuentaActual.ATRIBUTO, UUID.randomUUID());
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test void rutaApiConParametroDeSegmentoSigueExigiendoApiKey() throws Exception {
        for (String uri : new String[]{"/v1;x/facturas", "/v1/%66acturas", "/v1", "/v1/admin/../facturas"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, res, chain);
            assertThat(chain.getRequest()).as(uri).isNull();
            assertThat(res.getStatus()).as(uri).isEqualTo(401);
        }
    }
}
