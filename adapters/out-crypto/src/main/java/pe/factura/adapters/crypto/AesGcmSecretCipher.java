package pe.factura.adapters.crypto;

import pe.factura.application.port.out.SecretCipher;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM con la MASTER_KEY. Para rotarla (S2) se configura la nueva en MASTER_KEY y la de antes en MASTER_KEY_ANTERIOR: todo se cifra con la
 * nueva, y lo que no se descifra con ella se intenta con la anterior. GCM verifica la integridad (la etiqueta), así que un dato nunca «se descifra»
 * con la clave equivocada: o falla la etiqueta o es la clave con la que se cifró.
 */
public class AesGcmSecretCipher implements SecretCipher {
    private static final int IV = 12, TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;
    private final SecretKeySpec anterior;

    public AesGcmSecretCipher(String masterKeyBase64) {
        this(masterKeyBase64, null);
    }

    /** {@code anteriorBase64} vacío o nulo: no se está rotando. */
    public AesGcmSecretCipher(String masterKeyBase64, String anteriorBase64) {
        this.key = clave(masterKeyBase64, "MASTER_KEY");
        if (anteriorBase64 == null || anteriorBase64.isBlank()) {
            this.anterior = null;
        } else {
            this.anterior = clave(anteriorBase64.strip(), "MASTER_KEY_ANTERIOR");
            if (MessageDigest.isEqual(key.getEncoded(), anterior.getEncoded()))
                throw new IllegalArgumentException("MASTER_KEY_ANTERIOR es igual a MASTER_KEY: así no se rota nada");
        }
    }

    private static SecretKeySpec clave(String base64, String variable) {
        byte[] k = Base64.getDecoder().decode(base64);
        if (k.length != 32) throw new IllegalArgumentException(variable + " debe ser 32 bytes en base64");
        return new SecretKeySpec(k, "AES");
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
            return con(key, cifrado);
        } catch (AEADBadTagException e) {
            if (anterior == null) throw new IllegalStateException("Error descifrando (clave incorrecta o datos alterados)", e);
            try {
                return con(anterior, cifrado);
            } catch (Exception e2) { throw new IllegalStateException("Error descifrando (ni MASTER_KEY ni MASTER_KEY_ANTERIOR, o datos alterados)", e2); }
        } catch (Exception e) { throw new IllegalStateException("Error descifrando (clave incorrecta o datos alterados)", e); }
    }

    @Override public boolean necesitaRecifrar(byte[] cifrado) {
        if (anterior == null) return false;
        try {
            con(key, cifrado);
            return false;
        } catch (AEADBadTagException e) {
            descifrar(cifrado);   // lanza si tampoco es de la anterior: un dato que no se puede leer no se «recifra»
            return true;
        } catch (Exception e) { throw new IllegalStateException("Error descifrando (clave incorrecta o datos alterados)", e); }
    }

    @Override public boolean rotando() { return anterior != null; }

    private static byte[] con(SecretKeySpec k, byte[] cifrado) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(cifrado, 0, IV)));
        return c.doFinal(Arrays.copyOfRange(cifrado, IV, cifrado.length));
    }
}
