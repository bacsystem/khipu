package pe.factura.application.service;

import org.junit.jupiter.api.Test;
import pe.factura.domain.tenant.ApiKey;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyGeneratorTest {
    /** El único lugar que decide cómo se guarda una API key nueva: lo usan el alta de una empresa y el alta asistida (#188). */
    @Test void unaKeyNuevaGuardaSoloElHashYElPrefijoActivaYSinRevocar() {
        UUID tenantId = UUID.randomUUID();
        Instant ahora = Instant.parse("2026-10-03T15:00:00Z");
        String enClaro = ApiKeyGenerator.generar();

        ApiKey k = ApiKeyGenerator.nueva(tenantId, enClaro, "pepper", ahora);

        assertThat(k.id()).isNotNull();
        assertThat(k.tenantId()).isEqualTo(tenantId);
        assertThat(k.hash()).isEqualTo(ApiKeyGenerator.hash(enClaro, "pepper")).isNotEqualTo(enClaro);
        assertThat(k.prefijo()).isEqualTo(ApiKeyGenerator.prefijo(enClaro));
        assertThat(k.activa()).isTrue();
        assertThat(k.creadaEn()).isEqualTo(ahora);
        assertThat(k.revocadaEn()).isNull();
    }

    @Test void elHashDependeDelPepper() {
        String enClaro = ApiKeyGenerator.generar();
        UUID tenantId = UUID.randomUUID();
        Instant ahora = Instant.now();

        assertThat(ApiKeyGenerator.nueva(tenantId, enClaro, "uno", ahora).hash()).isNotEqualTo(ApiKeyGenerator.nueva(tenantId, enClaro, "dos", ahora).hash());
    }
}
