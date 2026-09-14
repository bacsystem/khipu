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
        Cuenta c = new Cuenta(UUID.randomUUID(), "Mi negocio", "ana@negocio.pe");
        cuentas.guardar(c);
        Usuario u = new Usuario(UUID.randomUUID(), c.id(), "ana@negocio.pe", "hash", Rol.ADMIN, true);
        usuarios.guardar(u);
        assertThat(cuentas.buscarPorEmail("ana@negocio.pe")).contains(c);
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
}
