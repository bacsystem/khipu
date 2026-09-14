package pe.factura.adapters.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptPasswordHasherTest {
    BcryptPasswordHasher hasher = new BcryptPasswordHasher();

    @Test void hashYVerificacion() {
        String hash = hasher.hash("Segura123");
        assertThat(hash).isNotEqualTo("Segura123").startsWith("$2");
        assertThat(hasher.coincide("Segura123", hash)).isTrue();
        assertThat(hasher.coincide("Otra1234", hash)).isFalse();
    }

    @Test void saltDistintoEnCadaHash() {
        assertThat(hasher.hash("x")).isNotEqualTo(hasher.hash("x"));
    }

    @Test void hashMalformadoNuncaCoincide() {
        assertThat(hasher.coincide("cualquiera", "no-es-un-hash")).isFalse();
        assertThat(hasher.coincide("cualquiera", "")).isFalse();
        assertThat(hasher.coincide("cualquiera", null)).isFalse();
        assertThat(hasher.coincide(null, "$2a$12$abc")).isFalse();
    }
}
