package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AltaAsistidaUseCase.AltaCreada;
import pe.factura.application.port.in.AltaAsistidaUseCase.Solicitud;
import pe.factura.application.port.in.Idempotencia;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;
import pe.factura.domain.tenant.ApiKey;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Serie;
import pe.factura.domain.tenant.Tenant;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Alta asistida de un cliente (#188): cuenta + empresa + primera serie + API key + invitación, en un solo paso. */
class AltaAsistidaServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
    static final String PORTAL = "https://portal.khipu.test";

    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    /** Cuántas de las escrituras ocurrieron dentro de la transacción: todas, o el alta puede quedar a medias. */
    List<Boolean> escrituras = new ArrayList<>();

    Map<UUID, Cuenta> cuentasMap = new HashMap<>();
    CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { escrituras.add(uow.dentro); cuentasMap.put(c.id(), c); }
        public Optional<Cuenta> buscar(UUID id) { return Optional.ofNullable(cuentasMap.get(id)); }
        public Optional<Cuenta> buscarPorEmail(String e) { return cuentasMap.values().stream().filter(c -> c.email().equals(e)).findFirst(); }
    };
    Map<UUID, Usuario> usuariosMap = new HashMap<>();
    UsuarioRepository usuarios = new UsuarioRepository() {
        public void guardar(Usuario u) { escrituras.add(uow.dentro); usuariosMap.put(u.id(), u); }
        public Optional<Usuario> buscar(UUID id) { return Optional.ofNullable(usuariosMap.get(id)); }
        public Optional<Usuario> buscarPorEmail(String e) { return usuariosMap.values().stream().filter(u -> u.email().equals(e)).findFirst(); }
    };
    Map<String, SesionRepository.TokenRecuperacion> invitaciones = new HashMap<>();
    SesionRepository sesiones = new SesionRepository() {
        public void crear(Sesion s) { throw new AssertionError("un alta asistida no abre sesión"); }
        public Optional<Sesion> buscarPorRefreshHash(String h) { return Optional.empty(); }
        public void revocar(UUID id) {}
        public void revocarTodas(UUID usuarioId) {}
        public void crearRecuperacion(TokenRecuperacion t) { escrituras.add(uow.dentro); invitaciones.put(t.tokenHash(), t); }
        public Optional<TokenRecuperacion> buscarRecuperacion(String h) { return Optional.ofNullable(invitaciones.get(h)); }
        public void marcarRecuperacionUsada(String h) {}
    };
    Fakes.Tenants tenants = new Fakes.Tenants() {
        @Override public void guardar(Tenant t) { escrituras.add(uow.dentro); super.guardar(t); }
        @Override public void asignarCuenta(UUID t, UUID c) { escrituras.add(uow.dentro); super.asignarCuenta(t, c); }
    };
    Fakes.Series series = new Fakes.Series() {
        @Override public void crear(Serie s) { escrituras.add(uow.dentro); super.crear(s); }
    };
    Map<String, ApiKey> keys = new HashMap<>();
    ApiKeyRepository apiKeys = new ApiKeyRepository() {
        public void guardar(ApiKey k) { escrituras.add(uow.dentro); keys.put(k.hash(), k); }
        public Optional<ApiKey> buscarPorHash(String h) { return Optional.ofNullable(keys.get(h)); }
        public Optional<ApiKey> buscar(UUID id) { return keys.values().stream().filter(k -> k.id().equals(id)).findFirst(); }
        public List<ApiKey> listarPorTenant(UUID tenantId) { return keys.values().stream().filter(k -> k.tenantId().equals(tenantId)).toList(); }
    };
    PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "H(" + p + ")"; }
        public boolean coincide(String p, String h) { return h.equals("H(" + p + ")"); }
    };
    List<String> correos = new ArrayList<>();
    List<Boolean> correoDentroDeLaTransaccion = new ArrayList<>();
    RuntimeException correoFalla;
    /** {@code false} simula el adaptador que solo escribe el correo en el log (sin SMTP): no lanza, pero no entrega. */
    boolean correoEntrega = true;
    CorreoSender correo = new CorreoSender() {
        public void enviar(String para, String asunto, String cuerpo) {
            correoDentroDeLaTransaccion.add(uow.dentro);
            if (correoFalla != null) throw correoFalla;
            correos.add(para + "|" + asunto + "|" + cuerpo);
        }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpo); }
        @Override public boolean entregaDeVerdad() { return correoEntrega; }
    };
    Fakes.Auditoria auditoria = new Fakes.Auditoria() {
        @Override public void registrar(RegistroAuditoria r) { escrituras.add(uow.dentro); super.registrar(r); }
    };
    { auditoria.uow = uow; }

    Fakes.Idempotencias claves = new Fakes.Idempotencias();
    { claves.uow = uow; }
    /** «Cifra» invirtiendo los bytes: basta para comprobar que la respuesta, con la API key, no se guarda en claro. */
    SecretCipher cifrador = new SecretCipher() {
        public byte[] cifrar(byte[] p) { return invertir(p); }
        public byte[] descifrar(byte[] c) { return invertir(c); }
    };

    static byte[] invertir(byte[] b) {
        byte[] r = new byte[b.length];
        for (int i = 0; i < b.length; i++) r[i] = b[b.length - 1 - i];
        return r;
    }

    AltaAsistidaService service = null;
    AltaAsistidaService servicio() {
        if (service == null) service = new AltaAsistidaService(cuentas, usuarios, sesiones, tenants, series, apiKeys, hasher, correo, uow, auditoria, "pepper", Fakes.CLOCK, claves, cifrador);
        return service;
    }

    static Solicitud solicitud() {
        return new Solicitud("Comercial Andina", "Ana@Andina.pe", "987654321", "20100066603", "COMERCIAL ANDINA SAC", null, TipoDocumento.FACTURA, "F001");
    }

    @Test void creaLaCuentaSuUsuarioLaEmpresaLaSerieYLaApiKeyEnUnSoloPaso() {
        AltaCreada r = servicio().alta(ACTOR, solicitud(), PORTAL);

        Cuenta cuenta = cuentasMap.get(r.cuentaId());
        assertThat(cuenta.nombre()).isEqualTo("Comercial Andina");
        assertThat(cuenta.email()).isEqualTo("ana@andina.pe");
        assertThat(cuenta.telefono()).isEqualTo("987654321");

        Usuario usuario = usuarios.buscarPorEmail("ana@andina.pe").orElseThrow();
        assertThat(usuario.cuentaId()).isEqualTo(cuenta.id());
        assertThat(usuario.rol()).isEqualTo(Rol.ADMIN);
        assertThat(usuario.activo()).isTrue();

        Tenant tenant = tenants.buscarPorRuc("20100066603").orElseThrow();
        assertThat(r.tenant().id()).isEqualTo(tenant.id());
        assertThat(tenants.cuentaDe(tenant.id())).contains(cuenta.id());
        assertThat(tenant.entorno()).as("sin entorno en la solicitud, BETA como en el portal").isEqualTo(Entorno.BETA);

        assertThat(r.apiKeyEnClaro()).startsWith("fk_");
        assertThat(keys).containsKey(ApiKeyGenerator.hash(r.apiKeyEnClaro(), "pepper"));
        assertThat(series.buscar(tenant.id(), TipoDocumento.FACTURA, "F001")).isPresent();
        assertThat(series.siguienteNumero(tenant.id(), TipoDocumento.FACTURA, "F001")).as("la serie arranca en el 1").isEqualTo(1);
        assertThat(r.tipoSerie()).isEqualTo(TipoDocumento.FACTURA);
        assertThat(r.serie()).isEqualTo("F001");
    }

    @Test void respetaElEntornoPedido() {
        Solicitud s = new Solicitud("Comercial Andina", "ana@andina.pe", null, "20100066603", "COMERCIAL ANDINA SAC", Entorno.PRODUCCION, TipoDocumento.BOLETA, "B001");
        servicio().alta(ACTOR, s, PORTAL);
        assertThat(tenants.buscarPorRuc("20100066603").orElseThrow().entorno()).isEqualTo(Entorno.PRODUCCION);
    }

    @Test void elAdministradorNuncaEligeLaContrasenaYNadieMasLaConoce() {
        servicio().alta(ACTOR, solicitud(), PORTAL);
        servicio().alta(ACTOR, new Solicitud("Otra", "otra@andina.pe", null, "20601234565", "OTRA SAC", null, TipoDocumento.FACTURA, "F001"), PORTAL);

        List<String> hashes = usuariosMap.values().stream().map(Usuario::passwordHash).toList();
        assertThat(hashes).hasSize(2).doesNotHaveDuplicates();
        // Una contraseña aleatoria de 43 caracteres: ni vacía ni algo que alguien pudiera teclear.
        for (String h : hashes) assertThat(h).startsWith("H(").hasSizeGreaterThan(40);
        for (Usuario u : usuariosMap.values()) assertThat(hasher.coincide("", u.passwordHash())).isFalse();
    }

    @Test void mandaUnaInvitacionConUnEnlaceDeUnSoloUsoQueVenceEnSieteDias() {
        servicio().alta(ACTOR, solicitud(), PORTAL);

        assertThat(correos).hasSize(1);
        String[] partes = correos.get(0).split("\\|", 3);
        assertThat(partes[0]).isEqualTo("ana@andina.pe");
        Matcher m = Pattern.compile(Pattern.quote(PORTAL) + "/restablecer/([A-Za-z0-9_-]+)\\?invitacion=1").matcher(partes[2]);
        assertThat(m.find()).as("el enlace de invitación en el cuerpo: " + partes[2]).isTrue();

        var guardado = invitaciones.get(TokenOpaco.hash(m.group(1)));
        assertThat(guardado).as("se guarda el hash, no el token").isNotNull();
        assertThat(guardado.usado()).isFalse();
        assertThat(guardado.expiraEn()).isEqualTo(Fakes.CLOCK.instant().plus(Duration.ofDays(7)));
        assertThat(guardado.usuarioId()).isEqualTo(usuarios.buscarPorEmail("ana@andina.pe").orElseThrow().id());
        assertThat(invitaciones).as("el token no se guarda en claro").doesNotContainKey(m.group(1));
    }

    @Test void laBitacoraRegistraElAltaConActorCuentaYEmpresaSinSecretos() {
        AltaCreada r = servicio().alta(ACTOR, solicitud(), PORTAL);

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria reg = auditoria.registros.get(0);
        assertThat(reg.actor()).isEqualTo(ACTOR);
        assertThat(reg.accion()).isEqualTo(AccionAdmin.CREAR_CUENTA);
        assertThat(reg.cuentaId()).isEqualTo(r.cuentaId());
        assertThat(reg.tenantId()).isEqualTo(r.tenant().id());
        assertThat(reg.ocurridoEn()).isEqualTo(Fakes.CLOCK.instant());
        assertThat(reg.detalle()).contains("20100066603", "F001", "BETA");
        assertThat(reg.detalle()).doesNotContain(r.apiKeyEnClaro()).doesNotContain("ana@andina.pe");
        invitaciones.values().forEach(t -> assertThat(reg.detalle()).doesNotContain(t.tokenHash()));
    }

    @Test void todaEscrituraOcurreDentroDeLaMismaTransaccion() {
        servicio().alta(ACTOR, solicitud(), PORTAL);

        // cuenta, usuario, empresa, asignación a la cuenta, API key, serie, invitación y bitácora.
        assertThat(escrituras).hasSize(8).containsOnly(true);
    }

    @Test void elCorreoSaleDespuesDeLaTransaccionNoDentro() {
        servicio().alta(ACTOR, solicitud(), PORTAL);
        assertThat(correoDentroDeLaTransaccion).containsExactly(false);
    }

    @Test void siElCorreoFallaElAltaQuedaHechaYSeAvisaQueNoSalioLaInvitacion() {
        correoFalla = new IllegalStateException("SMTP caído");
        AltaCreada r = servicio().alta(ACTOR, solicitud(), PORTAL);

        assertThat(r.invitacionEnviada()).isFalse();
        assertThat(cuentasMap).containsKey(r.cuentaId());
        assertThat(tenants.buscarPorRuc("20100066603")).isPresent();
        assertThat(r.apiKeyEnClaro()).as("la API key se entrega igual: es la única vez").startsWith("fk_");
    }

    /**
     * Sin SMTP (el default de MAIL_HABILITADO) el adaptador escribe el correo en el log y no lanza. Antes la respuesta decía
     * «invitación enviada» y el cliente nunca la recibía. Se sigue «enviando» —en desarrollo el log es por donde se lee el
     * enlace—, pero la respuesta no afirma una entrega que no ocurrió.
     */
    @Test void siElCorreoSoloQuedaEnElLogLaRespuestaDiceQueLaInvitacionNoSalio() {
        correoEntrega = false;
        AltaCreada r = servicio().alta(ACTOR, solicitud(), PORTAL);

        assertThat(r.invitacionEnviada()).isFalse();
        assertThat(correos).as("el enlace sigue quedando donde el adaptador lo deja (el log, en desarrollo)").hasSize(1);
        assertThat(cuentasMap).containsKey(r.cuentaId());
    }

    @Test void siElCorreoSaleLaRespuestaLoDice() {
        assertThat(servicio().alta(ACTOR, solicitud(), PORTAL).invitacionEnviada()).isTrue();
    }

    @Test void validaTodoAntesDeEscribirAlgo() {
        List<Solicitud> invalidas = List.of(
                new Solicitud("Comercial Andina", "no-es-un-correo", null, "20100066603", "A SAC", null, TipoDocumento.FACTURA, "F001"),
                new Solicitud("Comercial Andina", "ana@andina.pe", null, "123", "A SAC", null, TipoDocumento.FACTURA, "F001"),
                new Solicitud("Comercial Andina", "ana@andina.pe", null, "20100066603", "A SAC", null, TipoDocumento.FACTURA, "B001"),
                new Solicitud("Comercial Andina", "ana@andina.pe", "12345", "20100066603", "A SAC", null, TipoDocumento.FACTURA, "F001"),
                new Solicitud(" ", "ana@andina.pe", null, "20100066603", "A SAC", null, TipoDocumento.FACTURA, "F001"),
                new Solicitud("Comercial Andina", "ana@andina.pe", null, "20100066603", " ", null, TipoDocumento.FACTURA, "F001"),
                new Solicitud("Comercial Andina", "ana@andina.pe", null, "20100066603", "A SAC", null, null, "F001"));
        for (Solicitud s : invalidas) {
            assertThatThrownBy(() -> servicio().alta(ACTOR, s, PORTAL)).as(s.toString()).isInstanceOf(DomainException.class);
        }
        assertThat(cuentasMap).isEmpty();
        assertThat(usuariosMap).isEmpty();
        assertThat(tenants.datos).isEmpty();
        assertThat(keys).isEmpty();
        assertThat(invitaciones).isEmpty();
        assertThat(escrituras).isEmpty();
        assertThat(correos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void laSerieDebeSerDelTipoPedido() {
        Solicitud s = new Solicitud("Comercial Andina", "ana@andina.pe", null, "20100066603", "A SAC", null, TipoDocumento.BOLETA, "F001");
        assertThatThrownBy(() -> servicio().alta(ACTOR, s, PORTAL)).extracting("codigo").isEqualTo("SERIE_INVALIDA");
    }

    @Test void unCorreoYaRegistradoSeRechazaSinDejarNada() {
        servicio().alta(ACTOR, solicitud(), PORTAL);
        int cuentasAntes = cuentasMap.size();
        Solicitud otra = new Solicitud("Otra", "ANA@andina.pe", null, "20601234565", "OTRA SAC", null, TipoDocumento.FACTURA, "F001");

        assertThatThrownBy(() -> servicio().alta(ACTOR, otra, PORTAL)).extracting("codigo").isEqualTo("DUPLICADO");
        assertThat(cuentasMap).hasSize(cuentasAntes);
        assertThat(tenants.buscarPorRuc("20601234565")).isEmpty();
        assertThat(auditoria.registros).hasSize(1);
    }

    @Test void unaEmpresaYaRegistradaSeRechazaSinCrearLaCuenta() {
        servicio().alta(ACTOR, solicitud(), PORTAL);
        Solicitud otra = new Solicitud("Otra", "otra@andina.pe", null, "20100066603", "OTRA SAC", null, TipoDocumento.FACTURA, "F001");

        assertThatThrownBy(() -> servicio().alta(ACTOR, otra, PORTAL)).extracting("codigo").isEqualTo("DUPLICADO");
        assertThat(usuarios.buscarPorEmail("otra@andina.pe")).as("sin cuenta huérfana").isEmpty();
        assertThat(cuentas.buscarPorEmail("otra@andina.pe")).isEmpty();
        assertThat(auditoria.registros).hasSize(1);
    }

    @Test void siLaBitacoraFallaElAltaFalla() {
        auditoria.falla = new IllegalStateException("tabla de auditoría no disponible");
        assertThatThrownBy(() -> servicio().alta(ACTOR, solicitud(), PORTAL)).isInstanceOf(IllegalStateException.class);
        assertThat(correos).as("sin alta no hay invitación").isEmpty();
    }

    // --- #219: idempotencia ------------------------------------------------------------------------------------------------------

    static final Idempotencia CLAVE = new Idempotencia("a1b2c3d4-0000-4000-8000-000000000001", "huella-1");

    /** El caso del issue: la respuesta del alta se perdió; el reintento recibe la misma API key, sin crear ni invitar otra vez. */
    @Test void unReintentoConLaMismaClaveDevuelveLaMismaRespuestaConLaApiKey() {
        var primero = servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE);
        var reintento = servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE);

        assertThat(primero.repetida()).isFalse();
        assertThat(reintento.repetida()).isTrue();
        assertThat(reintento.alta()).isEqualTo(primero.alta());
        assertThat(reintento.alta().apiKeyEnClaro()).isEqualTo(primero.alta().apiKeyEnClaro());
        assertThat(cuentasMap).hasSize(1);
        assertThat(keys).hasSize(1);
        assertThat(correos).as("una sola invitación").hasSize(1);
        assertThat(auditoria.registros).as("una sola entrada en la bitácora").hasSize(1);
    }

    @Test void laRespuestaGuardadaVaCifradaYNuncaConLaApiKeyEnClaro() {
        var alta = servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE).alta();

        byte[] guardada = claves.filas.get("alta-cuenta|" + CLAVE.clave()).respuestaCifrada();
        assertThat(new String(guardada, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(alta.apiKeyEnClaro());
        assertThat(new String(invertir(guardada), java.nio.charset.StandardCharsets.UTF_8)).as("y descifrada sí la tiene").contains(alta.apiKeyEnClaro());
    }

    @Test void laClaveSeReservaYCompletaDentroDeLaTransaccion() {
        servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE);

        assertThat(claves.reservadoDentro).containsExactly(true);
        assertThat(claves.completadoDentro).as("la respuesta con la API key, junto con el alta").first().isEqualTo(true);
    }

    /** Si la invitación no salió, el reintento lo sigue diciendo: la respuesta guardada es la que vio el administrador. */
    @Test void elReintentoConservaSiLaInvitacionSalio() {
        correoEntrega = false;
        servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE);
        correoEntrega = true;

        assertThat(servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE).alta().invitacionEnviada()).isFalse();
    }

    @Test void conLaInvitacionEnviadaElReintentoTambienLoDice() {
        servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE);
        assertThat(servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE).alta().invitacionEnviada()).isTrue();
    }

    @Test void laMismaClaveConOtroPedidoSeRechazaSinCrearNada() {
        servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE);
        Solicitud otra = new Solicitud("Otra", "otra@andina.pe", null, "20601234565", "OTRA SAC", null, TipoDocumento.FACTURA, "F001");

        assertThatThrownBy(() -> servicio().alta(ACTOR, otra, PORTAL, new Idempotencia(CLAVE.clave(), "otra-huella")))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("IDEMPOTENCIA_INVALIDA");
        assertThat(cuentasMap).hasSize(1);
    }

    /** Pasada la hora la API key ya no se guarda: el reintento se reconoce, no crea un alta duplicada, y dice qué hacer. */
    @Test void pasadaLaHoraElReintentoSeReconocePeroYaNoDevuelveLaApiKey() {
        servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE);
        claves.olvidarRespuestasAnterioresA(null);

        assertThatThrownBy(() -> servicio().alta(ACTOR, solicitud(), PORTAL, CLAVE))
                .isInstanceOf(DomainException.class).hasMessageContaining("otra API key")
                .extracting("codigo").isEqualTo("IDEMPOTENCIA_VENCIDA");
        assertThat(cuentasMap).hasSize(1);
    }

    @Test void sinClaveUnReintentoSigueSiendoDuplicado() {
        servicio().alta(ACTOR, solicitud(), PORTAL, null);
        assertThatThrownBy(() -> servicio().alta(ACTOR, solicitud(), PORTAL, null)).extracting("codigo").isEqualTo("DUPLICADO");
        assertThat(claves.filas).isEmpty();
    }
}
