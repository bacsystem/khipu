package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AutenticarUsuarioUseCase.Tokens;
import pe.factura.application.port.out.*;
import pe.factura.domain.plataforma.PlantillaDeCorreo;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.plan.EstadoPlan;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class AutenticarUsuarioServiceTest {
    // Fakes locales de auth
    Map<UUID, Cuenta> cuentasMap = new HashMap<>();
    CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { cuentasMap.put(c.id(), c); }
        public Optional<Cuenta> buscar(UUID id) { return Optional.ofNullable(cuentasMap.get(id)); }
        public Optional<Cuenta> buscarPorEmail(String e) { return cuentasMap.values().stream().filter(c -> c.email().equals(e)).findFirst(); }
    };
    Map<UUID, Usuario> usuariosMap = new HashMap<>();
    UsuarioRepository usuarios = new UsuarioRepository() {
        public void guardar(Usuario u) { usuariosMap.put(u.id(), u); }
        public Optional<Usuario> buscar(UUID id) { return Optional.ofNullable(usuariosMap.get(id)); }
        public Optional<Usuario> buscarPorEmail(String e) { return usuariosMap.values().stream().filter(u -> u.email().equals(e)).findFirst(); }
        public void marcarCorreoVerificado(UUID id, java.time.Instant cuando) { usuariosMap.computeIfPresent(id, (k, u) -> u.conCorreoVerificado(cuando)); }
    };
    Map<String, SesionRepository.Sesion> sesionesMap = new HashMap<>();
    Map<String, SesionRepository.TokenRecuperacion> recMap = new HashMap<>();
    /** Se ejecuta una vez, justo después de la próxima lectura de una sesión por su refresh: simula otra petición que llega entre la lectura y la rotación. */
    Runnable despuesDeLeer;
    SesionRepository sesiones = new SesionRepository() {
        public void crear(Sesion s) { sesionesMap.put(s.refreshHash(), s); }
        public Optional<Sesion> buscarPorRefreshHash(String h) {
            Optional<Sesion> leida = Optional.ofNullable(sesionesMap.get(h));
            if (despuesDeLeer != null) { Runnable otraPeticion = despuesDeLeer; despuesDeLeer = null; otraPeticion.run(); }   // la carrera: algo ocurre tras la lectura
            return leida;
        }
        public Optional<Sesion> buscar(UUID id) { return sesionesMap.values().stream().filter(s -> s.id().equals(id)).findFirst(); }
        /** Como el JDBC: revocar borra las marcas de rotación (sin gracia) y conserva la familia. */
        Sesion revocada(Sesion s) { return new Sesion(s.id(), s.usuarioId(), s.refreshHash(), s.expiraEn(), true, null, null, s.familia()); }
        public void revocar(UUID id) { sesionesMap.replaceAll((k, s) -> s.id().equals(id) ? revocada(s) : s); }
        public void revocarFamilia(UUID f) { sesionesMap.replaceAll((k, s) -> s.familia().equals(f) ? revocada(s) : s); }
        public void revocarTodas(UUID u) { sesionesMap.replaceAll((k, s) -> s.usuarioId().equals(u) ? revocada(s) : s); }
        /** Como el JDBC: no rota una sesión revocada sin rotación previa (logout, restablecer, reuso) y devuelve si la rotó. */
        public boolean rotar(UUID id, UUID nueva, java.time.Instant en) {
            Optional<Sesion> actual = buscar(id).filter(s -> !s.revocada() || s.rotadaEn() != null);
            actual.ifPresent(s -> sesionesMap.put(s.refreshHash(), new Sesion(s.id(), s.usuarioId(), s.refreshHash(), s.expiraEn(), true,
                    s.rotadaEn() == null ? en : s.rotadaEn(), s.reemplazadaPor() == null ? nueva : s.reemplazadaPor(), s.familia())));
            return actual.isPresent();
        }
        public void crearRecuperacion(TokenRecuperacion t) { recMap.put(t.tokenHash(), t); }
        public Optional<TokenRecuperacion> buscarRecuperacion(String h) { return Optional.ofNullable(recMap.get(h)); }
        public void marcarRecuperacionUsada(String h) { recMap.computeIfPresent(h, (k, t) -> new TokenRecuperacion(t.tokenHash(), t.usuarioId(), t.expiraEn(), true)); }
    };
    PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "H(" + p + ")"; }
        public boolean coincide(String p, String h) { return h.equals("H(" + p + ")"); }
    };
    TokenEmisor tokens = new TokenEmisor() {
        public String emitir(Claims c) { return "jwt:" + c.usuarioId() + ":" + c.cuentaId() + ":" + c.rol(); }
        public Optional<Claims> verificar(String t) { return Optional.empty(); }
    };
    List<String> correos = new ArrayList<>();
    CorreoSender correo = new CorreoSender() {
        public void enviar(String para, String asunto, String cuerpo) { correos.add(para + "|" + cuerpo); }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpo); }
    };
    Clock clock = Clock.fixed(java.time.Instant.parse("2026-09-14T12:00:00Z"), ZoneId.of("America/Lima"));
    static final String PORTAL = "https://portal";
    Map<String, VerificacionCorreoRepository.Token> verifMap = new HashMap<>();
    VerificacionCorreoRepository verificaciones = new VerificacionCorreoRepository() {
        public void crear(Token t) { verifMap.put(t.tokenHash(), t); }
        public Optional<Token> buscar(String h) { return Optional.ofNullable(verifMap.get(h)); }
        public boolean usar(String h) {
            Token t = verifMap.get(h);
            if (t == null || t.usado()) return false;
            verifMap.put(h, new Token(t.tokenHash(), t.usuarioId(), t.expiraEn(), true));
            return true;
        }
        public int contarSinVencer(UUID u, java.time.Instant ahora) {
            return (int) verifMap.values().stream().filter(t -> t.usuarioId().equals(u) && t.expiraEn().isAfter(ahora)).count();
        }
    };
    Set<UUID> cuentasSuspendidas = new HashSet<>();
    SuspensionRepository suspensiones = new SuspensionRepository() {
        public boolean cuentaSuspendida(UUID c) { return cuentasSuspendidas.contains(c); }
        public boolean empresaSuspendida(UUID t) { throw new AssertionError("el login trabaja por cuenta"); }
        public boolean suspender(UUID c, java.time.Instant cuando) { throw new AssertionError("el login no suspende"); }
        public boolean reactivar(UUID c) { throw new AssertionError("el login no reactiva"); }
    };
    ConfiguracionFake.Plantillas plantillasGuardadas = new ConfiguracionFake.Plantillas();
    Fakes.Intentos intentos = new Fakes.Intentos();
    LimiteDeIntentos limite = new LimiteDeIntentos(intentos, clock);
    Fakes.Aceptaciones aceptaciones = new Fakes.Aceptaciones();
    static final String TERMINOS = "2026-10-20";
    AceptarTerminosService terminos = new AceptarTerminosService(aceptaciones, Fakes.UOW, clock, TERMINOS);
    /** #581: los planes que conoce el registro, lo que anotó y lo que avisó al operador. */
    GestionarPlanesServiceTest.Planes planes = new GestionarPlanesServiceTest.Planes();
    Map<UUID, UUID> anotados = new HashMap<>();
    PlanSolicitadoRepository planesSolicitados = anotados::put;
    List<String> avisos = new ArrayList<>();
    AvisoAlOperador operador = (asunto, cuerpo) -> avisos.add(asunto + "|" + cuerpo);
    AutenticarUsuarioService service = new AutenticarUsuarioService(cuentas, usuarios, sesiones, hasher, tokens, correo, Fakes.UOW, clock, verificaciones, suspensiones, new PlantillasDeCorreo(plantillasGuardadas), limite, terminos, planes, planesSolicitados, operador);

    /** El token del último correo que contiene {@code ruta}: así llega al usuario, y así se lo usa. */
    private String tokenDelCorreo(String ruta) {
        String c = correos.stream().filter(x -> x.contains(ruta)).reduce((a, b) -> b).orElseThrow();
        return c.substring(c.lastIndexOf(ruta) + ruta.length()).trim();
    }

    // --- #581: el plan que eligió en la página de precios ---------------------------------------------------------------------------

    Plan plan(String nombre, String precio, boolean porDefecto, EstadoPlan estado, boolean publicado) {
        Plan p = new Plan(UUID.randomUUID(), nombre, new BigDecimal(precio), new Limites(Limite.de(300), 1, Limite.de(1), Limite.de(2), 5), estado, porDefecto, null, publicado);
        planes.datos.put(p.id(), p);
        return p;
    }

    UUID cuentaDe(Tokens t) { return t.usuario().cuentaId(); }

    /** Un plan publicado de pago queda anotado en la cuenta, en la transacción del registro, y el operador recibe el aviso con quién es y dónde verla. */
    @Test void elPlanElegidoQuedaAnotadoYSeAvisaAlOperador() {
        Plan negocio = plan("Negocio", "69", false, EstadoPlan.ACTIVO, true);

        Tokens t = service.registrar("Bodega Ana", "a@b.pe", "Segura123", "987654321", true, "negocio", PORTAL);

        assertThat(anotados).containsExactly(Map.entry(cuentaDe(t), negocio.id()));
        assertThat(avisos).singleElement().satisfies(a -> assertThat(a)
                .startsWith("Cuenta nueva pide el plan Negocio|")
                .contains("Bodega Ana").contains("a＠b.pe").contains("987654321")
                .contains(PORTAL + "/admin/cuentas/" + cuentaDe(t)));
    }

    /**
     * #581 (H1 de la revisión): el aviso sale por el webhook del operador (Slack, Discord, Mattermost) y lleva lo que escribió quien se registra. Sin neutralizar,
     * cualquiera mencionaría a todo el canal con su nombre (`<!channel>`, `@everyone`, `@channel`). El dato se sigue leyendo; solo deja de ser una mención.
     */
    @Test void elAvisoNoDejaMencionarAlCanalConLoQueEscribioElUsuario() {
        plan("Negocio", "69", false, EstadoPlan.ACTIVO, true);

        service.registrar("<!channel> @everyone Bodega", "a@b.pe", "Segura123", "987654321", true, "Negocio", PORTAL);

        assertThat(avisos).singleElement().satisfies(a -> assertThat(a)
                .doesNotContain("<!channel>").doesNotContain("@everyone").doesNotContain("<").doesNotContain(">")
                .contains("everyone Bodega").contains("a\uFF20b.pe"));
    }

    @Test void elNombreSeEmparejaSinImportarMayusculasNiEspacios() {
        Plan pro = plan("Pro", "129", false, EstadoPlan.ACTIVO, true);

        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, "  PRO ", PORTAL);

        assertThat(anotados).containsEntry(cuentaDe(t), pro.id());
    }

    /**
     * Lo que no es un plan de pago que se ofrece hoy se ignora y la cuenta se crea igual: el gratis (con el que nace toda cuenta), uno que no se publica, uno
     * fuera de la oferta, uno que no existe, o nada. Un enlace viejo o retocado no impide registrarse ni molesta al operador.
     */
    @Test void loQueNoEsUnPlanDePagoPublicadoSeIgnora() {
        plan("Gratis", "0", true, EstadoPlan.ACTIVO, true);
        plan("Estudio", "49", false, EstadoPlan.ACTIVO, false);
        plan("Viejo", "19", false, EstadoPlan.INACTIVO, true);
        int n = 0;
        for (String pedido : Arrays.asList("gratis", "estudio", "viejo", "corporativo", "", "   ", null)) {
            Tokens t = service.registrar("A", "c" + (n++) + "@b.pe", "Segura123", "987654321", true, pedido, PORTAL);
            assertThat(t.usuario()).as("se registra igual con %s", pedido).isNotNull();
        }

        assertThat(anotados).isEmpty();
        assertThat(avisos).isEmpty();
    }

    /** Si el registro falla, no queda nada anotado ni se avisa: el aviso sale después de la transacción. */
    @Test void unRegistroQueFallaNoAvisa() {
        plan("Negocio", "69", false, EstadoPlan.ACTIVO, true);
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);

        assertThatThrownBy(() -> service.registrar("B", "a@b.pe", "Segura123", "987654321", true, "Negocio", PORTAL)).extracting("codigo").isEqualTo("DUPLICADO");
        assertThat(anotados).isEmpty();
        assertThat(avisos).isEmpty();
    }

    // --- #22: verificación del correo ---------------------------------------------------------------------------------------------

    @Test void elRegistroDejaElCorreoSinVerificarYMandaElEnlace() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);

        assertThat(t.usuario().correoVerificado()).isFalse();
        assertThat(correos).singleElement().satisfies(c -> assertThat(c).startsWith("a@b.pe|").contains(PORTAL + "/verificar/").contains("24 horas"));
        String token = tokenDelCorreo("/verificar/");
        assertThat(verifMap).as("solo el hash").containsOnlyKeys(TokenOpaco.hash(token));
        assertThat(verifMap.get(TokenOpaco.hash(token)).expiraEn()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
    }

    @Test void elEnlaceVerificaElCorreoUnaSolaVez() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        String token = tokenDelCorreo("/verificar/");

        service.verificarCorreo(token);

        assertThat(service.me(t.usuario().id()).correoVerificadoEn()).isEqualTo(clock.instant());
        assertThatThrownBy(() -> service.verificarCorreo(token)).extracting("codigo").isEqualTo("TOKEN_INVALIDO");
    }

    @Test void unEnlaceVencidoOInventadoNoVerifica() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        String token = tokenDelCorreo("/verificar/");
        AutenticarUsuarioService tarde = new AutenticarUsuarioService(cuentas, usuarios, sesiones, hasher, tokens, correo, Fakes.UOW,
                Clock.offset(clock, Duration.ofHours(24).plusSeconds(1)), verificaciones, suspensiones, new PlantillasDeCorreo(new ConfiguracionFake.Plantillas()), limite, terminos, planes, planesSolicitados, operador);

        assertThatThrownBy(() -> tarde.verificarCorreo(token)).extracting("codigo").isEqualTo("TOKEN_INVALIDO");
        assertThatThrownBy(() -> service.verificarCorreo("inventado")).extracting("codigo").isEqualTo("TOKEN_INVALIDO");
        assertThatThrownBy(() -> service.verificarCorreo(null)).extracting("codigo").isEqualTo("TOKEN_INVALIDO");
        assertThat(service.me(t.usuario().id()).correoVerificado()).isFalse();
    }

    @Test void unEnlaceDeVerificacionNoSirveParaCambiarLaContrasena() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        String token = tokenDelCorreo("/verificar/");
        assertThatThrownBy(() -> service.restablecer(token, "Nueva1234")).extracting("codigo").isEqualTo("TOKEN_INVALIDO");
    }

    @Test void reenviarMandaOtroEnlaceQueTambienVale() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        String primero = tokenDelCorreo("/verificar/");

        service.reenviarVerificacion(t.usuario().id(), PORTAL);

        String segundo = tokenDelCorreo("/verificar/");
        assertThat(segundo).isNotEqualTo(primero);
        assertThat(correos).hasSize(2);
        service.verificarCorreo(segundo);
        assertThat(service.me(t.usuario().id()).correoVerificado()).isTrue();
    }

    @Test void conElCorreoYaVerificadoNoSeReenvia() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        service.verificarCorreo(tokenDelCorreo("/verificar/"));

        assertThatThrownBy(() -> service.reenviarVerificacion(t.usuario().id(), PORTAL)).extracting("codigo").isEqualTo("CORREO_YA_VERIFICADO");
        assertThat(correos).hasSize(1);
    }

    /**
     * Sin verificar, el correo puede no ser de quien se registró: sin tope, el reenvío serviría para llenar el buzón de otro desde nuestro
     * dominio. Cinco enlaces por día contando el del registro; al vencer el primero, se puede pedir otro.
     */
    @Test void losEnlacesDeVerificacionTienenTopePorDia() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        UUID id = t.usuario().id();
        for (int i = 0; i < 4; i++) service.reenviarVerificacion(id, PORTAL);
        assertThat(correos).hasSize(5);

        assertThatThrownBy(() -> service.reenviarVerificacion(id, PORTAL)).extracting("codigo").isEqualTo("DEMASIADOS_ENLACES");
        assertThat(correos).as("no sale el sexto").hasSize(5);
        assertThat(verifMap).hasSize(5);

        AutenticarUsuarioService manana = new AutenticarUsuarioService(cuentas, usuarios, sesiones, hasher, tokens, correo, Fakes.UOW,
                Clock.offset(clock, Duration.ofHours(24).plusSeconds(1)), verificaciones, suspensiones, new PlantillasDeCorreo(plantillasGuardadas), limite, terminos, planes, planesSolicitados, operador);
        manana.reenviarVerificacion(id, PORTAL);
        assertThat(correos).hasSize(6);
    }

    /** Verificar solo marca el correo: una contraseña restablecida mientras tanto no vuelve a la anterior. */
    @Test void verificarNoPisaUnaContrasenaCambiadaMientrasTanto() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        String token = tokenDelCorreo("/verificar/");
        UUID id = t.usuario().id();
        // Un restablecer de la misma cuenta termina mientras se gasta el enlace, y otra vez justo después de cualquier lectura del usuario:
        // si verificar escribe lo que leyó, en el orden que sea, devuelve la contraseña anterior.
        // Cada restablecer deja una contraseña distinta: así se nota cualquier escritura de una lectura vieja.
        java.util.concurrent.atomic.AtomicInteger restablecidas = new java.util.concurrent.atomic.AtomicInteger();
        Runnable restablecer = () -> usuariosMap.computeIfPresent(id, (k, u) -> u.conPasswordHash(hasher.hash("Nueva" + restablecidas.incrementAndGet())));
        VerificacionCorreoRepository conCarrera = new VerificacionCorreoRepository() {
            public void crear(Token x) { verificaciones.crear(x); }
            public Optional<Token> buscar(String h) { return verificaciones.buscar(h); }
            public boolean usar(String h) { restablecer.run(); return verificaciones.usar(h); }
            public int contarSinVencer(UUID u, java.time.Instant ahora) { return verificaciones.contarSinVencer(u, ahora); }
        };
        UsuarioRepository leerConCarrera = new UsuarioRepository() {
            public void guardar(Usuario u) { usuarios.guardar(u); }
            public Optional<Usuario> buscar(UUID u) { Optional<Usuario> leido = usuarios.buscar(u); restablecer.run(); return leido; }
            public Optional<Usuario> buscarPorEmail(String e) { return usuarios.buscarPorEmail(e); }
            public void marcarCorreoVerificado(UUID u, java.time.Instant cuando) { usuarios.marcarCorreoVerificado(u, cuando); }
        };
        AutenticarUsuarioService enCarrera = new AutenticarUsuarioService(cuentas, leerConCarrera, sesiones, hasher, tokens, correo, Fakes.UOW, clock, conCarrera, suspensiones, new PlantillasDeCorreo(plantillasGuardadas), limite, terminos, planes, planesSolicitados, operador);

        enCarrera.verificarCorreo(token);

        assertThat(usuariosMap.get(id).correoVerificado()).isTrue();
        assertThat(restablecidas.get()).isPositive();
        assertThat(usuariosMap.get(id).passwordHash()).as("la última contraseña restablecida").isEqualTo(hasher.hash("Nueva" + restablecidas.get()));
    }

    /** Abrir el enlace de restablecer (o el de la invitación del alta asistida) también demuestra que el correo es suyo. */
    @Test void restablecerLaContrasenaTambienVerificaElCorreo() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        service.solicitarRecuperacion("a@b.pe", PORTAL);

        service.restablecer(tokenDelCorreo("/restablecer/"), "Nueva1234");

        assertThat(service.me(t.usuario().id()).correoVerificado()).isTrue();
    }

    /**
     * #21 (H2): restablecer lee al usuario antes de su transacción. Si un administrador lo desactiva y le cambia el rol en ese intervalo, la contraseña nueva no debe
     * reescribir el usuario leído (lo reactivaba y le devolvía el rol): solo cambia la contraseña.
     */
    @Test void restablecerNoDeshaceUnaDesactivacionHechaEntreLaLecturaYLaEscritura() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        UUID id = t.usuario().id();
        service.solicitarRecuperacion("a@b.pe", PORTAL);
        String token = tokenDelCorreo("/restablecer/");
        UsuarioRepository conCarrera = new UsuarioRepository() {
            public void guardar(Usuario u) { usuarios.guardar(u); }
            public Optional<Usuario> buscar(UUID u) {
                Optional<Usuario> leido = usuarios.buscar(u);
                // Lo que hace el administrador justo después de esta lectura.
                usuariosMap.computeIfPresent(u, (k, x) -> x.desactivar().conRol(Rol.LECTURA));
                return leido;
            }
            public Optional<Usuario> buscarPorEmail(String e) { return usuarios.buscarPorEmail(e); }
            public void marcarCorreoVerificado(UUID u, java.time.Instant cuando) { usuarios.marcarCorreoVerificado(u, cuando); }
            /** Como la base: un UPDATE de esas columnas sobre la fila de hoy. */
            public void cambiarPassword(UUID u, String hash, java.time.Instant cuando) {
                usuariosMap.computeIfPresent(u, (k, x) -> x.conPasswordHash(hash).conCorreoVerificado(cuando));
            }
        };
        AutenticarUsuarioService enCarrera = new AutenticarUsuarioService(cuentas, conCarrera, sesiones, hasher, tokens, correo, Fakes.UOW, clock, verificaciones, suspensiones,
                new PlantillasDeCorreo(plantillasGuardadas), limite, terminos, planes, planesSolicitados, operador);

        enCarrera.restablecer(token, "Nueva1234");

        assertThat(usuariosMap.get(id).activo()).as("sigue desactivado").isFalse();
        assertThat(usuariosMap.get(id).rol()).isEqualTo(Rol.LECTURA);
        assertThat(usuariosMap.get(id).passwordHash()).isEqualTo(hasher.hash("Nueva1234"));
    }

    /** Un SMTP caído no impide registrarse: el usuario pide otro enlace desde el portal. */
    @Test void siElCorreoFallaElRegistroQuedaHechoIgual() {
        CorreoSender roto = new CorreoSender() {
            public void enviar(String p, String a, String c) { throw new IllegalStateException("SMTP caído"); }
            public void enviar(String p, String a, String c, List<Adjunto> adj) { throw new IllegalStateException("SMTP caído"); }
        };
        AutenticarUsuarioService conRoto = new AutenticarUsuarioService(cuentas, usuarios, sesiones, hasher, tokens, roto, Fakes.UOW, clock, verificaciones, suspensiones, new PlantillasDeCorreo(new ConfiguracionFake.Plantillas()), limite, terminos, planes, planesSolicitados, operador);

        Tokens t = conRoto.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);

        assertThat(usuariosMap).containsKey(t.usuario().id());
        assertThat(verifMap).hasSize(1);
    }

    @Test void registroCreaCuentaUsuarioAdminYTokens() {
        Tokens t = service.registrar("Mi negocio", "Ana@Negocio.pe", "Segura123", "987654321", true, PORTAL);
        assertThat(cuentasMap).hasSize(1);
        assertThat(t.usuario().rol()).isEqualTo(Rol.ADMIN);
        assertThat(t.usuario().email()).isEqualTo("ana@negocio.pe");
        assertThat(t.access()).startsWith("jwt:" + t.usuario().id());
        assertThat(sesionesMap).containsKey(TokenOpaco.hash(t.refresh()));
        assertThat(cuentasMap.get(t.usuario().cuentaId()).telefono()).isEqualTo("987654321");
    }

    // --- #342: términos del servicio y política de privacidad ---------------------------------------------------------------------

    @Test void sinAceptarLosTerminosNoSeCreaNada() {
        assertThatThrownBy(() -> service.registrar("A", "a@b.pe", "Segura123", "987654321", false, PORTAL))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("TERMINOS_NO_ACEPTADOS");
        assertThat(cuentasMap).isEmpty();
        assertThat(usuariosMap).isEmpty();
        assertThat(aceptaciones.filas).isEmpty();
        assertThat(correos).as("ni el enlace de verificación").isEmpty();
    }

    @Test void elRegistroGuardaLaVersionVigenteYLaFecha() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);

        assertThat(aceptaciones.buscar(t.usuario().id(), TERMINOS)).get()
                .satisfies(a -> assertThat(a.aceptadaEn()).isEqualTo(clock.instant()));
        assertThat(t.terminosPendientes()).isFalse();
        assertThat(service.login("a@b.pe", "Segura123", null).terminosPendientes()).isFalse();
    }

    /** Un usuario de antes de #342 no aceptó nada: el login y el refresh lo dicen, hasta que acepte. */
    @Test void unUsuarioSinAceptacionQuedaPendienteHastaAceptar() {
        Cuenta cuenta = new Cuenta(UUID.randomUUID(), "Antigua", "vieja@b.pe", "987654321");
        cuentas.guardar(cuenta);
        Usuario viejo = new Usuario(UUID.randomUUID(), cuenta.id(), "vieja@b.pe", hasher.hash("Segura123"), Rol.ADMIN, true);
        usuarios.guardar(viejo);

        Tokens t = service.login("vieja@b.pe", "Segura123", null);
        assertThat(t.terminosPendientes()).isTrue();
        assertThat(service.refrescar(t.refresh()).terminosPendientes()).isTrue();

        terminos.aceptar(viejo.id());

        assertThat(service.login("vieja@b.pe", "Segura123", null).terminosPendientes()).isFalse();
    }

    /** Subir la versión deja pendientes a todos los que aceptaron la anterior. */
    @Test void unaVersionNuevaVuelveAPedirLaAceptacion() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        AceptarTerminosService nuevos = new AceptarTerminosService(aceptaciones, Fakes.UOW, clock, "2027-01-15");
        AutenticarUsuarioService conNuevos = new AutenticarUsuarioService(cuentas, usuarios, sesiones, hasher, tokens, correo, Fakes.UOW, clock,
                verificaciones, suspensiones, new PlantillasDeCorreo(plantillasGuardadas), limite, nuevos, planes, planesSolicitados, operador);

        assertThat(conNuevos.login("a@b.pe", "Segura123", null).terminosPendientes()).isTrue();
    }

    /** Celular de contacto en Perú (#onboarding): 9 dígitos que empiezan con 9; admite +51/51 y espacios, se normaliza sin ellos. */
    @Test void telefonoDeContactoSeNormalizaYSeValida() {
        Tokens t = service.registrar("Con prefijo", "prefijo@b.pe", "Segura123", "+51 987 654 321", true, PORTAL);
        assertThat(cuentasMap.get(t.usuario().cuentaId()).telefono()).isEqualTo("987654321");
        assertThatThrownBy(() -> service.registrar("Fijo", "fijo@b.pe", "Segura123", "123456789", true, PORTAL))
                .isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("TELEFONO_INVALIDO");
        assertThatThrownBy(() -> service.registrar("Corto", "corto@b.pe", "Segura123", "98765432", true, PORTAL))
                .extracting("codigo").isEqualTo("TELEFONO_INVALIDO");
    }

    @Test void registroDuplicadoYPasswordDebil() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        assertThatThrownBy(() -> service.registrar("B", "A@B.PE", "Segura123", "987654321", true, PORTAL)).extracting("codigo").isEqualTo("DUPLICADO");
        assertThatThrownBy(() -> service.registrar("C", "c@d.pe", "corta", "987654321", true, PORTAL)).extracting("codigo").isEqualTo("PASSWORD_DEBIL");
    }

    @Test void loginCorrectoEIncorrecto() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        assertThat(service.login("A@B.PE", "Segura123", null).access()).isNotBlank();
        assertThatThrownBy(() -> service.login("a@b.pe", "otra", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.login("nadie@b.pe", "Segura123", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
    }

    // --- límite de intentos (#261) ------------------------------------------------------------------------------------------------

    @Test void trasCincoFallosNiLaContrasenaCorrectaEntraYNoSeAbreSesion() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.login("a@b.pe", "otra", "203.0.113.9")).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        int sesionesAntes = sesionesMap.size();

        assertThatThrownBy(() -> service.login("a@b.pe", "Segura123", "203.0.113.9")).extracting("codigo").isEqualTo("DEMASIADOS_INTENTOS_LOGIN");
        assertThat(sesionesMap).hasSize(sesionesAntes);
    }

    /**
     * H1 (revisión de la PR #262): el contador y la búsqueda de la cuenta tienen que ver el mismo correo. Con un carácter de control delante, `trim()` lo quitaba
     * para buscar la cuenta y `strip()` lo dejaba en la clave del contador: cada variante abría un contador nuevo y la contraseña se podía probar sin límite.
     */
    @Test void unCaracterDeControlDelanteDelCorreoNoAbreOtroContador() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.login("a@b.pe", "otra", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");

        for (String disfrazado : new String[]{"\u0001a@b.pe", "\u0002A@B.PE ", "\u0000\u001Fa@b.pe"})
            assertThatThrownBy(() -> service.login(disfrazado, "Segura123", null)).extracting("codigo").isEqualTo("DEMASIADOS_INTENTOS_LOGIN");
    }

    @Test void laRecuperacionNoSeMultiplicaConCaracteresDeControl() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        correos.clear();
        for (String variante : new String[]{"a@b.pe", "\u0001a@b.pe", "\u0002a@b.pe", "\u0003a@b.pe", "\u0004a@b.pe"}) service.solicitarRecuperacion(variante, PORTAL);
        assertThat(correos).hasSize(3);
    }

    /** El bloqueo de un correo que no existe se ve igual: la respuesta no dice qué cuentas hay. */
    @Test void unCorreoQueNoExisteTambienSeBloquea() {
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.login("nadie@b.pe", "otra", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.login("nadie@b.pe", "otra", null)).extracting("codigo").isEqualTo("DEMASIADOS_INTENTOS_LOGIN");
    }

    @Test void unLoginCorrectoReiniciaLosFallosDelCorreo() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        for (int i = 0; i < 4; i++) assertThatThrownBy(() -> service.login("a@b.pe", "otra", null)).isInstanceOf(DomainException.class);
        service.login("a@b.pe", "Segura123", null);
        for (int i = 0; i < 4; i++) assertThatThrownBy(() -> service.login("a@b.pe", "otra", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThat(service.login("a@b.pe", "Segura123", null).access()).isNotBlank();
    }

    /** Pasado el tope no se manda nada, pero quien pide no lo nota: la respuesta es la misma, exista o no la cuenta. */
    @Test void laRecuperacionMandaComoMuchoTresCorreosPorHora() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        correos.clear();
        for (int i = 0; i < 5; i++) assertThatCode(() -> service.solicitarRecuperacion("a@b.pe", PORTAL)).doesNotThrowAnyException();
        assertThat(correos).hasSize(3);
    }

    private AutenticarUsuarioService conReloj(Clock c) {
        return new AutenticarUsuarioService(cuentas, usuarios, sesiones, hasher, tokens, correo, Fakes.UOW, c, verificaciones, suspensiones, new PlantillasDeCorreo(plantillasGuardadas), limite, terminos, planes, planesSolicitados, operador);
    }

    @Test void refreshRotaYElAnteriorDejaDeServir() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens t2 = service.refrescar(t1.refresh());
        assertThat(t2.refresh()).isNotEqualTo(t1.refresh());
        AutenticarUsuarioService pasadaLaGracia = conReloj(Clock.offset(clock, AutenticarUsuarioService.GRACIA_ROTACION.plusSeconds(1)));
        assertThatThrownBy(() -> pasadaLaGracia.refrescar(t1.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
        service.logout(t2.refresh());
        assertThatThrownBy(() -> service.refrescar(t2.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    // --- #635: un refresh ya usado, presentado fuera de la gracia, revoca la familia ---------------------------------------------------

    private AutenticarUsuarioService pasados(Duration segundos) { return conReloj(Clock.offset(clock, segundos)); }

    private boolean viva(Tokens t) { return !sesionesMap.get(TokenOpaco.hash(t.refresh())).revocada(); }

    /** El escenario del issue: A→B, el atacante rota B→C con el token robado, la víctima presenta B pasada la gracia: C también muere. */
    @Test void reusarUnRefreshRotadoFueraDeLaGraciaRevocaLaFamiliaEntera() {
        Tokens a = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens b = service.refrescar(a.refresh());
        Tokens c = pasados(GRACIA_MAS_UNO).refrescar(b.refresh());   // el atacante, con B
        assertThat(viva(c)).isTrue();

        // La víctima presenta B 31 s después de que el atacante lo rotó.
        assertThatThrownBy(() -> pasados(GRACIA_MAS_UNO.multipliedBy(2)).refrescar(b.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");

        assertThat(viva(c)).as("la rama del atacante").isFalse();
        assertThatThrownBy(() -> pasados(GRACIA_MAS_UNO.multipliedBy(2)).refrescar(c.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    @Test void elReusoNoTocaOtraFamiliaDelMismoUsuario() {
        Tokens a = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens celular = service.login("a@b.pe", "Segura123", null);
        service.refrescar(a.refresh());

        assertThatThrownBy(() -> pasados(GRACIA_MAS_UNO).refrescar(a.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");

        assertThat(viva(celular)).as("otro login, otra familia").isTrue();
    }

    /** Dentro de la gracia no es un reuso: es la segunda petición del mismo navegador y no revoca nada. */
    @Test void dentroDeLaGraciaNoRevocaNada() {
        Tokens a = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens b = service.refrescar(a.refresh());

        Tokens rama = pasados(Duration.ofSeconds(5)).refrescar(a.refresh());

        assertThat(viva(b)).isTrue();
        assertThat(viva(rama)).isTrue();
    }

    /** Un refresh cerrado por logout, restablecer o desactivar no tiene rotación: la familia ya estaba cerrada y el rechazo es el de siempre. */
    @Test void unRefreshRevocadoSinRotacionSoloSeRechaza() {
        Tokens a = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens otroLogin = service.login("a@b.pe", "Segura123", null);
        service.logout(a.refresh());

        assertThatThrownBy(() -> pasados(GRACIA_MAS_UNO).refrescar(a.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");

        assertThat(viva(otroLogin)).isTrue();
    }

    /** Un refresh vencido es un rechazo sin más: no revoca la familia, que por la misma razón ya no tiene nada vivo que cuidar. */
    @Test void unRefreshVencidoNoRevocaLaFamilia() {
        Tokens a = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens b = service.refrescar(a.refresh());
        sesionesMap.put(TokenOpaco.hash(a.refresh()), vencida(sesionesMap.get(TokenOpaco.hash(a.refresh()))));

        assertThatThrownBy(() -> service.refrescar(a.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");

        assertThat(viva(b)).isTrue();
    }

    private SesionRepository.Sesion vencida(SesionRepository.Sesion s) {
        return new SesionRepository.Sesion(s.id(), s.usuarioId(), s.refreshHash(), clock.instant().minusSeconds(1), s.revocada(), s.rotadaEn(), s.reemplazadaPor(), s.familia());
    }

    /** Un refresh que no existe (o ya se purgó) es un rechazo sin revocar nada. */
    @Test void unRefreshDesconocidoNoRevocaNada() {
        Tokens a = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);

        assertThatThrownBy(() -> service.refrescar("no-existe")).extracting("codigo").isEqualTo("SESION_INVALIDA");

        assertThat(viva(a)).isTrue();
    }

    /**
     * La carrera que cierra {@code rotar} condicional: el logout (o el reuso) confirma entre la lectura de la sesión y su rotación. Sin la condición el
     * refresh seguía y emitía una sesión nueva viva después del logout; con ella la rotación falla y el refresh se rechaza.
     */
    @Test void unLogoutQueLlegaEntreLaLecturaYLaRotacionRechazaElRefresh() {
        Tokens a = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        despuesDeLeer = () -> service.logout(a.refresh());

        assertThatThrownBy(() -> service.refrescar(a.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
        assertThat(sesionesMap.get(TokenOpaco.hash(a.refresh())).revocada()).isTrue();
    }

    /**
     * #635 + #611: con la fila raíz ya purgada, presentar una rama rotada fuera de la gracia revoca igual toda la familia, incluida la otra rama que abrió la
     * gracia. Depende de que la familia se revoque por su id y no recorriendo la cadena desde la raíz.
     */
    @Test void conLaFilaRaizPurgadaElReusoRevocaLaFamiliaCompletaIncluidaLaOtraRama() {
        Tokens raiz = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens ramaA = service.refrescar(raiz.refresh());
        Tokens ramaB = conReloj(Clock.offset(clock, Duration.ofSeconds(5))).refrescar(raiz.refresh());   // en gracia: abre la otra rama
        Tokens c = service.refrescar(ramaA.refresh());
        sesionesMap.remove(TokenOpaco.hash(raiz.refresh()));   // la limpieza de #611 ya borró la raíz

        assertThatThrownBy(() -> pasados(GRACIA_MAS_UNO).refrescar(ramaA.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");

        assertThat(viva(ramaB)).as("la otra rama").isFalse();
        assertThat(viva(c)).as("la rama que sigue a la reutilizada").isFalse();
    }

    private static final Duration GRACIA_MAS_UNO = AutenticarUsuarioService.GRACIA_ROTACION.plusSeconds(1);

    /**
     * #611: la limpieza borra las filas vencidas, así que un refresh rotado que vuelve cuando su fila ya no existe recibe el mismo rechazo que con la fila
     * presente, y el logout cierra la familia aunque falte la fila del login (se busca por la familia, que heredan las rotaciones).
     */
    @Test void conLaFilaPurgadaElRefreshRotadoDaElMismoRechazoYElLogoutCierraLaFamilia() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens t2 = service.refrescar(t1.refresh());
        AutenticarUsuarioService pasadaLaGracia = conReloj(Clock.offset(clock, AutenticarUsuarioService.GRACIA_ROTACION.plusSeconds(1)));
        assertThatThrownBy(() -> pasadaLaGracia.refrescar(t1.refresh())).as("con la fila").extracting("codigo").isEqualTo("SESION_INVALIDA");

        sesionesMap.remove(TokenOpaco.hash(t1.refresh()));

        assertThatThrownBy(() -> pasadaLaGracia.refrescar(t1.refresh())).as("sin la fila").extracting("codigo").isEqualTo("SESION_INVALIDA");
        service.logout(t2.refresh());
        assertThatThrownBy(() -> service.refrescar(t2.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    /** #611: con la fila del login borrada, el logout con una rama sigue cerrando la otra rama de la gracia (la familia se busca por su id, no por la fila raíz). */
    @Test void conLaFilaDelLoginPurgadaElLogoutCierraLaOtraRama() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens ramaA = service.refrescar(t1.refresh());
        Tokens ramaB = conReloj(Clock.offset(clock, Duration.ofSeconds(5))).refrescar(t1.refresh());
        sesionesMap.remove(TokenOpaco.hash(t1.refresh()));

        service.logout(ramaB.refresh());

        assertThatThrownBy(() -> service.refrescar(ramaA.refresh())).as("la otra rama").extracting("codigo").isEqualTo("SESION_INVALIDA");
        assertThatThrownBy(() -> service.refrescar(ramaB.refresh())).as("la que cerró sesión").extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    // --- S6: dos instancias del portal refrescan la misma sesión casi a la vez ---------------------------------------------------

    /**
     * Cada instancia del portal tiene su propio single-flight: si dos peticiones del mismo navegador caen en instancias distintas, ambas
     * mandan el mismo refresh. La que llega segunda no debe cerrar la sesión del usuario.
     */
    @Test void elRefreshRecienRotadoSirveParaLaPeticionQueLlegaJustoDespues() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens primera = service.refrescar(t1.refresh());

        Tokens segunda = conReloj(Clock.offset(clock, Duration.ofSeconds(5))).refrescar(t1.refresh());

        assertThat(segunda.access()).isNotBlank();
        assertThat(segunda.refresh()).isNotEqualTo(primera.refresh()).isNotEqualTo(t1.refresh());
        assertThat(service.refrescar(primera.refresh()).access()).as("la de la primera sigue viva").isNotBlank();
    }

    /** Si el reemplazo también lo rotó otro refresh (no un logout), la sesión sigue viva y la gracia vale. */
    @Test void laGraciaValeAunqueElReemplazoTambienSeHayaRotado() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        service.refrescar(service.refrescar(t1.refresh()).refresh());

        assertThat(service.refrescar(t1.refresh()).access()).isNotBlank();
    }

    /** Cerrar sesión justo después de un refresh no deja el refresh anterior abierto durante la gracia. */
    @Test void cerrarSesionAnulaLaGraciaDelRefreshAnterior() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens t2 = service.refrescar(t1.refresh());

        service.logout(t2.refresh());

        assertThatThrownBy(() -> service.refrescar(t1.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    /**
     * 271-H1: con dos rotaciones seguidas, cerrar sesión desde la última tampoco deja abierto el refresh del principio. Antes la gracia solo miraba al
     * reemplazo directo (rotado, no revocado por logout) y el refresh original creaba una sesión de 30 días después del logout.
     */
    @Test void cerrarSesionTrasDosRotacionesAnulaLaGraciaDeTodaLaCadena() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens t2 = service.refrescar(t1.refresh());
        Tokens t3 = service.refrescar(t2.refresh());

        service.logout(t3.refresh());

        assertThatThrownBy(() -> service.refrescar(t1.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
        assertThatThrownBy(() -> service.refrescar(t2.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    /**
     * 271-H1, el caso real de S6: dos instancias rotan el mismo refresh (una en gracia) y el navegador se queda con una de las dos. Cerrar sesión con esa
     * cierra también la otra, que si no quedaba viva 30 días, y la gracia del refresh original.
     */
    @Test void cerrarSesionCierraTambienLaRamaQueAbrioLaGracia() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens instanciaA = service.refrescar(t1.refresh());
        Tokens instanciaB = conReloj(Clock.offset(clock, Duration.ofSeconds(5))).refrescar(t1.refresh());

        service.logout(instanciaB.refresh());

        assertThatThrownBy(() -> service.refrescar(instanciaA.refresh())).as("la otra rama").extracting("codigo").isEqualTo("SESION_INVALIDA");
        assertThatThrownBy(() -> service.refrescar(t1.refresh())).as("el refresh original").extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    /** Cerrar sesión cierra lo de ese navegador, no las demás sesiones del usuario (otro dispositivo es otro login). */
    @Test void cerrarSesionNoCierraOtroLoginDelMismoUsuario() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Tokens laptop = service.login("a@b.pe", "Segura123", null);
        Tokens celular = service.login("a@b.pe", "Segura123", null);

        service.logout(laptop.refresh());

        assertThat(service.refrescar(celular.refresh()).access()).isNotBlank();
    }

    /** Restablecer la contraseña revoca todo: tampoco queda gracia para un refresh recién rotado. */
    @Test void restablecerLaContrasenaAnulaLaGracia() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        service.refrescar(t1.refresh());
        service.solicitarRecuperacion("a@b.pe", PORTAL);

        service.restablecer(tokenDelCorreo("/restablecer/"), "Nueva1234");

        assertThatThrownBy(() -> service.refrescar(t1.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    /** Un refresh cerrado con logout nunca tuvo gracia: solo la rotación la da. */
    @Test void unRefreshCerradoConLogoutNoTieneGracia() {
        Tokens t1 = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        service.logout(t1.refresh());
        assertThatThrownBy(() -> service.refrescar(t1.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    @Test void refreshExpiradoFalla() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        AutenticarUsuarioService tarde = new AutenticarUsuarioService(cuentas, usuarios, sesiones, hasher, tokens, correo, Fakes.UOW,
                Clock.offset(clock, Duration.ofDays(31)), verificaciones, suspensiones, new PlantillasDeCorreo(new ConfiguracionFake.Plantillas()), limite, terminos, planes, planesSolicitados, operador);
        assertThatThrownBy(() -> tarde.refrescar(t.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");
    }

    // --- cuenta suspendida (#182) -----------------------------------------------------------------------------------------------

    @Test void unaCuentaSuspendidaNoInicia() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        cuentasSuspendidas.add(t.usuario().cuentaId());
        int sesionesAntes = sesionesMap.size();

        assertThatThrownBy(() -> service.login("a@b.pe", "Segura123", null)).extracting("codigo").isEqualTo("CUENTA_SUSPENDIDA");
        assertThat(sesionesMap).as("no se abre ninguna sesión").hasSize(sesionesAntes);
    }

    /** Quien no acierta la contraseña no se entera de si la cuenta existe ni de si está suspendida. */
    @Test void conLaContrasenaErroneaNoSeRevelaQueLaCuentaEstaSuspendida() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        cuentasSuspendidas.add(t.usuario().cuentaId());

        assertThatThrownBy(() -> service.login("a@b.pe", "otra", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test void unaCuentaSuspendidaNoRefrescaSuSesionPeroLaSesionNoSeRevoca() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        cuentasSuspendidas.add(t.usuario().cuentaId());

        assertThatThrownBy(() -> service.refrescar(t.refresh())).extracting("codigo").isEqualTo("CUENTA_SUSPENDIDA");

        // Reactivar lo devuelve todo a como estaba: la misma sesión vuelve a servir.
        cuentasSuspendidas.clear();
        assertThat(service.refrescar(t.refresh()).access()).isNotBlank();
    }

    @Test void reactivadaLaCuentaVuelveAIniciar() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        cuentasSuspendidas.add(t.usuario().cuentaId());
        cuentasSuspendidas.clear();

        assertThat(service.login("a@b.pe", "Segura123", null).access()).isNotBlank();
    }

    @Test void suspenderUnaCuentaNoAfectaALasDemas() {
        Tokens a = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        service.registrar("B", "b@b.pe", "Segura123", "987654321", true, PORTAL);
        cuentasSuspendidas.add(a.usuario().cuentaId());

        assertThat(service.login("b@b.pe", "Segura123", null).access()).isNotBlank();
    }

    // --- #199: el texto de los correos de acceso lo edita un administrador ----------------------------------------------------------

    @Test void elCorreoDeVerificacionSaleConElTextoQueUnAdministradorEdito() {
        plantillasGuardadas.filas.put(PlantillaDeCorreo.VERIFICACION_CORREO, new PlantillasRepository.Guardada(new PlantillaDeCorreo.Texto("Confirma", "Confirma en {enlace} antes de {validez}"), java.time.Instant.EPOCH));

        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);

        assertThat(correos).singleElement().satisfies(c -> assertThat(c).startsWith("a@b.pe|Confirma en " + PORTAL + "/verificar/").endsWith(" antes de 24 horas"));
    }

    @Test void elCorreoDeRecuperacionSaleConElTextoQueUnAdministradorEdito() {
        service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        correos.clear();
        plantillasGuardadas.filas.put(PlantillaDeCorreo.RECUPERACION_CLAVE, new PlantillasRepository.Guardada(new PlantillaDeCorreo.Texto("Nueva clave", "Elige otra clave en {enlace} (dura {validez})"), java.time.Instant.EPOCH));

        service.solicitarRecuperacion("a@b.pe", PORTAL);

        assertThat(correos).singleElement().satisfies(c -> assertThat(c).startsWith("a@b.pe|Elige otra clave en " + PORTAL + "/restablecer/").endsWith(" (dura 1 hora)"));
    }

    @Test void recuperacionEnviaCorreoYRestablece() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        correos.clear();   // el de verificación del registro (#22)
        service.solicitarRecuperacion("nadie@b.pe", "https://portal");   // silencioso
        assertThat(correos).isEmpty();
        service.solicitarRecuperacion("a@b.pe", "https://portal");
        assertThat(correos).hasSize(1);
        String token = correos.get(0).substring(correos.get(0).lastIndexOf("/restablecer/") + "/restablecer/".length());
        service.restablecer(token, "Nueva1234");
        assertThat(service.login("a@b.pe", "Nueva1234", null).access()).isNotBlank();
        assertThatThrownBy(() -> service.login("a@b.pe", "Segura123", null)).extracting("codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        assertThatThrownBy(() -> service.refrescar(t.refresh())).extracting("codigo").isEqualTo("SESION_INVALIDA");   // sesiones revocadas
        assertThatThrownBy(() -> service.restablecer(token, "Otra12345")).extracting("codigo").isEqualTo("TOKEN_INVALIDO"); // un solo uso
    }

    @Test void empresasDeLaCuenta() {
        Tokens t = service.registrar("A", "a@b.pe", "Segura123", "987654321", true, PORTAL);
        Fakes.Tenants tenants = new Fakes.Tenants();
        GestionarEmpresasService empresas = new GestionarEmpresasService(tenants, cuentas, Fakes.UOW, (cuenta, recurso) -> {});
        Tenant e = empresas.crear(t.usuario().cuentaId(), "20100066603", "EMPRESA SAC", Entorno.BETA);
        assertThat(empresas.listar(t.usuario().cuentaId())).extracting(Tenant::id).containsExactly(e.id());
        assertThatCode(() -> empresas.exigirPertenencia(t.usuario().cuentaId(), e.id())).doesNotThrowAnyException();
        assertThatThrownBy(() -> empresas.exigirPertenencia(UUID.randomUUID(), e.id())).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo("EMPRESA_AJENA");
        assertThatThrownBy(() -> empresas.crear(t.usuario().cuentaId(), "20100066603", "OTRA", Entorno.BETA)).extracting("codigo").isEqualTo("DUPLICADO");
    }
}
