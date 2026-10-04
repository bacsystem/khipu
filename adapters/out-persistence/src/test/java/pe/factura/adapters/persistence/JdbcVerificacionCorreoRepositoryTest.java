package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.VerificacionCorreoRepository.Token;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcVerificacionCorreoRepositoryTest extends PersistenciaTestBase {
    final JdbcVerificacionCorreoRepository repo = new JdbcVerificacionCorreoRepository(jdbc);
    final JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(jdbc);

    private Usuario usuario() {
        Cuenta c = new Cuenta(UUID.randomUUID(), "Mi negocio", "ana" + UUID.randomUUID() + "@b.pe");
        new JdbcCuentaRepository(jdbc).guardar(c);
        Usuario u = new Usuario(UUID.randomUUID(), c.id(), c.email(), "hash", Rol.ADMIN, true);
        usuarios.guardar(u);
        return u;
    }

    @Test void guardaYBuscaElToken() {
        Usuario u = usuario();
        Instant expira = Instant.now().plus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        repo.crear(new Token("a".repeat(64), u.id(), expira, false));

        assertThat(repo.buscar("a".repeat(64))).contains(new Token("a".repeat(64), u.id(), expira, false));
        assertThat(repo.buscar("b".repeat(64))).isEmpty();
    }

    @Test void unTokenSeUsaUnaSolaVez() {
        Usuario u = usuario();
        repo.crear(new Token("a".repeat(64), u.id(), Instant.now().plusSeconds(60), false));

        assertThat(repo.usar("a".repeat(64))).isTrue();
        assertThat(repo.usar("a".repeat(64))).isFalse();
        assertThat(repo.buscar("a".repeat(64)).orElseThrow().usado()).isTrue();
        assertThat(repo.usar("c".repeat(64))).as("inexistente").isFalse();
    }

    /** El usuario guarda cuándo verificó su correo; un usuario nuevo empieza sin verificar. */
    @Test void elUsuarioGuardaLaVerificacionDelCorreo() {
        Usuario u = usuario();
        assertThat(usuarios.buscar(u.id()).orElseThrow().correoVerificado()).isFalse();

        Instant cuando = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        usuarios.guardar(u.conCorreoVerificado(cuando));

        assertThat(usuarios.buscar(u.id()).orElseThrow().correoVerificadoEn()).isEqualTo(cuando);
        assertThat(usuarios.buscarPorEmail(u.email()).orElseThrow().correoVerificadoEn()).isEqualTo(cuando);
    }
}
