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
}
