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

    // --- S2: rotar MASTER_KEY sin perder lo guardado ----------------------------------------------------------------------------

    static String clave(int primerByte) {
        byte[] k = new byte[32]; k[0] = (byte) primerByte;
        return Base64.getEncoder().encodeToString(k);
    }

    @Test void conLaAnteriorConfiguradaDescifraLoViejoYCifraConLaNueva() {
        byte[] viejo = new AesGcmSecretCipher(clave(1)).cifrar("clave-sol".getBytes());
        AesGcmSecretCipher rotando = new AesGcmSecretCipher(clave(2), clave(1));

        assertThat(new String(rotando.descifrar(viejo))).isEqualTo("clave-sol");
        byte[] nuevo = rotando.cifrar("clave-sol".getBytes());
        assertThat(new String(new AesGcmSecretCipher(clave(2)).descifrar(nuevo))).as("lo nuevo sale con la clave nueva").isEqualTo("clave-sol");
        assertThatThrownBy(() -> new AesGcmSecretCipher(clave(1)).descifrar(nuevo)).isInstanceOf(IllegalStateException.class);
    }

    @Test void diceQueHayQueRecifrarSoloLoQueEstaConLaAnterior() {
        AesGcmSecretCipher rotando = new AesGcmSecretCipher(clave(2), clave(1));
        assertThat(rotando.necesitaRecifrar(new AesGcmSecretCipher(clave(1)).cifrar("x".getBytes()))).isTrue();
        assertThat(rotando.necesitaRecifrar(rotando.cifrar("x".getBytes()))).isFalse();
        assertThat(new AesGcmSecretCipher(clave(1)).necesitaRecifrar(new AesGcmSecretCipher(clave(1)).cifrar("x".getBytes())))
                .as("sin anterior no hay nada que recifrar").isFalse();
    }

    @Test void loCifradoConUnaTerceraClaveNoSeDescifraNiSeRecifra() {
        byte[] ajeno = new AesGcmSecretCipher(clave(3)).cifrar("x".getBytes());
        AesGcmSecretCipher rotando = new AesGcmSecretCipher(clave(2), clave(1));
        assertThatThrownBy(() -> rotando.descifrar(ajeno)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> rotando.necesitaRecifrar(ajeno)).isInstanceOf(IllegalStateException.class);
    }

    /** Copiar la misma clave en las dos variables no rota nada: es un error de configuración, y se dice al arrancar. */
    @Test void laAnteriorIgualALaNuevaSeRechaza() {
        assertThatThrownBy(() -> new AesGcmSecretCipher(clave(1), clave(1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MASTER_KEY_ANTERIOR");
        assertThatThrownBy(() -> new AesGcmSecretCipher(clave(1), Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MASTER_KEY_ANTERIOR");
    }

    @Test void unaAnteriorVaciaEsLoMismoQueNoTenerla() {
        AesGcmSecretCipher c = new AesGcmSecretCipher(clave(1), " ");
        assertThat(c.rotando()).isFalse();
        assertThat(new String(c.descifrar(c.cifrar("x".getBytes())))).isEqualTo("x");
    }
}
