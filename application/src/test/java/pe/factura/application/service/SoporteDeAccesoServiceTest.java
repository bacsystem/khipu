package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.SoporteDeAccesoUseCase.Destinatario;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.SesionRepository;
import pe.factura.application.port.out.UsuarioRepository;
import pe.factura.application.port.out.VerificacionCorreoRepository;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoporteDeAccesoServiceTest {
    static final ActorAdmin ACTOR = ActorAdmin.administrador(UUID.randomUUID(), "203.0.113.7");
    static final String PORTAL = "https://portal";

    UUID cuentaId = UUID.randomUUID();
    Usuario ana = new Usuario(UUID.randomUUID(), cuentaId, "ana@negocio.pe", "hash-de-ana", Rol.ADMIN, true);   // sin verificar
    Usuario luis = new Usuario(UUID.randomUUID(), cuentaId, "luis@negocio.pe", "hash-de-luis", Rol.ADMIN, true, Instant.parse("2026-09-02T10:00:00Z"));   // verificado
    Usuario baja = new Usuario(UUID.randomUUID(), cuentaId, "baja@negocio.pe", "hash", Rol.ADMIN, false);
    Usuario ajeno = new Usuario(UUID.randomUUID(), UUID.randomUUID(), "otro@otra.pe", "hash", Rol.ADMIN, true);

    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    { auditoria.uow = uow; }

    final Map<UUID, Usuario> usuariosMap = new HashMap<>();
    {
        for (Usuario u : List.of(ana, luis, baja, ajeno)) usuariosMap.put(u.id(), u);
    }
    /** Nunca se escribe un usuario: el administrador no fija contraseñas. */
    UsuarioRepository usuarios = new UsuarioRepository() {
        public void guardar(Usuario u) { throw new AssertionError("el soporte de acceso no modifica usuarios"); }
        public Optional<Usuario> buscar(UUID id) { return Optional.ofNullable(usuariosMap.get(id)); }
        public Optional<Usuario> buscarPorEmail(String e) { throw new AssertionError("se busca por id, dentro de la cuenta"); }
        public void marcarCorreoVerificado(UUID id, java.time.Instant cuando) { throw new AssertionError("el soporte de acceso no verifica correos"); }
    };

    final List<SesionRepository.TokenRecuperacion> recuperaciones = new ArrayList<>();
    final List<Boolean> recuperacionDentro = new ArrayList<>();
    SesionRepository sesiones = new SesionRepository() {
        public void crear(Sesion s) { throw new AssertionError("no se tocan las sesiones"); }
        public Optional<Sesion> buscarPorRefreshHash(String h) { throw new AssertionError("no se tocan las sesiones"); }
        public void revocar(UUID id) { throw new AssertionError("no se revoca ninguna sesión"); }
        public void revocarFamilia(UUID familia) { throw new AssertionError("no se revoca ninguna sesión"); }
        public void revocarTodas(UUID u) { throw new AssertionError("no se revoca ninguna sesión"); }
        public Optional<Sesion> buscar(UUID id) { throw new AssertionError("no se tocan las sesiones"); }
        public void rotar(UUID id, UUID nueva, java.time.Instant en) { throw new AssertionError("no se rota ninguna sesión"); }
        public void crearRecuperacion(TokenRecuperacion t) { recuperacionDentro.add(uow.dentro); recuperaciones.add(t); }
        public Optional<TokenRecuperacion> buscarRecuperacion(String h) { throw new AssertionError("no se consulta"); }
        public void marcarRecuperacionUsada(String h) { throw new AssertionError("no se consume"); }
    };

    final List<VerificacionCorreoRepository.Token> verificaciones = new ArrayList<>();
    final List<Boolean> verificacionDentro = new ArrayList<>();
    VerificacionCorreoRepository verificacion = new VerificacionCorreoRepository() {
        public void crear(Token t) { verificacionDentro.add(uow.dentro); verificaciones.add(t); }
        public Optional<Token> buscar(String h) { throw new AssertionError("no se consulta"); }
        public boolean usar(String h) { throw new AssertionError("no se consume"); }
        public int contarSinVencer(UUID u, java.time.Instant ahora) { throw new AssertionError("el operador no tiene el tope del reenvío del cliente"); }
    };

    final List<String[]> correos = new ArrayList<>();
    final List<Boolean> correoDentro = new ArrayList<>();
    boolean entrega = true;
    RuntimeException smtpRechaza;
    CorreoSender correo = new CorreoSender() {
        public void enviar(String para, String asunto, String cuerpo) {
            correoDentro.add(uow.dentro);
            if (smtpRechaza != null) throw smtpRechaza;
            correos.add(new String[]{para, asunto, cuerpo});
        }
        public void enviar(String para, String asunto, String cuerpo, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpo); }
        @Override public boolean entregaDeVerdad() { return entrega; }
    };

    ConfiguracionFake.Plantillas plantillasGuardadas = new ConfiguracionFake.Plantillas();
    SoporteDeAccesoService service = new SoporteDeAccesoService(usuarios, sesiones, verificacion, correo, uow, auditoria, Fakes.CLOCK, new PlantillasDeCorreo(plantillasGuardadas));

    /** El token que viaja en el enlace del último correo, para comprobar que es el que se guardó (por su hash). */
    private String tokenDelCorreo(String ruta) {
        String cuerpo = correos.get(correos.size() - 1)[2];
        return cuerpo.substring(cuerpo.indexOf(PORTAL + ruta) + (PORTAL + ruta).length()).strip();
    }

    // --- #199: el texto de los correos de acceso lo edita un administrador ----------------------------------------------------------

    @Test void elRestablecimientoSaleConElTextoQueUnAdministradorEdito() {
        plantillasGuardadas.filas.put(pe.factura.domain.plataforma.PlantillaDeCorreo.RECUPERACION_CLAVE,
                new pe.factura.application.port.out.PlantillasRepository.Guardada(new pe.factura.domain.plataforma.PlantillaDeCorreo.Texto("Soporte te ayuda", "Elige otra clave en {enlace} (dura {validez})"), java.time.Instant.EPOCH));

        service.enviarRestablecimiento(ACTOR, cuentaId, luis.id(), PORTAL);

        assertThat(correos.get(0)[1]).isEqualTo("Soporte te ayuda");
        assertThat(correos.get(0)[2]).startsWith("Elige otra clave en " + PORTAL + "/restablecer/").endsWith(" (dura 1 hora)");
    }

    // --- restablecer la contraseña -------------------------------------------------------------------------------------------------

    @Test void elRestablecimientoCreaUnEnlaceDeUnaHoraYSeLoMandaAlUsuario() {
        Destinatario d = service.enviarRestablecimiento(ACTOR, cuentaId, luis.id(), PORTAL);

        assertThat(d).isEqualTo(new Destinatario(luis.id(), "luis@negocio.pe"));
        assertThat(recuperaciones).hasSize(1);
        var t = recuperaciones.get(0);
        assertThat(t.usuarioId()).isEqualTo(luis.id());
        assertThat(t.usado()).isFalse();
        assertThat(t.expiraEn()).isEqualTo(Fakes.CLOCK.instant().plus(Duration.ofHours(1)));
        assertThat(correos).hasSize(1);
        assertThat(correos.get(0)[0]).isEqualTo("luis@negocio.pe");
        assertThat(correos.get(0)[1]).isEqualTo("Restablecer contraseña");
        assertThat(correos.get(0)[2]).contains("válido 1 hora").contains(PORTAL + "/restablecer/");
        // Lo que viaja en el enlace es el token cuyo hash se guardó: sin el hash, el enlace no serviría.
        assertThat(TokenOpaco.hash(tokenDelCorreo("/restablecer/"))).isEqualTo(t.tokenHash());
    }

    @Test void elRestablecimientoQuedaEnLaBitacoraSinElEnlaceNiElToken() {
        service.enviarRestablecimiento(ACTOR, cuentaId, luis.id(), PORTAL);

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.accion()).isEqualTo(AccionAdmin.ENVIAR_RESTABLECIMIENTO);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.tenantId()).isNull();
        assertThat(r.detalle()).isEqualTo("usuario=luis@negocio.pe");
        assertThat(r.ocurridoEn()).isEqualTo(Fakes.CLOCK.instant());
        assertThat(r.detalle()).doesNotContain(tokenDelCorreo("/restablecer/")).doesNotContain("/restablecer/");
    }

    @Test void elEnlaceYLaBitacoraVanEnLaMismaTransaccionYElCorreoSaleDespues() {
        service.enviarRestablecimiento(ACTOR, cuentaId, luis.id(), PORTAL);

        assertThat(recuperacionDentro).containsExactly(true);
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
        assertThat(correoDentro).as("el correo no sale dentro de la transacción: si esta se deshace, no habría mandado un enlace muerto").containsExactly(false);
    }

    @Test void nuncaSeFijaNiSeMuestraUnaContrasena() {
        Destinatario d = service.enviarRestablecimiento(ACTOR, cuentaId, luis.id(), PORTAL);

        // El fake de usuarios lanza si alguien llama a `guardar`: ningún usuario ni su hash de contraseña se tocan.
        assertThat(luis.passwordHash()).isEqualTo("hash-de-luis");
        assertThat(d.toString()).doesNotContain("hash-de-luis").doesNotContain(tokenDelCorreo("/restablecer/"));
        assertThat(correos.get(0)[2]).doesNotContain("hash-de-luis");
    }

    @Test void siLaBitacoraFallaNoSaleNingunCorreo() {
        auditoria.falla = new IllegalStateException("tabla de auditoría no disponible");

        assertThatThrownBy(() -> service.enviarRestablecimiento(ACTOR, cuentaId, luis.id(), PORTAL)).isInstanceOf(IllegalStateException.class);

        assertThat(correos).isEmpty();
    }

    @Test void unUsuarioDeOtraCuentaNoSeEncuentraAunqueExista() {
        assertThatThrownBy(() -> service.enviarRestablecimiento(ACTOR, cuentaId, ajeno.id(), PORTAL)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.reenviarVerificacion(ACTOR, cuentaId, ajeno.id(), PORTAL)).extracting("codigo").isEqualTo("NO_ENCONTRADO");

        assertNadaPaso();
    }

    @Test void unUsuarioQueNoExisteOUnIdNuloNoSeEncuentran() {
        assertThatThrownBy(() -> service.enviarRestablecimiento(ACTOR, cuentaId, UUID.randomUUID(), PORTAL)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.enviarRestablecimiento(ACTOR, cuentaId, null, PORTAL)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.enviarRestablecimiento(ACTOR, null, luis.id(), PORTAL)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.reenviarVerificacion(ACTOR, cuentaId, null, PORTAL)).extracting("codigo").isEqualTo("NO_ENCONTRADO");

        assertNadaPaso();
    }

    @Test void unUsuarioDesactivadoNoRecibeNada() {
        assertThatThrownBy(() -> service.enviarRestablecimiento(ACTOR, cuentaId, baja.id(), PORTAL)).extracting("codigo").isEqualTo("USUARIO_INACTIVO");
        assertThatThrownBy(() -> service.reenviarVerificacion(ACTOR, cuentaId, baja.id(), PORTAL)).extracting("codigo").isEqualTo("USUARIO_INACTIVO");

        assertNadaPaso();
    }

    /** Sin SMTP el adaptador escribe en el log y no lanza: contestar «enviado» sería mentir. No se crea nada. */
    @Test void sinCorreoConfiguradoEnElServidorNoSeCreaNadaYSeDice() {
        entrega = false;

        assertThatThrownBy(() -> service.enviarRestablecimiento(ACTOR, cuentaId, luis.id(), PORTAL)).extracting("codigo").isEqualTo("CORREO_NO_CONFIGURADO");
        assertThatThrownBy(() -> service.reenviarVerificacion(ACTOR, cuentaId, ana.id(), PORTAL)).extracting("codigo").isEqualTo("CORREO_NO_CONFIGURADO");

        assertNadaPaso();
    }

    @Test void siElServidorDeCorreoRechazaElEnvioSeDiceYElIntentoQuedaEnLaBitacora() {
        smtpRechaza = new IllegalStateException("550 buzón no disponible");

        assertThatThrownBy(() -> service.enviarRestablecimiento(ACTOR, cuentaId, luis.id(), PORTAL)).extracting("codigo").isEqualTo("CORREO_NO_ENVIADO");

        assertThat(auditoria.registros).as("es el intento del administrador: queda aunque el correo no haya salido").hasSize(1);
        assertThat(correos).isEmpty();
    }

    // --- reenviar la verificación ----------------------------------------------------------------------------------------------------

    @Test void laVerificacionCreaUnEnlaceDe24HorasYSeLoReenvia() {
        Destinatario d = service.reenviarVerificacion(ACTOR, cuentaId, ana.id(), PORTAL);

        assertThat(d).isEqualTo(new Destinatario(ana.id(), "ana@negocio.pe"));
        assertThat(verificaciones).hasSize(1);
        var t = verificaciones.get(0);
        assertThat(t.usuarioId()).isEqualTo(ana.id());
        assertThat(t.usado()).isFalse();
        assertThat(t.expiraEn()).isEqualTo(Fakes.CLOCK.instant().plus(Duration.ofHours(24)));
        assertThat(correos.get(0)[0]).isEqualTo("ana@negocio.pe");
        assertThat(correos.get(0)[1]).isEqualTo("Verifica tu correo en khipu");
        assertThat(correos.get(0)[2]).contains("válido 24 horas").contains(PORTAL + "/verificar/");
        assertThat(TokenOpaco.hash(tokenDelCorreo("/verificar/"))).isEqualTo(t.tokenHash());
        assertThat(recuperaciones).as("un enlace de verificación no sirve para cambiar la contraseña").isEmpty();
    }

    @Test void laVerificacionQuedaEnLaBitacoraYVaEnLaMismaTransaccion() {
        service.reenviarVerificacion(ACTOR, cuentaId, ana.id(), PORTAL);

        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.accion()).isEqualTo(AccionAdmin.REENVIAR_VERIFICACION);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.detalle()).isEqualTo("usuario=ana@negocio.pe").doesNotContain("/verificar/");
        assertThat(verificacionDentro).containsExactly(true);
        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
        assertThat(correoDentro).containsExactly(false);
    }

    @Test void conElCorreoYaVerificadoNoHayNadaQueReenviar() {
        assertThatThrownBy(() -> service.reenviarVerificacion(ACTOR, cuentaId, luis.id(), PORTAL)).extracting("codigo").isEqualTo("CORREO_YA_VERIFICADO");

        assertNadaPaso();
    }

    /** Restablecer la contraseña vale aunque el correo ya esté verificado: es el caso de soporte más frecuente. */
    @Test void unUsuarioYaVerificadoSiPuedeRestablecer() {
        service.enviarRestablecimiento(ACTOR, cuentaId, luis.id(), PORTAL);

        assertThat(correos).hasSize(1);
    }

    private void assertNadaPaso() {
        assertThat(recuperaciones).isEmpty();
        assertThat(verificaciones).isEmpty();
        assertThat(correos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }
}
