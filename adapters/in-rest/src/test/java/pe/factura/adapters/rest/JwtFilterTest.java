package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.application.port.out.UsuarioRepository;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;
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
    /** El usuario del token, verificado por defecto: lo que prueban los tests de empresa no depende de la verificación (#22). */
    Usuario delToken = new Usuario(usuario, cuenta, "ana@b.pe", "hash", Rol.ADMIN, true, java.time.Instant.parse("2026-10-01T00:00:00Z"));
    UsuarioRepository usuarios = new UsuarioRepository() {
        public void guardar(Usuario u) {}
        public Optional<Usuario> buscar(UUID id) { return delToken != null && delToken.id().equals(id) ? Optional.of(delToken) : Optional.empty(); }
        public Optional<Usuario> buscarPorEmail(String e) { return Optional.empty(); }
    };
    JwtFilter filter = new JwtFilter(tokenEmisor, tenants, usuarios);

    // --- #22: sin verificar el correo se puede mirar, no escribir ------------------------------------------------------------------

    private MockHttpServletResponse pedir(String metodo, String uri, MockFilterChain chain) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, uri);
        req.addHeader("Authorization", "Bearer " + tokenValido);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }

    @Test void sinVerificarElCorreoNingunaEscrituraPasa() throws Exception {
        delToken = new Usuario(usuario, cuenta, "ana@b.pe", "hash", Rol.ADMIN, true);
        for (String[] m : new String[][]{{"POST", "/v1/empresas"}, {"POST", "/v1/facturas"}, {"PUT", "/v1/empresa/datos-fiscales"},
                {"DELETE", "/v1/empresa/api-keys/1"}, {"PATCH", "/v1/series/F001"}}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse res = pedir(m[0], m[1], chain);
            assertThat(chain.getRequest()).as("%s %s", m[0], m[1]).isNull();
            assertThat(res.getStatus()).as("%s %s", m[0], m[1]).isEqualTo(403);
            assertThat(res.getContentAsString()).contains("CORREO_SIN_VERIFICAR");
        }
    }

    @Test void sinVerificarSePuedeMirarYManejarLaSesion() throws Exception {
        delToken = new Usuario(usuario, cuenta, "ana@b.pe", "hash", Rol.ADMIN, true);
        for (String[] m : new String[][]{{"GET", "/v1/empresas"}, {"HEAD", "/v1/facturas"}, {"OPTIONS", "/v1/facturas"},
                {"POST", "/v1/auth/verificacion"}, {"POST", "/v1/auth/logout"}}) {
            MockFilterChain chain = new MockFilterChain();
            pedir(m[0], m[1], chain);
            assertThat(chain.getRequest()).as("%s %s", m[0], m[1]).isNotNull();
        }
    }

    @Test void verificadoEscribeComoSiempre() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        pedir("POST", "/v1/empresas", chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    /** Un token de un usuario que ya no existe o se desactivó no escribe: falla cerrado. */
    @Test void sinUsuarioOConElUsuarioInactivoNoEscribe() throws Exception {
        delToken = null;
        MockHttpServletResponse res = pedir("POST", "/v1/empresas", new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);

        delToken = new Usuario(usuario, cuenta, "ana@b.pe", "hash", Rol.ADMIN, false, java.time.Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(pedir("POST", "/v1/empresas", new MockFilterChain()).getStatus()).isEqualTo(401);
    }

    /** La ruta se decide normalizada: un {@code ;x} o un {@code ..} no hace pasar una escritura por una ruta de auth. */
    @Test void unaRutaDisfrazadaDeAuthNoEsquivaElBloqueo() throws Exception {
        delToken = new Usuario(usuario, cuenta, "ana@b.pe", "hash", Rol.ADMIN, true);
        for (String uri : new String[]{"/v1/auth/../empresas", "/v1/auth;x/../empresas"}) {
            MockFilterChain chain = new MockFilterChain();
            assertThat(pedir("POST", uri, chain).getStatus()).as(uri).isEqualTo(403);
            assertThat(chain.getRequest()).as(uri).isNull();
        }
    }

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
        for (String uri : new String[]{"/v1/auth/registro", "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/recuperar", "/v1/auth/restablecer", "/v1/auth/verificar"}) {
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
