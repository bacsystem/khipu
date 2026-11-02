package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.SesionRepository.Sesion;
import pe.factura.application.port.out.SesionRepository.TokenRecuperacion;
import pe.factura.domain.cuenta.Cuenta;
import pe.factura.domain.cuenta.Rol;
import pe.factura.domain.cuenta.Usuario;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAuthRepositoriesTest extends PersistenciaTestBase {
    JdbcCuentaRepository cuentas = new JdbcCuentaRepository(jdbc);
    JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(jdbc);
    JdbcSesionRepository sesiones = new JdbcSesionRepository(jdbc);
    SecretCipher sinCifrar = new SecretCipher() { public byte[] cifrar(byte[] p) { return p; } public byte[] descifrar(byte[] c) { return c; } };
    JdbcTenantRepository tenants = new JdbcTenantRepository(jdbc, sinCifrar);

    @Test void cuentaUsuarioYEmpresasDeLaCuenta() {
        jdbc.update("TRUNCATE token_recuperacion, sesion, usuario, cuenta CASCADE");
        Cuenta c = new Cuenta(UUID.randomUUID(), "Mi negocio", "ana@negocio.pe", "987654321");
        cuentas.guardar(c);
        Usuario u = new Usuario(UUID.randomUUID(), c.id(), "ana@negocio.pe", "hash", Rol.ADMIN, true);
        usuarios.guardar(u);
        assertThat(cuentas.buscarPorEmail("ana@negocio.pe")).contains(c);
        assertThat(cuentas.buscar(c.id()).orElseThrow().telefono()).isEqualTo("987654321");
        // Cuentas creadas antes de este campo (o sin celular): vuelve null, no falla.
        Cuenta sinTelefono = new Cuenta(UUID.randomUUID(), "Sin celular", "sin-telefono@negocio.pe");
        cuentas.guardar(sinTelefono);
        assertThat(cuentas.buscar(sinTelefono.id()).orElseThrow().telefono()).isNull();
        assertThat(usuarios.buscarPorEmail("ana@negocio.pe")).contains(u);
        usuarios.guardar(u.conPasswordHash("hash2"));
        assertThat(usuarios.buscar(u.id()).orElseThrow().passwordHash()).isEqualTo("hash2");

        Tenant t = new Tenant(UUID.randomUUID(), "20100066603", "EMPRESA SAC", Entorno.BETA, null, null);
        tenants.guardar(t);
        assertThat(tenants.cuentaDe(t.id())).isEmpty();
        tenants.asignarCuenta(t.id(), c.id());
        assertThat(tenants.cuentaDe(t.id())).contains(c.id());
        assertThat(tenants.listarPorCuenta(c.id())).extracting(Tenant::id).containsExactly(t.id());
        assertThat(tenants.listarPorCuenta(UUID.randomUUID())).isEmpty();
    }

    @Test void sesionesYRecuperacion() {
        jdbc.update("TRUNCATE token_recuperacion, sesion, usuario, cuenta CASCADE");
        Cuenta c = new Cuenta(UUID.randomUUID(), "A", "a@b.pe"); cuentas.guardar(c);
        Usuario u = new Usuario(UUID.randomUUID(), c.id(), "a@b.pe", "h", Rol.ADMIN, true); usuarios.guardar(u);
        Sesion s = new Sesion(UUID.randomUUID(), u.id(), "abc", Instant.parse("2030-01-01T00:00:00Z"), false);
        sesiones.crear(s);
        assertThat(sesiones.buscarPorRefreshHash("abc").orElseThrow().revocada()).isFalse();
        sesiones.revocarTodas(u.id());
        assertThat(sesiones.buscarPorRefreshHash("abc").orElseThrow().revocada()).isTrue();
        sesiones.crearRecuperacion(new TokenRecuperacion("t1", u.id(), Instant.parse("2030-01-01T00:00:00Z"), false));
        sesiones.marcarRecuperacionUsada("t1");
        assertThat(sesiones.buscarRecuperacion("t1").orElseThrow().usado()).isTrue();
    }

    /** S6: rotar guarda cuándo y por cuál (la primera vez); revocar por cualquier otro motivo borra esa marca y con ella la gracia. */
    @Test void rotarMarcaLaGraciaYRevocarLaBorra() {
        jdbc.update("TRUNCATE token_recuperacion, sesion, usuario, cuenta CASCADE");
        Cuenta c = new Cuenta(UUID.randomUUID(), "A", "a@b.pe"); cuentas.guardar(c);
        Usuario u = new Usuario(UUID.randomUUID(), c.id(), "a@b.pe", "h", Rol.ADMIN, true); usuarios.guardar(u);
        Sesion vieja = new Sesion(UUID.randomUUID(), u.id(), "vieja", Instant.parse("2030-01-01T00:00:00Z"), false);
        Sesion nueva = new Sesion(UUID.randomUUID(), u.id(), "nueva", Instant.parse("2030-01-01T00:00:00Z"), false);
        sesiones.crear(vieja);
        sesiones.crear(nueva);
        Instant en = Instant.parse("2026-10-09T12:00:00Z");

        sesiones.rotar(vieja.id(), nueva.id(), en);
        sesiones.rotar(vieja.id(), UUID.randomUUID(), en.plusSeconds(10));   // otra instancia, con el mismo refresh

        Sesion rotada = sesiones.buscar(vieja.id()).orElseThrow();
        assertThat(rotada.revocada()).isTrue();
        assertThat(rotada.rotadaEn()).isEqualTo(en);
        assertThat(rotada.reemplazadaPor()).isEqualTo(nueva.id());
        assertThat(sesiones.buscar(nueva.id()).orElseThrow().rotadaEn()).isNull();

        sesiones.revocar(vieja.id());
        assertThat(sesiones.buscar(vieja.id()).orElseThrow()).extracting(Sesion::rotadaEn, Sesion::reemplazadaPor).containsOnlyNulls();

        sesiones.rotar(nueva.id(), UUID.randomUUID(), en);
        sesiones.revocarTodas(u.id());
        assertThat(sesiones.buscar(nueva.id()).orElseThrow()).extracting(Sesion::rotadaEn, Sesion::reemplazadaPor).containsOnlyNulls();
        assertThat(sesiones.buscar(UUID.randomUUID())).isEmpty();
    }

    /** 271-H1: el logout revoca toda la familia (la sesión del login, sus rotaciones y las ramas de la gracia) y nada de otro login. */
    @Test void revocarFamiliaCierraTodoLoDeEseLoginYNadaMas() {
        jdbc.update("TRUNCATE token_recuperacion, sesion, usuario, cuenta CASCADE");
        Cuenta c = new Cuenta(UUID.randomUUID(), "A", "a@b.pe"); cuentas.guardar(c);
        Usuario u = new Usuario(UUID.randomUUID(), c.id(), "a@b.pe", "h", Rol.ADMIN, true); usuarios.guardar(u);
        Instant vence = Instant.parse("2030-01-01T00:00:00Z");
        Sesion login = new Sesion(UUID.randomUUID(), u.id(), "login", vence, false);
        Sesion rotada = new Sesion(UUID.randomUUID(), u.id(), "rotada", vence, login.familia());
        Sesion rama = new Sesion(UUID.randomUUID(), u.id(), "rama", vence, login.familia());
        Sesion otroLogin = new Sesion(UUID.randomUUID(), u.id(), "otro", vence, false);
        for (Sesion s : new Sesion[]{login, rotada, rama, otroLogin}) sesiones.crear(s);
        sesiones.rotar(login.id(), rotada.id(), Instant.parse("2026-10-09T12:00:00Z"));

        sesiones.revocarFamilia(login.familia());

        for (Sesion s : new Sesion[]{login, rotada, rama})
            assertThat(sesiones.buscar(s.id()).orElseThrow()).extracting(Sesion::revocada, Sesion::rotadaEn).containsExactly(true, null);
        assertThat(sesiones.buscar(otroLogin.id()).orElseThrow().revocada()).as("otro login, otra familia").isFalse();
        assertThat(sesiones.buscar(rama.id()).orElseThrow().familia()).isEqualTo(login.id());
    }
}
