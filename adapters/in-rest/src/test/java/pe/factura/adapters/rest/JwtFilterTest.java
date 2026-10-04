package pe.factura.adapters.rest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.factura.application.port.out.SuspensionRepository;
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
    static final String TOKEN_DE_SOPORTE = "jwt-de-soporte";
    UUID administrador = UUID.randomUUID();

    Map<UUID, UUID> cuentaPorEmpresa = new HashMap<>() {{
        put(empresaPropia, cuenta);
        put(empresaAjena, UUID.randomUUID());
    }};

    TokenEmisor tokenEmisor = new TokenEmisor() {
        public String emitir(Claims c) { throw new UnsupportedOperationException(); }
        public Optional<Claims> verificar(String token) {
            if (TOKEN_DE_SOPORTE.equals(token)) return Optional.of(new Claims(usuario, cuenta, Rol.ADMIN, new Soporte(administrador, java.time.Instant.parse("2026-10-04T12:15:00Z"))));
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
        public void marcarCorreoVerificado(UUID id, java.time.Instant cuando) {}
    };
    java.util.Set<UUID> cuentasSuspendidas = new java.util.HashSet<>();
    int consultasDeSuspension = 0;
    SuspensionRepository suspensiones = new SuspensionRepository() {
        public boolean cuentaSuspendida(UUID c) { consultasDeSuspension++; return cuentasSuspendidas.contains(c); }
        public boolean empresaSuspendida(UUID t) { throw new AssertionError("el filtro JWT trabaja por cuenta"); }
        public boolean suspender(UUID c, java.time.Instant cuando) { throw new AssertionError("un filtro no suspende"); }
        public boolean reactivar(UUID c) { throw new AssertionError("un filtro no reactiva"); }
    };
    JwtFilter filter = new JwtFilter(tokenEmisor, tenants, usuarios, suspensiones);

    // --- #22: sin verificar el correo se puede mirar, no escribir ------------------------------------------------------------------

    private MockHttpServletResponse pedir(String metodo, String uri, MockFilterChain chain) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, uri);
        req.addHeader("Authorization", "Bearer " + tokenValido);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }

    // --- #182: una cuenta suspendida no entra, ni con una sesión que ya estaba abierta ---------------------------------------------

    @Test void unaCuentaSuspendidaNoPasaNiParaMirarNiParaEscribir() throws Exception {
        cuentasSuspendidas.add(cuenta);
        for (String[] m : new String[][]{{"GET", "/v1/empresas"}, {"GET", "/v1/facturas"}, {"POST", "/v1/facturas"}, {"POST", "/v1/empresas"},
                {"PUT", "/v1/empresa/datos-fiscales"}, {"DELETE", "/v1/empresa/api-keys/1"}}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse res = pedir(m[0], m[1], chain);
            assertThat(chain.getRequest()).as("%s %s", m[0], m[1]).isNull();
            assertThat(res.getStatus()).as("%s %s", m[0], m[1]).isEqualTo(403);
            assertThat(res.getContentAsString()).as("%s %s", m[0], m[1]).contains("\"codigo\":\"CUENTA_SUSPENDIDA\"").contains("\"estado\":\"error\"");
        }
    }

    @Test void conLaEmpresaElegidaTambienSeCorta() throws Exception {
        cuentasSuspendidas.add(cuenta);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/facturas");
        req.addHeader("Authorization", "Bearer " + tokenValido);
        req.addHeader("X-Empresa", empresaPropia.toString());
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(req.getAttribute(TenantActual.ATRIBUTO)).as("no se llega a fijar la empresa activa").isNull();
    }

    /** El portal sigue sabiendo quién es el usuario y puede cerrar la sesión, para mostrar «cuenta suspendida» en vez de un error suelto. */
    @Test void lasRutasDeLaPropiaSesionSiguenFuncionandoConLaCuentaSuspendida() throws Exception {
        cuentasSuspendidas.add(cuenta);
        for (String[] m : new String[][]{{"GET", "/v1/auth/me"}, {"POST", "/v1/auth/logout"}, {"POST", "/v1/auth/verificacion"}}) {
            MockFilterChain chain = new MockFilterChain();
            pedir(m[0], m[1], chain);
            assertThat(chain.getRequest()).as("%s %s", m[0], m[1]).isNotNull();
        }
    }

    @Test void reactivadaLaCuentaVuelveAPasarConLaMismaSesion() throws Exception {
        cuentasSuspendidas.add(cuenta);
        MockFilterChain bloqueada = new MockFilterChain();
        pedir("POST", "/v1/facturas", bloqueada);
        assertThat(bloqueada.getRequest()).isNull();

        cuentasSuspendidas.clear();
        MockFilterChain pasa = new MockFilterChain();
        MockHttpServletResponse res = pedir("POST", "/v1/facturas", pasa);

        assertThat(pasa.getRequest()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test void suspenderOtraCuentaNoAfectaAEstaYLaCuentaDelTokenEsLaQueSeConsulta() throws Exception {
        cuentasSuspendidas.add(UUID.randomUUID());
        MockFilterChain chain = new MockFilterChain();

        pedir("POST", "/v1/facturas", chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(consultasDeSuspension).isEqualTo(1);
    }

    /** Quien presenta un token inválido no se entera de nada de ninguna cuenta: se rechaza antes de mirar el estado. */
    @Test void conUnTokenInvalidoSeRechazaSinConsultarLaSuspension() throws Exception {
        cuentasSuspendidas.add(cuenta);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/empresas");
        req.addHeader("Authorization", "Bearer " + tokenInvalido);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).doesNotContain("SUSPENDIDA");
        assertThat(consultasDeSuspension).isZero();
    }

    @Test void laSuspensionVaAntesQueLaVerificacionDelCorreo() throws Exception {
        delToken = new Usuario(usuario, cuenta, "ana@b.pe", "hash", Rol.ADMIN, true);
        cuentasSuspendidas.add(cuenta);

        MockHttpServletResponse res = pedir("POST", "/v1/facturas", new MockFilterChain());

        assertThat(res.getContentAsString()).contains("CUENTA_SUSPENDIDA").doesNotContain("CORREO_SIN_VERIFICAR");
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

    // --- #184: una sesión de soporte solo puede mirar ------------------------------------------------------------------------------------

    private MockHttpServletResponse pedirConToken(String token, String metodo, String uri, MockFilterChain chain) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, uri);
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }

    @Test void unaSesionDeSoportePuedeMirar() throws Exception {
        for (String[] m : new String[][]{{"GET", "/v1/empresas"}, {"GET", "/v1/facturas"}, {"GET", "/v1/auth/me"}, {"HEAD", "/v1/empresas"}, {"OPTIONS", "/v1/empresas"}}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse res = pedirConToken(TOKEN_DE_SOPORTE, m[0], m[1], chain);
            assertThat(chain.getRequest()).as("%s %s", m[0], m[1]).isNotNull();
            assertThat(res.getStatus()).as("%s %s", m[0], m[1]).isEqualTo(200);
        }
    }

    /** Nada que escriba: ni la contraseña, ni las credenciales SOL, ni el certificado, ni las API keys, ni emitir, ni lo de la propia sesión. */
    @Test void unaSesionDeSoporteNoPuedeEscribirNada() throws Exception {
        for (String[] m : new String[][]{{"POST", "/v1/empresas"}, {"POST", "/v1/facturas"}, {"PUT", "/v1/empresa/credenciales-sol"}, {"PUT", "/v1/empresa/datos-fiscales"},
                {"POST", "/v1/empresa/certificado"}, {"POST", "/v1/empresa/api-keys"}, {"DELETE", "/v1/empresa/api-keys/1"}, {"POST", "/v1/series"},
                {"POST", "/v1/auth/logout"}, {"POST", "/v1/auth/verificacion"}, {"PATCH", "/v1/empresa"}, {"DELETE", "/v1/facturas/1"}}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse res = pedirConToken(TOKEN_DE_SOPORTE, m[0], m[1], chain);
            assertThat(chain.getRequest()).as("%s %s", m[0], m[1]).isNull();
            assertThat(res.getStatus()).as("%s %s", m[0], m[1]).isEqualTo(403);
            assertThat(res.getContentAsString()).as("%s %s", m[0], m[1]).contains("\"codigo\":\"SOPORTE_SOLO_LECTURA\"").contains("\"estado\":\"error\"");
        }
    }

    /** Se corta antes que todo lo demás: ni se consulta la base para saber si la cuenta está suspendida o el correo verificado. */
    @Test void elCorteDeEscrituraDeSoporteNoConsultaNada() throws Exception {
        pedirConToken(TOKEN_DE_SOPORTE, "POST", "/v1/empresas", new MockFilterChain());

        assertThat(consultasDeSuspension).isZero();
    }

    @Test void unaSesionNormalSigueEscribiendoYNoQuedaMarcadaComoSoporte() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/empresas");
        req.addHeader("Authorization", "Bearer " + tokenValido);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(SoporteActual.de(req)).isEmpty();
    }

    @Test void laPeticionDeSoporteQuedaMarcadaConElAdministradorYSuExpiracion() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/empresas");
        req.addHeader("Authorization", "Bearer " + TOKEN_DE_SOPORTE);

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        var soporte = SoporteActual.de(req).orElseThrow();
        assertThat(soporte.administradorId()).isEqualTo(administrador);
        assertThat(soporte.expiraEn()).isEqualTo(java.time.Instant.parse("2026-10-04T12:15:00Z"));
        assertThat(req.getAttribute(CuentaActual.ATRIBUTO)).as("mira como el usuario de la cuenta").isEqualTo(cuenta);
        assertThat(req.getAttribute(UsuarioActual.ATRIBUTO)).isEqualTo(usuario);
    }

    /** El soporte no abre más puertas que el cliente: su sesión sigue acotada a su cuenta. */
    @Test void conLaEmpresaDeOtraCuentaSigueSiendo403YConLaPropiaMira() throws Exception {
        MockHttpServletRequest ajena = new MockHttpServletRequest("GET", "/v1/series");
        ajena.addHeader("Authorization", "Bearer " + TOKEN_DE_SOPORTE);
        ajena.addHeader("X-Empresa", empresaAjena.toString());
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chainAjena = new MockFilterChain();

        filter.doFilter(ajena, res, chainAjena);

        assertThat(chainAjena.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("EMPRESA_AJENA");

        MockHttpServletRequest propia = new MockHttpServletRequest("GET", "/v1/series");
        propia.addHeader("Authorization", "Bearer " + TOKEN_DE_SOPORTE);
        propia.addHeader("X-Empresa", empresaPropia.toString());
        filter.doFilter(propia, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(propia.getAttribute(TenantActual.ATRIBUTO)).isEqualTo(empresaPropia);
    }

    @Test void unaCuentaSuspendidaTampocoSeMiraEnSoporte() throws Exception {
        cuentasSuspendidas.add(cuenta);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse res = pedirConToken(TOKEN_DE_SOPORTE, "GET", "/v1/empresas", chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("CUENTA_SUSPENDIDA");
    }
}
