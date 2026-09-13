package pe.factura.adapters.crypto;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class AesGcmSecretCipherTest {
    String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test void cifraYDescifra() {
        AesGcmSecretCipher c = new AesGcmSecretCipher(key);
        byte[] cifrado = c.cifrar("moddatos".getBytes());
        assertThat(cifrado).hasSizeGreaterThan(12 + 8);
        assertThat(new String(c.descifrar(cifrado))).isEqualTo("moddatos");
    }
    @Test void ivAleatorioProduceCifradosDistintos() {
        AesGcmSecretCipher c = new AesGcmSecretCipher(key);
        assertThat(c.cifrar("x".getBytes())).isNotEqualTo(c.cifrar("x".getBytes()));
    }
    @Test void claveIncorrectaFalla() {
        byte[] otra = new byte[32]; otra[0] = 1;
        byte[] cifrado = new AesGcmSecretCipher(key).cifrar("x".getBytes());
        assertThatThrownBy(() -> new AesGcmSecretCipher(Base64.getEncoder().encodeToString(otra)).descifrar(cifrado))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void claveDebeTener32Bytes() {
        assertThatThrownBy(() -> new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
