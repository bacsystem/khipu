package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import pe.factura.application.port.out.AdministradorTokenEmisor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthFilterTest {
    private static final AdministradorTokenEmisor SIN_TOKENS = new TokensDeAdminFalsos() {};

    @Test void claveCorrectaPasa() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "secreta");
        var chain = new MockFilterChain();
        new AdminAuthFilter("secreta", SIN_TOKENS).doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
    /** Sin esta marca explícita el controlador no sabría quién actuó: no se deduce «clave de plataforma» por descarte. */
    @Test void claveCorrectaMarcaElRequestComoClaveDePlataforma() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "secreta");
        var chain = new MockFilterChain();
        new AdminAuthFilter("secreta", SIN_TOKENS).doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest().getAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA)).isEqualTo(Boolean.TRUE);
        assertThat(chain.getRequest().getAttribute(AdministradorActual.ATRIBUTO)).isNull();
    }
    @Test void claveIncorrecta401() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "otra");
        var res = new MockHttpServletResponse();
        new AdminAuthFilter("secreta", SIN_TOKENS).doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }
    @Test void sinClaveConfigurada404() throws Exception {
        var req = new MockHttpServletRequest("POST", "/v1/admin/tenants"); req.addHeader("X-Platform-Key", "x");
        var res = new MockHttpServletResponse();
        new AdminAuthFilter("", SIN_TOKENS).doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(404);
    }
    @Test void otrasRutasNoSeFiltran() throws Exception {
        var req = new MockHttpServletRequest("GET", "/v1/facturas");
        var chain = new MockFilterChain();
        new AdminAuthFilter("secreta", SIN_TOKENS).doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    /** MockHttpServletRequest no decodifica ni limpia la URI: es el filtro (vía UrlPathHelper) quien debe hacerlo. */
    @Test void rutaAdminDisfrazadaSinClaveEs401() throws Exception {
        for (String uri : new String[]{"/v1;x/admin/tenants", "/v1/%61dmin/tenants", "/v1//admin/tenants", "/v1/admin", "/v1/facturas/../admin/tenants"}) {
            var req = new MockHttpServletRequest("POST", uri);
            var res = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            new AdminAuthFilter("secreta", SIN_TOKENS).doFilter(req, res, chain);
            assertThat(chain.getRequest()).as(uri).isNull();
            assertThat(res.getStatus()).as(uri).isEqualTo(401);
        }
    }
    @Test void rutaNoAdminConParametroDeSegmentoNoSeFiltra() throws Exception {
        for (String uri : new String[]{"/v1;x/facturas", "/v1/admin-x/tenants", "/v1/administracion"}) {
            var req = new MockHttpServletRequest("GET", uri);
            var chain = new MockFilterChain();
            new AdminAuthFilter("secreta", SIN_TOKENS).doFilter(req, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(uri).isNotNull();
        }
    }

    /** El login y sus pasos del segundo factor (#177): el administrador todavía no tiene sesión; los protege el token de desafío. */
    @Test void loginDelBackofficeYSuSegundoFactorNoExigenCredencial() throws Exception {
        for (String uri : new String[]{"/v1/admin/auth/login", "/v1/admin/auth/segundo-factor/configurar",
                "/v1/admin/auth/segundo-factor/confirmar", "/v1/admin/auth/segundo-factor/verificar"}) {
            var req = new MockHttpServletRequest("POST", uri);
            var chain = new MockFilterChain();
            new AdminAuthFilter("secreta", SIN_TOKENS).doFilter(req, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(uri).isNotNull();
        }
    }

    /** Solo esas rutas exactas: un prefijo parecido no abre nada. */
    @Test void otrasRutasDeAuthSiExigenCredencial() throws Exception {
        for (String uri : new String[]{"/v1/admin/auth/segundo-factor", "/v1/admin/auth/segundo-factor/otra", "/v1/admin/auth/me",
                "/v1/admin/auth/login/x", "/v1;x/admin/auth/segundo-factor/configurar/../../me"}) {
            var req = new MockHttpServletRequest("POST", uri);
            var res = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            new AdminAuthFilter("secreta", SIN_TOKENS).doFilter(req, res, chain);
            assertThat(chain.getRequest()).as(uri).isNull();
            assertThat(res.getStatus()).as(uri).isEqualTo(401);
        }
    }

    /** Con la contraseña sola se obtiene un desafío: llevarlo como Bearer no abre el backoffice. */
    @Test void unTokenDeDesafioNoAutentica() throws Exception {
        AdministradorTokenEmisor tokens = new TokensDeAdminFalsos() {
            @Override public Optional<UUID> verificarDesafio(String token) { return Optional.of(UUID.randomUUID()); }
        };
        var req = new MockHttpServletRequest("GET", "/v1/admin/cuentas"); req.addHeader("Authorization", "Bearer desafio");
        var res = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new AdminAuthFilter("secreta", tokens).doFilter(req, res, chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test void jwtDeAdministradorValidoPasaYExponeElId() throws Exception {
        UUID id = UUID.randomUUID();
        AdministradorTokenEmisor tokens = new TokensDeAdminFalsos() {
            @Override public Optional<Claims> verificar(String token) { return "bueno".equals(token) ? Optional.of(new Claims(id, "a@b.pe")) : Optional.empty(); }
        };
        var req = new MockHttpServletRequest("GET", "/v1/admin/auth/me"); req.addHeader("Authorization", "Bearer bueno");
        var chain = new MockFilterChain();
        new AdminAuthFilter("secreta", tokens).doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(chain.getRequest().getAttribute(AdministradorActual.ATRIBUTO)).isEqualTo(id);
        assertThat(chain.getRequest().getAttribute(AdministradorActual.ATRIBUTO_CLAVE_PLATAFORMA)).isNull();
    }

    @Test void jwtDeAdministradorInvalidoEs401AunqueLaClaveDePlataformaEsteVacia() throws Exception {
        var req = new MockHttpServletRequest("GET", "/v1/admin/auth/me"); req.addHeader("Authorization", "Bearer malo");
        var res = new MockHttpServletResponse();
        new AdminAuthFilter("", SIN_TOKENS).doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test void sinNingunaCredencial401() throws Exception {
        var req = new MockHttpServletRequest("GET", "/v1/admin/auth/me");
        var res = new MockHttpServletResponse();
        new AdminAuthFilter("secreta", SIN_TOKENS).doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }
}
