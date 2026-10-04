package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.AccionesDeEmpresaRepository.ApiKeyDeEmpresa;
import pe.factura.domain.tenant.Entorno;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acciones del administrador sobre una empresa (#187), con Postgres real. Lo que importa: los cambios son atómicos y condicionales, tocan una sola columna
 * (no pisan el certificado ni las credenciales SOL cifrados) y ninguna empresa afecta a otra.
 */
class JdbcAccionesDeEmpresaRepositoryTest extends PersistenciaTestBase {
    static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");

    JdbcAccionesDeEmpresaRepository repo = new JdbcAccionesDeEmpresaRepository(jdbc);

    UUID empresa(String ruc, String entorno) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno) VALUES (?, ?, 'EMPRESA SAC', ?)", id, ruc, entorno);
        return id;
    }

    UUID key(UUID empresa, String prefijo, boolean activa) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo, activa) VALUES (?, ?, ?, ?, ?)", id, empresa, UUID.randomUUID().toString().replace("-", "") + "0".repeat(32), prefijo, activa);
        return id;
    }

    void pendiente(UUID empresa) {
        jdbc.update("INSERT INTO outbox (tenant_id, agregado_id, accion, siguiente_intento) VALUES (?, gen_random_uuid(), 'ENVIAR', ?)", empresa, Timestamp.from(T0));
    }

    // --- entorno ----------------------------------------------------------------------------------------------------------------------

    @Test void elEntornoSeLeeYUnaEmpresaQueNoExisteNoTiene() {
        UUID e = empresa("20100066603", "BETA");

        assertThat(repo.entornoDe(e)).contains(Entorno.BETA);
        assertThat(repo.entornoDe(UUID.randomUUID())).isEmpty();
    }

    @Test void cambiarElEntornoSoloSiSeguiaSiendoElQueSeVio() {
        UUID e = empresa("20100066603", "BETA");

        assertThat(repo.cambiarEntorno(e, Entorno.BETA, Entorno.PRODUCCION)).isTrue();
        assertThat(repo.entornoDe(e)).contains(Entorno.PRODUCCION);
        assertThat(repo.cambiarEntorno(e, Entorno.BETA, Entorno.PRODUCCION)).as("ya no estaba en BETA: no cambia nada").isFalse();
        assertThat(repo.cambiarEntorno(e, Entorno.PRODUCCION, Entorno.BETA)).isTrue();
        assertThat(repo.entornoDe(e)).contains(Entorno.BETA);
    }

    @Test void cambiarElEntornoDeUnaEmpresaQueNoExisteNoCambiaNada() {
        assertThat(repo.cambiarEntorno(UUID.randomUUID(), Entorno.BETA, Entorno.PRODUCCION)).isFalse();
    }

    /** El cambio de entorno decide a qué URL de SUNAT se emite: no puede llevarse puesto el certificado, las credenciales SOL ni nada más de la empresa. */
    @Test void cambiarElEntornoNoTocaNingunaOtraColumna() {
        UUID e = empresa("20100066603", "BETA");
        jdbc.update("UPDATE tenant SET sol_usuario_enc = ?, sol_clave_enc = ?, cert_pkcs12_enc = ?, cert_clave_enc = ?, nombre_comercial = 'ANDINA' WHERE id = ?",
                new byte[]{1}, new byte[]{2}, new byte[]{3}, new byte[]{4}, e);

        repo.cambiarEntorno(e, Entorno.BETA, Entorno.PRODUCCION);

        assertThat(jdbc.queryForObject("SELECT sol_usuario_enc IS NOT NULL AND sol_clave_enc IS NOT NULL AND cert_pkcs12_enc IS NOT NULL AND cert_clave_enc IS NOT NULL FROM tenant WHERE id = ?", Boolean.class, e)).isTrue();
        assertThat(jdbc.queryForObject("SELECT razon_social || '/' || nombre_comercial FROM tenant WHERE id = ?", String.class, e)).isEqualTo("EMPRESA SAC/ANDINA");
    }

    @Test void cambiarElEntornoDeUnaEmpresaNoTocaALasDemas() {
        UUID a = empresa("20100066603", "BETA");
        UUID b = empresa("20100066611", "BETA");

        repo.cambiarEntorno(a, Entorno.BETA, Entorno.PRODUCCION);

        assertThat(repo.entornoDe(b)).contains(Entorno.BETA);
    }

    // --- envíos pendientes ------------------------------------------------------------------------------------------------------------

    @Test void cuentaLasTareasPendientesDeLaEmpresaYNoLasDeOtra() {
        UUID a = empresa("20100066603", "BETA");
        UUID b = empresa("20100066611", "BETA");
        pendiente(a);
        pendiente(a);
        pendiente(b);

        assertThat(repo.enviosPendientes(a)).isEqualTo(2);
        assertThat(repo.enviosPendientes(b)).isEqualTo(1);
        assertThat(repo.enviosPendientes(UUID.randomUUID())).isZero();
    }

    // --- API keys ---------------------------------------------------------------------------------------------------------------------

    @Test void laKeyDeUnaEmpresaSeLeeConSuPrefijoYSuEstado() {
        UUID e = empresa("20100066603", "BETA");
        UUID activa = key(e, "fk_activ01", true);
        UUID revocada = key(e, "fk_viej002", false);

        assertThat(repo.apiKey(e, activa)).contains(new ApiKeyDeEmpresa("fk_activ01", true));
        assertThat(repo.apiKey(e, revocada)).contains(new ApiKeyDeEmpresa("fk_viej002", false));
    }

    @Test void unaKeyDeOtraEmpresaOInexistenteNoSeEncuentra() {
        UUID a = empresa("20100066603", "BETA");
        UUID b = empresa("20100066611", "BETA");
        UUID deB = key(b, "fk_deb0001", true);

        assertThat(repo.apiKey(a, deB)).isEmpty();
        assertThat(repo.apiKey(a, UUID.randomUUID())).isEmpty();
    }

    @Test void revocarLaDejaInactivaConLaHoraDeLaRevocacion() {
        UUID e = empresa("20100066603", "BETA");
        UUID k = key(e, "fk_activ01", true);

        assertThat(repo.revocarApiKey(e, k, T0.plusSeconds(60))).isTrue();

        assertThat(jdbc.queryForObject("SELECT activa FROM api_key WHERE id = ?", Boolean.class, k)).isFalse();
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM api_key WHERE id = ?", Timestamp.class, k).toInstant()).isEqualTo(T0.plusSeconds(60));
    }

    /** Condicional: de dos pedidos a la vez uno solo lo logra, y el segundo no pisa la hora de la primera revocación. */
    @Test void revocarDosVecesNoCambiaNadaLaSegunda() {
        UUID e = empresa("20100066603", "BETA");
        UUID k = key(e, "fk_activ01", true);

        assertThat(repo.revocarApiKey(e, k, T0.plusSeconds(60))).isTrue();
        assertThat(repo.revocarApiKey(e, k, T0.plusSeconds(999))).isFalse();

        assertThat(jdbc.queryForObject("SELECT revoked_at FROM api_key WHERE id = ?", Timestamp.class, k).toInstant()).isEqualTo(T0.plusSeconds(60));
    }

    @Test void noSeRevocaUnaKeyDeOtraEmpresaAunqueSeConozcaSuId() {
        UUID a = empresa("20100066603", "BETA");
        UUID b = empresa("20100066611", "BETA");
        UUID deB = key(b, "fk_deb0001", true);

        assertThat(repo.revocarApiKey(a, deB, T0)).isFalse();

        assertThat(jdbc.queryForObject("SELECT activa FROM api_key WHERE id = ?", Boolean.class, deB)).isTrue();
    }

    @Test void revocarUnaKeyNoTocaLasOtrasDeLaMismaEmpresa() {
        UUID e = empresa("20100066603", "BETA");
        UUID una = key(e, "fk_una0001", true);
        UUID otra = key(e, "fk_otra002", true);

        repo.revocarApiKey(e, una, T0);

        assertThat(jdbc.queryForObject("SELECT activa FROM api_key WHERE id = ?", Boolean.class, otra)).isTrue();
    }
}
