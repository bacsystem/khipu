package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtFilterTest {
    UUID usuario = UUID.randomUUID();
    UUID cuenta = UUID.randomUUID();
    UUID empresaPropia = UUID.randomUUID();
    UUID empresaAjena = UUID.randomUUID();
    String tokenValido = "jwt-valido";
    String tokenInvalido = "jwt-basura";

    Map<UUID, UUID> cuentaPorEmpresa = new HashMap<>() {{
        put(empresaPropia, cuenta);
        put(empresaAjena, UUID.randomUUID());
    }};

    TokenEmisor tokenEmisor = new TokenEmisor() {
        public String emitir(Claims c) { throw new UnsupportedOperationException(); }
        public Optional<Claims> verificar(String token) {
            return tokenValido.equals(token) ? Optional.of(new Claims(usuario, cuenta, Rol.ADMIN)) : Optional.empty();
        }
    };
    TenantRepository tenants = new TenantRepository() {
        public void guardar(Tenant t) {}
        public Optional<Tenant> buscar(UUID id) { return Optional.empty(); }
        public Optional<Tenant> buscarPorRuc(String ruc) { return Optional.empty(); }
        public java.util.List<Tenant> listarPorCuenta(UUID c) { return java.util.List.of(); }
        public void asignarCuenta(UUID t, UUID c) {}
        public Optional<UUID> cuentaDe(UUID t) { return Optional.ofNullable(cuentaPorEmpresa.get(t)); }
    };
    JwtFilter filter = new JwtFilter(tokenEmisor, tenants);

    @Test void tokenValidoSinEmpresaExponeSoloLaCuenta() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/empresas");
        req.addHeader("Authorization", "Bearer " + tokenValido);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(CuentaActual.id(req)).isEqualTo(cuenta);
        assertThat(UsuarioActual.id(req)).isEqualTo(usuario);
        assertThat(req.getAttribute(TenantActual.ATRIBUTO)).isNull();
    }

    @Test void tokenValidoConEmpresaPropiaExponeElTenant() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        req.addHeader("Authorization", "Bearer " + tokenValido);
        req.addHeader("X-Empresa", empresaPropia.toString());
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(TenantActual.id(req)).isEqualTo(empresaPropia);
    }

    @Test void empresaAjenaResponde403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        req.addHeader("Authorization", "Bearer " + tokenValido);
        req.addHeader("X-Empresa", empresaAjena.toString());
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("EMPRESA_AJENA");
    }

    @Test void empresaInexistenteResponde403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        req.addHeader("Authorization", "Bearer " + tokenValido);
        req.addHeader("X-Empresa", UUID.randomUUID().toString());
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test void empresaConFormatoInvalidoResponde400() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        req.addHeader("Authorization", "Bearer " + tokenValido);
        req.addHeader("X-Empresa", "no-es-un-uuid");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(res.getContentAsString()).contains("EMPRESA_INVALIDA");
    }

    @Test void tokenInvalidoResponde401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/empresas");
        req.addHeader("Authorization", "Bearer " + tokenInvalido);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test void sinAuthorizationNoIntercepta() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(req.getAttribute(CuentaActual.ATRIBUTO)).isNull();
    }

    @Test void conApiKeyPresenteSeAparta() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/facturas");
        req.addHeader("Authorization", "Bearer " + tokenValido);
        req.addHeader("X-Api-Key", "fk_algo");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(req.getAttribute(CuentaActual.ATRIBUTO)).isNull();
    }

    @Test void rutasPublicasDeAuthNoInterceptan() throws Exception {
        for (String uri : new String[]{"/v1/auth/registro", "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/recuperar", "/v1/auth/restablecer"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(req, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(uri).isNotNull();
        }
    }

    @Test void rutasAdminNoInterceptan() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/admin/tenants");
        req.addHeader("Authorization", "Bearer " + tokenValido);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(req.getAttribute(CuentaActual.ATRIBUTO)).isNull();
    }
}
