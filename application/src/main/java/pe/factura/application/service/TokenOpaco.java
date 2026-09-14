package pe.factura.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Tokens aleatorios (refresh, recuperación) y su hash SHA-256 para persistir. */
public final class TokenOpaco {
    private static final SecureRandom RANDOM = new SecureRandom();
    private TokenOpaco() {}
    public static String generar() {
        byte[] b = new byte[32]; RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
