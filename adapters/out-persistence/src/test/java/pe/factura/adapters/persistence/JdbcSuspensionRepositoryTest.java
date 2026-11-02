package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Suspensión de cuentas (#182), con Postgres real. Lo que importa: el cambio es atómico y no toca a ninguna otra cuenta ni empresa. */
class JdbcSuspensionRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");

    JdbcSuspensionRepository repo = new JdbcSuspensionRepository(jdbc);

    UUID cuenta(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cuenta (id, nombre, email, telefono, created_at) VALUES (?, 'Mi negocio', ?, '987654321', ?)", id, email, Timestamp.from(T0));
        return id;
    }

    UUID empresa(UUID cuenta, String ruc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, cuenta_id) VALUES (?, ?, 'EMPRESA SAC', 'BETA', ?)", id, ruc, cuenta);
        return id;
    }

    @Test void unaCuentaNuevaNoEstaSuspendida() {
        assertThat(repo.cuentaSuspendida(cuenta("ana@negocio.pe"))).isFalse();
    }

    @Test void unaCuentaQueNoExisteNoEstaSuspendida() {
        assertThat(repo.cuentaSuspendida(UUID.randomUUID())).isFalse();
    }

    @Test void suspenderLaMarcaYReactivarLaDevuelve() {
        UUID c = cuenta("ana@negocio.pe");

        assertThat(repo.suspender(c, T0.plusSeconds(60))).isTrue();
        assertThat(repo.cuentaSuspendida(c)).isTrue();

        assertThat(repo.reactivar(c)).isTrue();
        assertThat(repo.cuentaSuspendida(c)).isFalse();
    }

    /** Condicional: de dos pedidos a la vez uno solo lo logra, y el segundo no pisa la hora del primero. */
    @Test void suspenderDosVecesNoCambiaNadaLaSegunda() {
        UUID c = cuenta("ana@negocio.pe");

        assertThat(repo.suspender(c, T0.plusSeconds(60))).isTrue();
        assertThat(repo.suspender(c, T0.plusSeconds(999))).isFalse();

        assertThat(jdbc.queryForObject("SELECT suspendida_en FROM cuenta WHERE id = ?", Timestamp.class, c).toInstant()).isEqualTo(T0.plusSeconds(60));
    }

    @Test void reactivarUnaCuentaActivaNoCambiaNada() {
        UUID c = cuenta("ana@negocio.pe");

        assertThat(repo.reactivar(c)).isFalse();
        assertThat(repo.cuentaSuspendida(c)).isFalse();
    }

    @Test void reactivarDosVecesSoloLaPrimeraCambia() {
        UUID c = cuenta("ana@negocio.pe");
        repo.suspender(c, T0);

        assertThat(repo.reactivar(c)).isTrue();
        assertThat(repo.reactivar(c)).isFalse();
    }

    @Test void suspenderUnaCuentaQueNoExisteNoCambiaNada() {
        assertThat(repo.suspender(UUID.randomUUID(), T0)).isFalse();
        assertThat(repo.reactivar(UUID.randomUUID())).isFalse();
    }

    @Test void suspenderUnaCuentaNoTocaALasDemas() {
        UUID a = cuenta("ana@negocio.pe");
        UUID b = cuenta("luis@otro.pe");

        repo.suspender(a, T0);

        assertThat(repo.cuentaSuspendida(a)).isTrue();
        assertThat(repo.cuentaSuspendida(b)).isFalse();
        assertThat(repo.reactivar(b)).as("reactivar la otra no la afecta").isFalse();
        assertThat(repo.cuentaSuspendida(a)).isTrue();
    }

    // --- empresas -------------------------------------------------------------------------------------------------------------------

    @Test void todasLasEmpresasDeUnaCuentaSuspendidaEstanSuspendidas() {
        UUID c = cuenta("ana@negocio.pe");
        UUID e1 = empresa(c, "20100066603");
        UUID e2 = empresa(c, "20100066611");
        repo.suspender(c, T0);

        assertThat(repo.empresaSuspendida(e1)).isTrue();
        assertThat(repo.empresaSuspendida(e2)).isTrue();
    }

    @Test void lasEmpresasDeOtraCuentaNoSeVenAfectadas() {
        UUID a = cuenta("ana@negocio.pe");
        UUID b = cuenta("luis@otro.pe");
        UUID deB = empresa(b, "20100066611");
        empresa(a, "20100066603");
        repo.suspender(a, T0);

        assertThat(repo.empresaSuspendida(deB)).isFalse();
    }

    @Test void reactivarDevuelveLasEmpresas() {
        UUID c = cuenta("ana@negocio.pe");
        UUID e = empresa(c, "20100066603");
        repo.suspender(c, T0);
        repo.reactivar(c);

        assertThat(repo.empresaSuspendida(e)).isFalse();
    }

    /** Las empresas dadas de alta por una integración no tienen cuenta: no hay a quién suspender, así que nunca lo están. */
    @Test void unaEmpresaSinCuentaNuncaEstaSuspendida() {
        UUID e = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno) VALUES (?, '20100066603', 'INTEGRADOR SAC', 'BETA')", e);
        cuenta("ana@negocio.pe");

        assertThat(repo.empresaSuspendida(e)).isFalse();
    }

    @Test void unaEmpresaQueNoExisteNoEstaSuspendida() {
        assertThat(repo.empresaSuspendida(UUID.randomUUID())).isFalse();
    }
}
