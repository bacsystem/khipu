package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.ImpersonarUsuarioUseCase.Impersonacion;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.application.port.out.UsuarioRepository;
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

/**
 * Impersonar a un usuario (#184): un administrador mira el portal como un cliente, con un token de alcance limitado y expiración corta, y queda registrado
 * quién impersonó a quién, cuándo y por cuánto tiempo.
 */
class ImpersonarUsuarioServiceTest {
    static final UUID ADMIN = UUID.randomUUID();
    static final ActorAdmin ACTOR = ActorAdmin.administrador(ADMIN, "203.0.113.7");

    UUID cuentaId = UUID.randomUUID();
    Usuario ana = new Usuario(UUID.randomUUID(), cuentaId, "ana@negocio.pe", "hash", Rol.ADMIN, true, Instant.parse("2026-09-01T10:00:00Z"));
    Map<UUID, Usuario> guardados = new HashMap<>();
    {
        guardados.put(ana.id(), ana);
    }

    UsuarioRepository usuarios = new UsuarioRepository() {
        public void guardar(Usuario u) { throw new AssertionError("impersonar no modifica al usuario"); }
        public Optional<Usuario> buscar(UUID id) { return Optional.ofNullable(guardados.get(id)); }
        public Optional<Usuario> buscarPorEmail(String email) { throw new AssertionError("no se busca por correo"); }
    };

    /** Guarda lo que le pidieron firmar y devuelve un token reconocible. */
    static class Tokens implements TokenEmisor {
        final List<Claims> emitidos = new ArrayList<>();
        public String emitir(Claims claims) { emitidos.add(claims); return "token-de-soporte-" + emitidos.size(); }
        public Optional<Claims> verificar(String token) { throw new AssertionError("no se verifica aquí"); }
    }

    Tokens tokens = new Tokens();
    Fakes.UowTransaccional uow = new Fakes.UowTransaccional();
    Fakes.Auditoria auditoria = new Fakes.Auditoria();
    ImpersonarUsuarioService service;

    {
        auditoria.uow = uow;
        service = new ImpersonarUsuarioService(usuarios, tokens, auditoria, uow, Fakes.CLOCK);
    }

    @Test void devuelveUnTokenDelUsuarioQueVenceEnQuinceMinutos() {
        Impersonacion i = service.impersonar(ACTOR, cuentaId, ana.id());

        assertThat(i.token()).isEqualTo("token-de-soporte-1");
        assertThat(i.expiraEn()).isEqualTo(Fakes.CLOCK.instant().plus(Duration.ofMinutes(15)));
        assertThat(i.usuario()).isEqualTo(ana);
    }

    @Test void elTokenEsUnaSesionDeSoporteDelUsuarioConElAdministradorYLaExpiracion() {
        service.impersonar(ACTOR, cuentaId, ana.id());

        TokenEmisor.Claims c = tokens.emitidos.get(0);
        assertThat(c.esSoporte()).isTrue();
        assertThat(c.usuarioId()).isEqualTo(ana.id());
        assertThat(c.cuentaId()).isEqualTo(cuentaId);
        assertThat(c.rol()).isEqualTo(Rol.ADMIN);
        assertThat(c.soporte().administradorId()).isEqualTo(ADMIN);
        assertThat(c.soporte().expiraEn()).isEqualTo(Fakes.CLOCK.instant().plus(Duration.ofMinutes(15)));
    }

    @Test void quedaEnLaBitacoraQuienImpersonoAQuienCuandoYPorCuantoTiempo() {
        service.impersonar(ACTOR, cuentaId, ana.id());

        assertThat(auditoria.registros).hasSize(1);
        RegistroAuditoria r = auditoria.registros.get(0);
        assertThat(r.actor()).isEqualTo(ACTOR);
        assertThat(r.accion()).isEqualTo(AccionAdmin.IMPERSONAR_USUARIO);
        assertThat(r.cuentaId()).isEqualTo(cuentaId);
        assertThat(r.tenantId()).isNull();
        assertThat(r.detalle()).isEqualTo("usuario=ana@negocio.pe duracion_s=900");
        assertThat(r.ocurridoEn()).isEqualTo(Fakes.CLOCK.instant());
    }

    @Test void laBitacoraVaEnUnaTransaccionYNuncaLlevaElToken() {
        service.impersonar(ACTOR, cuentaId, ana.id());

        assertThat(auditoria.dentroAlRegistrar).containsExactly(true);
        assertThat(auditoria.registros.get(0).detalle()).doesNotContain("token");
    }

    /** Sin bitácora no hay impersonación: si no se puede dejar constancia, el token no sale. */
    @Test void siLaBitacoraFallaNoSaleNingunToken() {
        auditoria.falla = new IllegalStateException("tabla de auditoría no disponible");

        assertThatThrownBy(() -> service.impersonar(ACTOR, cuentaId, ana.id())).isInstanceOf(IllegalStateException.class);
    }

    /** Impersonar a alguien es de una persona: la clave de plataforma no identifica a nadie y la bitácora no podría decir quién fue. */
    @Test void laClaveDePlataformaNoPuedeImpersonar() {
        assertThatThrownBy(() -> service.impersonar(ActorAdmin.clavePlataforma("127.0.0.1"), cuentaId, ana.id())).extracting("codigo").isEqualTo("REQUIERE_ADMINISTRADOR");
        assertThat(tokens.emitidos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unUsuarioDeOtraCuentaNoSeEncuentraAunqueExista() {
        assertThatThrownBy(() -> service.impersonar(ACTOR, UUID.randomUUID(), ana.id())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(tokens.emitidos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void unUsuarioInexistenteOIdsNulosSonNoEncontrados() {
        assertThatThrownBy(() -> service.impersonar(ACTOR, cuentaId, UUID.randomUUID())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.impersonar(ACTOR, cuentaId, null)).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThatThrownBy(() -> service.impersonar(ACTOR, null, ana.id())).extracting("codigo").isEqualTo("NO_ENCONTRADO");
        assertThat(tokens.emitidos).isEmpty();
    }

    @Test void aUnUsuarioDesactivadoNoSeLeImpersona() {
        guardados.put(ana.id(), ana.desactivar());

        assertThatThrownBy(() -> service.impersonar(ACTOR, cuentaId, ana.id())).extracting("codigo").isEqualTo("USUARIO_INACTIVO");
        assertThat(tokens.emitidos).isEmpty();
        assertThat(auditoria.registros).isEmpty();
    }

    @Test void cadaImpersonacionDejaSuPropioRegistro() {
        service.impersonar(ACTOR, cuentaId, ana.id());
        service.impersonar(ACTOR, cuentaId, ana.id());

        assertThat(auditoria.registros).hasSize(2);
        assertThat(tokens.emitidos).hasSize(2);
    }

    /** Un usuario sin el correo verificado también se puede mirar: el soporte existe justo para eso. */
    @Test void tambienSePuedeImpersonarAUnUsuarioSinVerificar() {
        Usuario nuevo = new Usuario(UUID.randomUUID(), cuentaId, "luis@negocio.pe", "hash", Rol.EMISOR, true);
        guardados.put(nuevo.id(), nuevo);

        Impersonacion i = service.impersonar(ACTOR, cuentaId, nuevo.id());

        assertThat(i.usuario().correoVerificado()).isFalse();
        assertThat(tokens.emitidos.get(0).rol()).isEqualTo(Rol.EMISOR);
    }
}
