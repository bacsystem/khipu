package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Baja lógica de cuentas (#201), con Postgres real. Lo que importa: el cambio es atómico, no toca a ninguna otra cuenta y **no borra ni libera nada**:
 * la empresa y su RUC siguen ahí.
 */
class JdbcBajaDeCuentaRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");

    JdbcBajaDeCuentaRepository repo = new JdbcBajaDeCuentaRepository(jdbc);

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

    Timestamp bajaEn(UUID cuenta) { return jdbc.queryForObject("SELECT baja_en FROM cuenta WHERE id = ?", Timestamp.class, cuenta); }

    @Test void unaCuentaNuevaNoEstaDeBaja() {
        assertThat(bajaEn(cuenta("ana@negocio.pe"))).isNull();
    }

    /** El puerto dice desde cuándo está de baja la cuenta, y solo esa: nulo en servicio, nulo si no existe. */
    @Test void dicedesdeCuandoEstaDeBajaCadaCuenta() {
        UUID c = cuenta("ana@negocio.pe");
        UUID otra = cuenta("beto@negocio.pe");
        assertThat(repo.bajaEn(c)).isNull();

        repo.darDeBaja(c, T0.plusSeconds(60));

        assertThat(repo.bajaEn(c)).isEqualTo(T0.plusSeconds(60));
        assertThat(repo.bajaEn(otra)).isNull();
        assertThat(repo.bajaEn(UUID.randomUUID())).isNull();
    }

    @Test void darDeBajaLaMarcaConLaHoraYReponerLaDevuelve() {
        UUID c = cuenta("ana@negocio.pe");

        assertThat(repo.darDeBaja(c, T0.plusSeconds(60))).isTrue();
        assertThat(bajaEn(c).toInstant()).isEqualTo(T0.plusSeconds(60));

        assertThat(repo.reponer(c)).isTrue();
        assertThat(bajaEn(c)).isNull();
    }

    /** Condicional: de dos pedidos a la vez uno solo lo logra, y el segundo no pisa la hora del primero. */
    @Test void darDeBajaDosVecesNoCambiaNadaLaSegunda() {
        UUID c = cuenta("ana@negocio.pe");

        assertThat(repo.darDeBaja(c, T0.plusSeconds(60))).isTrue();
        assertThat(repo.darDeBaja(c, T0.plusSeconds(999))).isFalse();

        assertThat(bajaEn(c).toInstant()).isEqualTo(T0.plusSeconds(60));
    }

    @Test void reponerUnaCuentaQueNoEstaDeBajaNoCambiaNada() {
        UUID c = cuenta("ana@negocio.pe");

        assertThat(repo.reponer(c)).isFalse();
        assertThat(bajaEn(c)).isNull();
    }

    @Test void reponerDosVecesSoloLaPrimeraCambia() {
        UUID c = cuenta("ana@negocio.pe");
        repo.darDeBaja(c, T0);

        assertThat(repo.reponer(c)).isTrue();
        assertThat(repo.reponer(c)).isFalse();
    }

    @Test void unaCuentaQueNoExisteNoCambiaNada() {
        assertThat(repo.darDeBaja(UUID.randomUUID(), T0)).isFalse();
        assertThat(repo.reponer(UUID.randomUUID())).isFalse();
    }

    @Test void darDeBajaUnaCuentaNoTocaALasDemas() {
        UUID a = cuenta("ana@negocio.pe");
        UUID b = cuenta("luis@otro.pe");

        repo.darDeBaja(a, T0);

        assertThat(bajaEn(a)).isNotNull();
        assertThat(bajaEn(b)).isNull();
        assertThat(repo.reponer(b)).as("reponer la otra no la afecta").isFalse();
        assertThat(bajaEn(a)).isNotNull();
    }

    /** La baja y la suspensión son dos cosas: una no pisa a la otra. */
    @Test void laBajaYLaSuspensionSonIndependientes() {
        UUID c = cuenta("ana@negocio.pe");
        JdbcSuspensionRepository suspensiones = new JdbcSuspensionRepository(jdbc);
        suspensiones.suspender(c, T0);

        repo.darDeBaja(c, T0.plusSeconds(10));
        repo.reponer(c);

        assertThat(suspensiones.cuentaSuspendida(c)).as("reponer no reactiva una cuenta suspendida").isTrue();
        repo.darDeBaja(c, T0.plusSeconds(20));
        suspensiones.reactivar(c);
        assertThat(bajaEn(c)).as("reactivar no repone una cuenta de baja").isNotNull();
    }

    // --- lo que se conserva ---------------------------------------------------------------------------------------------------------

    @Test void laBajaNoBorraNiTocaLaEmpresaDeLaCuenta() {
        UUID c = cuenta("ana@negocio.pe");
        UUID e = empresa(c, "20100066603");

        repo.darDeBaja(c, T0);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenant WHERE id = ? AND cuenta_id = ?", Long.class, e, c)).isEqualTo(1L);
    }

    /** El RUC no se libera: la empresa sigue ahí, y la restricción de unicidad de la base sigue impidiendo registrarlo otra vez. */
    @Test void elRucDeUnaCuentaDeBajaSigueOcupado() {
        UUID c = cuenta("ana@negocio.pe");
        empresa(c, "20100066603");
        repo.darDeBaja(c, T0);
        UUID otra = cuenta("luis@otro.pe");

        assertThatThrownBy(() -> empresa(otra, "20100066603")).isInstanceOf(DuplicateKeyException.class);
    }
}
