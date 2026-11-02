package pe.factura.adapters.persistence;

import org.junit.jupiter.api.Test;
import pe.factura.domain.tenant.ApiKey;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcApiKeyRepositoryTest extends PersistenciaTestBase {
    JdbcApiKeyRepository repo = new JdbcApiKeyRepository(jdbc);

    @Test void listaPorTenantMasRecientePrimeroYRevoca() {
        UUID tenant = tenantDePrueba(), otro = tenantDePrueba();
        Instant t1 = Instant.parse("2026-09-15T10:00:00Z"), t2 = t1.plus(1, ChronoUnit.HOURS);
        ApiKey vieja = new ApiKey(UUID.randomUUID(), tenant, "hash-1", "fk_vieja", true, t1, null);
        ApiKey nueva = new ApiKey(UUID.randomUUID(), tenant, "hash-2", "fk_nueva", true, t2, null);
        repo.guardar(vieja);
        repo.guardar(nueva);
        repo.guardar(new ApiKey(UUID.randomUUID(), otro, "hash-3", "fk_ajena", true, t1, null));

        assertThat(repo.listarPorTenant(tenant)).extracting(ApiKey::prefijo).containsExactly("fk_nueva", "fk_vieja");
        assertThat(repo.listarPorTenant(tenant)).allSatisfy(k -> assertThat(k.revocadaEn()).isNull());
        assertThat(repo.buscar(vieja.id())).get().extracting(ApiKey::creadaEn).isEqualTo(t1);
        assertThat(repo.buscar(UUID.randomUUID())).isEmpty();

        repo.guardar(vieja.revocar(t2));
        ApiKey r = repo.buscar(vieja.id()).orElseThrow();
        assertThat(r.activa()).isFalse();
        assertThat(r.revocadaEn()).isEqualTo(t2);
        assertThat(repo.buscarPorHash("hash-1")).get().extracting(ApiKey::activa).isEqualTo(false);
    }

    // --- S2: rotar API_KEY_PEPPER ------------------------------------------------------------------------------------------------

    String huella(String hash) { return jdbc.queryForObject("SELECT pepper_huella FROM api_key WHERE key_hash = ?", String.class, hash); }

    @Test void unaKeyNuevaSeGuardaConLaHuellaDelPepperVigente() {
        var conHuella = new JdbcApiKeyRepository(jdbc, "huella-nueva");
        conHuella.guardar(new ApiKey(UUID.randomUUID(), tenantDePrueba(), "h-nueva", "fk_nueva", true, null, null));
        assertThat(huella("h-nueva")).isEqualTo("huella-nueva");
    }

    @Test void rehashearCambiaElHashYLaHuellaSoloSiNadieLoCambioAntes() {
        UUID tenant = tenantDePrueba();
        new JdbcApiKeyRepository(jdbc, "huella-vieja").guardar(new ApiKey(UUID.randomUUID(), tenant, "h-vieja", "fk_vieja", true, null, null));
        ApiKey k = repo.buscarPorHash("h-vieja").orElseThrow();
        var rotando = new JdbcApiKeyRepository(jdbc, "huella-nueva");

        assertThat(rotando.rehashear(k.id(), "h-vieja", "h-nueva")).isTrue();
        assertThat(repo.buscarPorHash("h-vieja")).isEmpty();
        assertThat(repo.buscarPorHash("h-nueva")).get().extracting(ApiKey::id).isEqualTo(k.id());
        assertThat(huella("h-nueva")).isEqualTo("huella-nueva");
        assertThat(rotando.rehashear(k.id(), "h-vieja", "h-otra")).as("ya no tiene el hash viejo").isFalse();
    }

    @Test void completaLasHuellasQueFaltanYCuentaLasActivasConOtra() {
        UUID tenant = tenantDePrueba();
        repo.guardar(new ApiKey(UUID.randomUUID(), tenant, "h-sin-1", "fk_sin1", true, null, null));
        repo.guardar(new ApiKey(UUID.randomUUID(), tenant, "h-sin-2", "fk_sin2", false, null, null));
        new JdbcApiKeyRepository(jdbc, "huella-nueva").guardar(new ApiKey(UUID.randomUUID(), tenant, "h-con", "fk_con", true, null, null));
        var rotando = new JdbcApiKeyRepository(jdbc, "huella-nueva");

        assertThat(rotando.completarHuellas("huella-vieja")).isEqualTo(2);
        assertThat(rotando.completarHuellas("huella-vieja")).as("idempotente").isZero();
        assertThat(rotando.activasConOtraHuella("huella-nueva")).as("la revocada no cuenta").isEqualTo(1);
    }
}
