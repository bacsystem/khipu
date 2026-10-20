package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AceptarInvitacionUseCase;
import pe.factura.application.port.in.GestionarEquipoUseCase.Enviada;
import pe.factura.application.port.out.*;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Equipo;
import pe.factura.domain.cuenta.Invitacion;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #21: el equipo de una cuenta y sus invitaciones. */
class EquipoServiceTest {
    static final String PORTAL = "https://portal";
    Instant ahora = Instant.parse("2026-10-20T12:00:00Z");
    Clock clock = Clock.fixed(ahora, ZoneId.of("America/Lima"));

    Map<UUID, Cuenta> cuentasMap = new HashMap<>();
    CuentaRepository cuentas = new CuentaRepository() {
        public void guardar(Cuenta c) { cuentasMap.put(c.id(), c); }
        public Optional<Cuenta> buscar(UUID id) { return Optional.ofNullable(cuentasMap.get(id)); }
        public Optional<Cuenta> buscarPorEmail(String e) { return cuentasMap.values().stream().filter(c -> c.email().equals(e)).findFirst(); }
    };
    Map<UUID, Usuario> usuariosMap = new LinkedHashMap<>();
    UsuarioRepository usuarios = new UsuarioRepository() {
        public void guardar(Usuario u) { usuariosMap.put(u.id(), u); }
        public Optional<Usuario> buscar(UUID id) { return Optional.ofNullable(usuariosMap.get(id)); }
        public Optional<Usuario> buscarPorEmail(String e) { return usuariosMap.values().stream().filter(u -> u.email().equals(e)).findFirst(); }
        public void marcarCorreoVerificado(UUID id, Instant cuando) {}
    };
    int bloqueos = 0;
    EquipoRepository equipos = new EquipoRepository() {
        public Equipo deLaCuenta(UUID c) { return new Equipo(c, usuariosMap.values().stream().filter(u -> u.cuentaId().equals(c)).toList()); }
        public Equipo deLaCuentaParaCambiar(UUID c) { bloqueos++; return deLaCuenta(c); }
    };
    Map<UUID, Invitacion> invitacionesMap = new LinkedHashMap<>();
    InvitacionRepository invitaciones = new InvitacionRepository() {
        public void crear(Invitacion i) { invitacionesMap.put(i.id(), i); }
        public boolean actualizarPendiente(Invitacion i) {
            Invitacion actual = invitacionesMap.get(i.id());
            if (actual == null || !actual.pendiente()) return false;
            invitacionesMap.put(i.id(), i);
            return true;
        }
        public Optional<Invitacion> buscar(UUID id) { return Optional.ofNullable(invitacionesMap.get(id)); }
        public Optional<Invitacion> buscarPorTokenHash(String h) { return invitacionesMap.values().stream().filter(i -> i.tokenHash().equals(h)).findFirst(); }
        public List<Invitacion> pendientes(UUID c) { return invitacionesMap.values().stream().filter(i -> i.cuentaId().equals(c) && i.pendiente()).toList(); }
        public boolean aceptar(UUID id, String h, Instant en) {
            Invitacion i = invitacionesMap.get(id);
            if (i == null || !i.tokenHash().equals(h) || !i.aceptable(en)) return false;
            invitacionesMap.put(id, new Invitacion(i.id(), i.cuentaId(), i.email(), i.rol(), i.tokenHash(), i.expiraEn(), Invitacion.Estado.ACEPTADA, i.invitadoPor(), i.envios(), i.creadaEn()));
            return true;
        }
    };
    List<UUID> sesionesRevocadas = new ArrayList<>();
    SesionRepository sesiones = new SesionRepository() {
        public void crear(Sesion s) {}
        public Optional<Sesion> buscarPorRefreshHash(String h) { return Optional.empty(); }
        public Optional<Sesion> buscar(UUID id) { return Optional.empty(); }
        public void revocar(UUID id) {}
        public void revocarFamilia(UUID f) {}
        public void revocarTodas(UUID u) { sesionesRevocadas.add(u); }
        public void rotar(UUID id, UUID nueva, Instant en) {}
        public void crearRecuperacion(TokenRecuperacion t) {}
        public Optional<TokenRecuperacion> buscarRecuperacion(String h) { return Optional.empty(); }
        public void marcarRecuperacionUsada(String h) {}
    };
    PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "H(" + p + ")"; }
        public boolean coincide(String p, String h) { return h.equals("H(" + p + ")"); }
    };
    List<String> correos = new ArrayList<>();
    boolean correoCaido = false;
    CorreoSender correo = new CorreoSender() {
        public void enviar(String para, String asunto, String cuerpo) {
            if (correoCaido) throw new IllegalStateException("SMTP caído");
            correos.add(para + "|" + asunto + "|" + cuerpo);
        }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpo); }
    };

    EquipoService service = servicio(clock);

    EquipoService servicio(Clock c) {
        return new EquipoService(equipos, usuarios, cuentas, invitaciones, sesiones, hasher, correo, new PlantillasDeCorreo(new ConfiguracionFake.Plantillas()), Fakes.UOW, c);
    }

    final Cuenta cuenta = new Cuenta(UUID.randomUUID(), "Ferretería Torres", "ana@torres.pe");
    final Usuario ana = new Usuario(UUID.randomUUID(), cuenta.id(), "ana@torres.pe", "hash", Rol.ADMIN, true, ahora);
    final Usuario beto = new Usuario(UUID.randomUUID(), cuenta.id(), "beto@torres.pe", "hash", Rol.EMISOR, true, ahora);

    {
        cuentas.guardar(cuenta);
        usuarios.guardar(ana);
        usuarios.guardar(beto);
    }

    private static void falla(Runnable r, String codigo) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class).extracting("codigo").isEqualTo(codigo);
    }

    /** El token del último enlace de invitación que llegó a {@code email}. */
    private String tokenDelCorreo(String email) {
        String c = correos.stream().filter(x -> x.startsWith(email + "|")).reduce((a, b) -> b).orElseThrow();
        Matcher m = Pattern.compile(PORTAL + "/invitacion/([A-Za-z0-9_-]+)").matcher(c);
        assertThat(m.find()).as(c).isTrue();
        return m.group(1);
    }

    // --- Usuarios -------------------------------------------------------------------------------------------------------------------

    @Test void elAdministradorVeASuEquipoYNadieMas() {
        assertThat(service.miembros(cuenta.id(), ana.id())).extracting(Usuario::email).containsExactly("ana@torres.pe", "beto@torres.pe");
        falla(() -> service.miembros(cuenta.id(), beto.id()), "ROL_INSUFICIENTE");
    }

    @Test void cambiarElRolSeGuardaConElEquipoBloqueado() {
        Usuario cambiado = service.cambiarRol(cuenta.id(), ana.id(), beto.id(), Rol.LECTURA);
        assertThat(cambiado.rol()).isEqualTo(Rol.LECTURA);
        assertThat(usuariosMap.get(beto.id()).rol()).isEqualTo(Rol.LECTURA);
        assertThat(bloqueos).isEqualTo(1);
    }

    @Test void elUltimoAdministradorNoSeQuitaElRol() {
        falla(() -> service.cambiarRol(cuenta.id(), ana.id(), ana.id(), Rol.EMISOR), "ULTIMO_ADMINISTRADOR");
        assertThat(usuariosMap.get(ana.id()).rol()).isEqualTo(Rol.ADMIN);
    }

    @Test void desactivarCierraSusSesiones() {
        Usuario d = service.desactivar(cuenta.id(), ana.id(), beto.id());
        assertThat(d.activo()).isFalse();
        assertThat(usuariosMap.get(beto.id()).activo()).isFalse();
        assertThat(sesionesRevocadas).containsExactly(beto.id());

        assertThat(service.reactivar(cuenta.id(), ana.id(), beto.id()).activo()).isTrue();
        assertThat(usuariosMap.get(beto.id()).activo()).isTrue();
    }

    @Test void unEmisorNoDesactivaNiCambiaRoles() {
        falla(() -> service.desactivar(cuenta.id(), beto.id(), ana.id()), "ROL_INSUFICIENTE");
        falla(() -> service.cambiarRol(cuenta.id(), beto.id(), beto.id(), Rol.ADMIN), "ROL_INSUFICIENTE");
        assertThat(usuariosMap.get(ana.id()).activo()).isTrue();
        assertThat(usuariosMap.get(beto.id()).rol()).isEqualTo(Rol.EMISOR);
        assertThat(sesionesRevocadas).isEmpty();
    }

    /** Un administrador de otra cuenta no toca a los usuarios de esta, aunque conozca sus ids. */
    @Test void unAdministradorDeOtraCuentaNoGestionaEsteEquipo() {
        Cuenta otra = new Cuenta(UUID.randomUUID(), "Otra", "zoe@otra.pe");
        cuentas.guardar(otra);
        Usuario zoe = new Usuario(UUID.randomUUID(), otra.id(), "zoe@otra.pe", "hash", Rol.ADMIN, true, ahora);
        usuarios.guardar(zoe);

        falla(() -> service.desactivar(cuenta.id(), zoe.id(), beto.id()), "ROL_INSUFICIENTE");
        falla(() -> service.desactivar(otra.id(), zoe.id(), beto.id()), "NO_ENCONTRADO");
        assertThat(usuariosMap.get(beto.id()).activo()).isTrue();
    }

    // --- Invitar --------------------------------------------------------------------------------------------------------------------

    @Test void invitarGuardaSoloElHashYMandaElEnlaceConLaCuentaYElRol() {
        Enviada e = service.invitar(cuenta.id(), ana.id(), " Caro@Torres.pe ", Rol.LECTURA, PORTAL);

        assertThat(e.correoEnviado()).isTrue();
        Invitacion i = e.invitacion();
        assertThat(i.email()).isEqualTo("caro@torres.pe");
        assertThat(i.rol()).isEqualTo(Rol.LECTURA);
        assertThat(i.invitadoPor()).isEqualTo(ana.id());
        assertThat(i.expiraEn()).isEqualTo(ahora.plus(Duration.ofDays(7)));
        String token = tokenDelCorreo("caro@torres.pe");
        assertThat(i.tokenHash()).isEqualTo(TokenOpaco.hash(token)).isNotEqualTo(token);
        assertThat(correos.get(0)).contains("Ferretería Torres").contains("Solo lectura").contains("7 días");
        assertThat(invitacionesMap).containsKey(i.id());
    }

    @Test void soloUnAdministradorInvita() {
        falla(() -> service.invitar(cuenta.id(), beto.id(), "caro@torres.pe", Rol.EMISOR, PORTAL), "ROL_INSUFICIENTE");
        assertThat(invitacionesMap).isEmpty();
        assertThat(correos).isEmpty();
    }

    @Test void noSeInvitaAUnMiembroNiAUnCorreoDeOtraCuenta() {
        falla(() -> service.invitar(cuenta.id(), ana.id(), "BETO@torres.pe", Rol.EMISOR, PORTAL), "YA_ES_MIEMBRO");
        usuarios.guardar(new Usuario(UUID.randomUUID(), UUID.randomUUID(), "zoe@otra.pe", "hash", Rol.ADMIN, true));
        falla(() -> service.invitar(cuenta.id(), ana.id(), "zoe@otra.pe", Rol.EMISOR, PORTAL), "USUARIO_EXISTENTE");
        assertThat(invitacionesMap).isEmpty();
    }

    @Test void unaSolaInvitacionPendientePorCorreo() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        falla(() -> service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.LECTURA, PORTAL), "INVITACION_PENDIENTE");
        assertThat(invitacionesMap).hasSize(1);
    }

    @Test void tieneTopeDeInvitacionesPendientes() {
        for (int i = 0; i < EquipoService.MAX_PENDIENTES; i++) service.invitar(cuenta.id(), ana.id(), "p" + i + "@torres.pe", Rol.EMISOR, PORTAL);
        falla(() -> service.invitar(cuenta.id(), ana.id(), "otra@torres.pe", Rol.EMISOR, PORTAL), "DEMASIADAS_INVITACIONES");
    }

    @Test void conElCorreoCaidoLaInvitacionQuedaYSeDiceQueNoSalio() {
        correoCaido = true;
        Enviada e = service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        assertThat(e.correoEnviado()).isFalse();
        assertThat(service.invitaciones(cuenta.id(), ana.id())).extracting(Invitacion::email).containsExactly("caro@torres.pe");
    }

    @Test void reenviarCambiaElEnlaceYElAnteriorDejaDeServir() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        String primero = tokenDelCorreo("caro@torres.pe");
        UUID id = invitacionesMap.keySet().iterator().next();

        Enviada r = service.reenviar(cuenta.id(), ana.id(), id, PORTAL);

        String segundo = tokenDelCorreo("caro@torres.pe");
        assertThat(segundo).isNotEqualTo(primero);
        assertThat(r.invitacion().envios()).isEqualTo(2);
        falla(() -> service.consultar(primero), "TOKEN_INVALIDO");
        assertThat(service.consultar(segundo).email()).isEqualTo("caro@torres.pe");
    }

    @Test void revocarDejaElEnlaceInservible() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        String token = tokenDelCorreo("caro@torres.pe");
        UUID id = invitacionesMap.keySet().iterator().next();

        service.revocar(cuenta.id(), ana.id(), id);

        assertThat(service.invitaciones(cuenta.id(), ana.id())).isEmpty();
        falla(() -> service.aceptar(token, "Segura123"), "TOKEN_INVALIDO");
        falla(() -> service.revocar(cuenta.id(), ana.id(), id), "INVITACION_NO_PENDIENTE");
        falla(() -> service.reenviar(cuenta.id(), ana.id(), id, PORTAL), "INVITACION_NO_PENDIENTE");
    }

    @Test void laInvitacionDeOtraCuentaNoExisteParaEsta() {
        Cuenta otra = new Cuenta(UUID.randomUUID(), "Otra", "zoe@otra.pe");
        cuentas.guardar(otra);
        Usuario zoe = new Usuario(UUID.randomUUID(), otra.id(), "zoe@otra.pe", "hash", Rol.ADMIN, true, ahora);
        usuarios.guardar(zoe);
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        UUID id = invitacionesMap.keySet().iterator().next();

        falla(() -> service.revocar(otra.id(), zoe.id(), id), "NO_ENCONTRADO");
        falla(() -> service.reenviar(otra.id(), zoe.id(), id, PORTAL), "NO_ENCONTRADO");
        assertThat(invitacionesMap.get(id).pendiente()).isTrue();
    }

    @Test void soloUnAdministradorReenviaRevocaYListaInvitaciones() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        UUID id = invitacionesMap.keySet().iterator().next();
        falla(() -> service.invitaciones(cuenta.id(), beto.id()), "ROL_INSUFICIENTE");
        falla(() -> service.reenviar(cuenta.id(), beto.id(), id, PORTAL), "ROL_INSUFICIENTE");
        falla(() -> service.revocar(cuenta.id(), beto.id(), id), "ROL_INSUFICIENTE");
        assertThat(invitacionesMap.get(id).pendiente()).isTrue();
    }

    // --- Aceptar --------------------------------------------------------------------------------------------------------------------

    @Test void consultarDiceAQueLoInvitaron() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);

        AceptarInvitacionUseCase.Detalle d = service.consultar(tokenDelCorreo("caro@torres.pe"));

        assertThat(d).isEqualTo(new AceptarInvitacionUseCase.Detalle("caro@torres.pe", "Ferretería Torres", Rol.EMISOR, ahora.plus(Duration.ofDays(7))));
    }

    @Test void aceptarCreaElUsuarioConElRolYElCorreoVerificado() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.LECTURA, PORTAL);
        String token = tokenDelCorreo("caro@torres.pe");

        service.aceptar(token, "Segura123");

        Usuario caro = usuarios.buscarPorEmail("caro@torres.pe").orElseThrow();
        assertThat(caro.cuentaId()).isEqualTo(cuenta.id());
        assertThat(caro.rol()).isEqualTo(Rol.LECTURA);
        assertThat(caro.activo()).isTrue();
        assertThat(caro.correoVerificado()).isTrue();
        assertThat(caro.passwordHash()).isEqualTo("H(Segura123)");
        assertThat(service.invitaciones(cuenta.id(), ana.id())).isEmpty();
    }

    @Test void elEnlaceSirveUnaSolaVez() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        String token = tokenDelCorreo("caro@torres.pe");
        service.aceptar(token, "Segura123");

        falla(() -> service.aceptar(token, "Otra12345"), "TOKEN_INVALIDO");
        falla(() -> service.consultar(token), "TOKEN_INVALIDO");
        assertThat(usuarios.buscarPorEmail("caro@torres.pe").orElseThrow().passwordHash()).isEqualTo("H(Segura123)");
    }

    @Test void unaInvitacionVencidaNoSeAcepta() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        String token = tokenDelCorreo("caro@torres.pe");
        EquipoService despues = servicio(Clock.offset(clock, Duration.ofDays(7)));

        falla(() -> despues.aceptar(token, "Segura123"), "TOKEN_INVALIDO");
        falla(() -> despues.consultar(token), "TOKEN_INVALIDO");
        assertThat(usuarios.buscarPorEmail("caro@torres.pe")).isEmpty();
    }

    @Test void unaContrasenaDebilNoConsumeElEnlace() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        String token = tokenDelCorreo("caro@torres.pe");

        falla(() -> service.aceptar(token, "corta"), "PASSWORD_DEBIL");
        service.aceptar(token, "Segura123");
        assertThat(usuarios.buscarPorEmail("caro@torres.pe")).isPresent();
    }

    /** Si el correo se registró por su cuenta mientras la invitación esperaba, no se crea un segundo usuario con el mismo correo. */
    @Test void siElCorreoYaTieneUsuarioNoSeAcepta() {
        service.invitar(cuenta.id(), ana.id(), "caro@torres.pe", Rol.EMISOR, PORTAL);
        String token = tokenDelCorreo("caro@torres.pe");
        usuarios.guardar(new Usuario(UUID.randomUUID(), UUID.randomUUID(), "caro@torres.pe", "hash", Rol.ADMIN, true));

        falla(() -> service.aceptar(token, "Segura123"), "USUARIO_EXISTENTE");
        assertThat(invitacionesMap.values().iterator().next().pendiente()).isTrue();
    }

    @Test void unTokenInventadoONuloEsInvalido() {
        falla(() -> service.aceptar("inventado", "Segura123"), "TOKEN_INVALIDO");
        falla(() -> service.consultar(null), "TOKEN_INVALIDO");
    }
}
