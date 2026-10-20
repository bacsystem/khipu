package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Equipo;
import pe.factura.domain.cuenta.Invitacion;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #21: el equipo de una cuenta y sus invitaciones en la base. */
class JdbcEquipoEInvitacionesTest extends PersistenciaTestBase {
    final JdbcEquipoRepository equipos = new JdbcEquipoRepository(jdbc);
    final JdbcInvitacionRepository invitaciones = new JdbcInvitacionRepository(jdbc);
    final JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(jdbc);
    final Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private Cuenta cuenta() {
        Cuenta c = new Cuenta(UUID.randomUUID(), "Mi negocio", "dueno" + UUID.randomUUID() + "@b.pe");
        new JdbcCuentaRepository(jdbc).guardar(c);
        return c;
    }

    private Usuario usuario(Cuenta c, String email, Rol rol) {
        Usuario u = new Usuario(UUID.randomUUID(), c.id(), email, "hash", rol, true);
        usuarios.guardar(u);
        return u;
    }

    private Invitacion invitacion(Cuenta c, String email, String hash) {
        return Invitacion.nueva(c.id(), email, Rol.EMISOR, hash, ahora, Duration.ofDays(7), null);
    }

    @Test void elEquipoTraeSoloLosUsuariosDeLaCuentaConSuRolYEstado() {
        Cuenta c = cuenta();
        Usuario ana = usuario(c, "ana@b.pe", Rol.ADMIN);
        Usuario beto = usuario(c, "beto@b.pe", Rol.LECTURA);
        usuarios.guardar(beto.desactivar());
        usuario(cuenta(), "otro@b.pe", Rol.ADMIN);

        Equipo e = equipos.deLaCuenta(c.id());

        assertThat(e.miembros()).extracting(Usuario::email).containsExactlyInAnyOrder("ana@b.pe", "beto@b.pe");
        assertThat(e.miembro(beto.id()).activo()).isFalse();
        assertThat(e.miembro(beto.id()).rol()).isEqualTo(Rol.LECTURA);
        assertThat(e.miembro(ana.id()).esAdminActivo()).isTrue();
        assertThat(equipos.deLaCuenta(UUID.randomUUID()).miembros()).isEmpty();
    }

    /** Mientras una transacción tiene el equipo para cambiarlo, otra que lo pide igual espera a que termine. */
    @Test void cambiarElEquipoEsperaAQueTermineOtroCambio() throws Exception {
        Cuenta c = cuenta();
        usuario(c, "ana@b.pe", Rol.ADMIN);
        CountDownLatch tomado = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);

        CompletableFuture<Void> primera = CompletableFuture.runAsync(() -> uow.ejecutar(() -> {
            equipos.deLaCuentaParaCambiar(c.id());
            tomado.countDown();
            try { soltar.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Equipo> segunda = CompletableFuture.supplyAsync(() -> uow.ejecutar(() -> equipos.deLaCuentaParaCambiar(c.id())));

        Thread.sleep(500);
        assertThat(segunda).as("la segunda espera al bloqueo").isNotDone();
        soltar.countDown();
        primera.get(10, TimeUnit.SECONDS);
        assertThat(segunda.get(10, TimeUnit.SECONDS).miembros()).hasSize(1);
    }

    @Test void guardaYLeeUnaInvitacion() {
        Cuenta c = cuenta();
        Usuario ana = usuario(c, "ana@b.pe", Rol.ADMIN);
        Invitacion i = Invitacion.nueva(c.id(), "caro@b.pe", Rol.LECTURA, "a".repeat(64), ahora, Duration.ofDays(7), ana.id());
        invitaciones.crear(i);

        assertThat(invitaciones.buscar(i.id())).contains(i);
        assertThat(invitaciones.buscarPorTokenHash("a".repeat(64))).contains(i);
        assertThat(invitaciones.buscarPorTokenHash("b".repeat(64))).isEmpty();
    }

    @Test void pendientesSonLasDeLaCuentaSinAceptarNiRevocar() {
        Cuenta c = cuenta();
        Invitacion pendiente = invitacion(c, "a@b.pe", "h1");
        Invitacion revocada = invitacion(c, "b@b.pe", "h2");
        Invitacion aceptada = invitacion(c, "c@b.pe", "h3");
        for (Invitacion i : new Invitacion[]{pendiente, revocada, aceptada}) invitaciones.crear(i);
        invitaciones.crear(invitacion(cuenta(), "d@b.pe", "h4"));
        invitaciones.actualizarPendiente(revocada.revocada());
        invitaciones.aceptar(aceptada.id(), "h3", ahora);

        assertThat(invitaciones.pendientes(c.id())).extracting(Invitacion::email).containsExactly("a@b.pe");
    }

    @Test void aceptarSirveUnaVezConElTokenVigenteYSinVencer() {
        Cuenta c = cuenta();
        Invitacion i = invitacion(c, "a@b.pe", "h1");
        invitaciones.crear(i);

        assertThat(invitaciones.aceptar(i.id(), "otro", ahora)).as("con otro token").isFalse();
        assertThat(invitaciones.aceptar(i.id(), "h1", ahora.plus(Duration.ofDays(7)))).as("vencida").isFalse();
        assertThat(invitaciones.aceptar(i.id(), "h1", ahora)).isTrue();
        assertThat(invitaciones.aceptar(i.id(), "h1", ahora)).as("ya aceptada").isFalse();
        assertThat(invitaciones.buscar(i.id()).orElseThrow().estado()).isEqualTo(Invitacion.Estado.ACEPTADA);
        assertThat(jdbc.queryForObject("SELECT aceptada_en IS NOT NULL FROM invitacion WHERE id = ?", Boolean.class, i.id())).isTrue();
    }

    /** Un reenvío que llega después de aceptarla no la revive, y el enlace viejo no sirve tras un reenvío. */
    @Test void soloSeActualizaLaQueSiguePendiente() {
        Cuenta c = cuenta();
        Invitacion i = invitacion(c, "a@b.pe", "h1");
        invitaciones.crear(i);

        Invitacion reenviada = i.reenviada("h2", ahora, Duration.ofDays(7));
        assertThat(invitaciones.actualizarPendiente(reenviada)).isTrue();
        assertThat(invitaciones.buscar(i.id())).contains(reenviada);
        assertThat(invitaciones.aceptar(i.id(), "h1", ahora)).as("el enlace anterior").isFalse();

        assertThat(invitaciones.aceptar(i.id(), "h2", ahora)).isTrue();
        assertThat(invitaciones.actualizarPendiente(reenviada.reenviada("h3", ahora, Duration.ofDays(7)))).isFalse();
        assertThat(invitaciones.actualizarPendiente(reenviada.revocada())).isFalse();
        assertThat(invitaciones.buscar(i.id()).orElseThrow().estado()).isEqualTo(Invitacion.Estado.ACEPTADA);
    }

    @Test void unaSolaPendientePorCorreoEnCadaCuenta() {
        Cuenta c = cuenta();
        invitaciones.crear(invitacion(c, "a@b.pe", "h1"));

        assertThatThrownBy(() -> invitaciones.crear(invitacion(c, "a@b.pe", "h2"))).isInstanceOf(DuplicateKeyException.class);
        invitaciones.crear(invitacion(cuenta(), "a@b.pe", "h3"));   // en otra cuenta, sí
    }
}
