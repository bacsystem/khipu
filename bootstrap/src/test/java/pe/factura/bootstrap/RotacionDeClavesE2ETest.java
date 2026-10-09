package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.factura.adapters.crypto.AesGcmSecretCipher;
import pe.factura.application.port.in.RevisarRotacionDeClavesUseCase;
import pe.factura.application.service.ApiKeyGenerator;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S2: rotar MASTER_KEY y API_KEY_PEPPER con el backend real. Lo guardado con la clave vieja se recifra con la nueva, y una API key emitida con el
 * pepper viejo sigue autenticando y pasa al nuevo la primera vez que se usa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.master-key=" + RotacionDeClavesE2ETest.NUEVA,
        "app.master-key-anterior=" + RotacionDeClavesE2ETest.VIEJA,
        "app.api-key-pepper=pepper-nuevo",
        "app.api-key-pepper-anterior=pepper-viejo"})
@ActiveProfiles("test")
@Testcontainers
class RotacionDeClavesE2ETest {
    static final String VIEJA = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    static final String NUEVA = "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired RevisarRotacionDeClavesUseCase rotacion;

    @Test void loGuardadoConLaClaveViejaSeRecifraYLaKeyViejaSigueAutenticando() {
        // Una empresa con credenciales SOL cifradas con la MASTER_KEY de antes, y una API key de antes (pepper viejo, sin huella).
        AesGcmSecretCipher vieja = new AesGcmSecretCipher(VIEJA);
        UUID tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, ruc, razon_social, entorno, sol_usuario_enc, sol_clave_enc) VALUES (?, '20100066603', 'EMPRESA SAC', 'BETA', ?, ?)",
                tenant, vieja.cifrar(bytes("MODDATOS")), vieja.cifrar(bytes("moddatos")));
        String key = "fk_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO api_key (id, tenant_id, key_hash, prefijo, activa) VALUES (?, ?, ?, ?, true)",
                UUID.randomUUID(), tenant, ApiKeyGenerator.hash(key, "pepper-viejo"), ApiKeyGenerator.prefijo(key));

        var informe = rotacion.revisar();

        assertThat(informe.secretosRecifrados()).isEqualTo(2);
        assertThat(informe.secretosPendientes()).isZero();
        assertThat(informe.apiKeysConPepperAnterior()).as("la key de antes, hasta que se use").isEqualTo(1);
        // Con la clave nueva sola ya se leen.
        byte[] clave = jdbc.queryForObject("SELECT sol_clave_enc FROM tenant WHERE id = ?", byte[].class, tenant);
        assertThat(new String(new AesGcmSecretCipher(NUEVA).descifrar(clave), StandardCharsets.UTF_8)).isEqualTo("moddatos");

        // La key de antes autentica, y queda con el pepper nuevo.
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", key);
        ResponseEntity<Map> empresa = http.exchange("/v1/empresa", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(empresa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) empresa.getBody().get("datos")).get("tiene_credenciales_sol")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT key_hash FROM api_key WHERE tenant_id = ?", String.class, tenant)).isEqualTo(ApiKeyGenerator.hash(key, "pepper-nuevo"));
        assertThat(rotacion.revisar().apiKeysConPepperAnterior()).as("ya no depende del pepper viejo").isZero();
    }

    @Test void unaKeyQueNoEsDeNingunPepperNoAutentica() {
        HttpHeaders h = new HttpHeaders(); h.set("X-Api-Key", "fk_inventada");
        assertThat(http.exchange("/v1/empresa", HttpMethod.GET, new HttpEntity<>(h), Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    static { assert Base64.getDecoder().decode(VIEJA).length == 32 && Base64.getDecoder().decode(NUEVA).length == 32; }
}
