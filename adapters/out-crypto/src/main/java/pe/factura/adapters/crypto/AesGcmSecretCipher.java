package pe.factura.adapters.crypto;

import pe.factura.application.port.out.SecretCipher;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class AesGcmSecretCipher implements SecretCipher {
    private static final int IV = 12, TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public AesGcmSecretCipher(String masterKeyBase64) {
        byte[] k = Base64.getDecoder().decode(masterKeyBase64);
        if (k.length != 32) throw new IllegalArgumentException("MASTER_KEY debe ser 32 bytes en base64");
        this.key = new SecretKeySpec(k, "AES");
    }

    @Override public byte[] cifrar(byte[] plano) {
        try {
            byte[] iv = new byte[IV]; RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plano);
            byte[] out = new byte[IV + ct.length];
            System.arraycopy(iv, 0, out, 0, IV); System.arraycopy(ct, 0, out, IV, ct.length);
            return out;
        } catch (Exception e) { throw new IllegalStateException("Error cifrando", e); }
    }

    @Override public byte[] descifrar(byte[] cifrado) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(cifrado, 0, IV)));
            return c.doFinal(Arrays.copyOfRange(cifrado, IV, cifrado.length));
        } catch (Exception e) { throw new IllegalStateException("Error descifrando (clave incorrecta o datos alterados)", e); }
    }
}
