package pe.factura.adapters.crypto;

import pe.factura.application.port.out.SegundoFactor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * TOTP de RFC 6238 con los parámetros que asumen todas las apps de autenticación: HMAC-SHA1, 6 dígitos y pasos de 30 s. Sin
 * dependencias: son unas pocas líneas sobre {@code javax.crypto}, y una librería más para esto no compensa.
 */
public class TotpRfc6238 implements SegundoFactor {
    static final String EMISOR = "khipu";
    private static final int PERIODO = 30;
    private static final int DIGITOS = 6;
    /** Un paso hacia cada lado: el reloj del teléfono puede ir unos segundos adelantado o atrasado. */
    private static final int VENTANA = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom AZAR = new SecureRandom();

    /** 160 bits, el tamaño de la clave de HMAC-SHA1 que recomienda el RFC 4226. */
    @Override public String nuevoSecreto() {
        byte[] b = new byte[20];
        AZAR.nextBytes(b);
        return base32(b);
    }

    @Override public String uri(String secreto, String cuenta) {
        return "otpauth://totp/" + EMISOR + ":" + URLEncoder.encode(cuenta, StandardCharsets.UTF_8).replace("+", "%20")
                + "?secret=" + secreto + "&issuer=" + EMISOR + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO;
    }

    @Override public OptionalLong paso(String secreto, String codigo, Instant ahora) {
        if (codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) return OptionalLong.empty();
        long actual = Math.floorDiv(ahora.getEpochSecond(), PERIODO);
        byte[] dado = codigo.getBytes(StandardCharsets.US_ASCII);
        for (long p = actual - VENTANA; p <= actual + VENTANA; p++)
            // Comparación de tiempo constante: no revela cuántos dígitos acertó.
            if (MessageDigest.isEqual(codigo(secreto, p).getBytes(StandardCharsets.US_ASCII), dado)) return OptionalLong.of(p);
        return OptionalLong.empty();
    }

    /**
     * RFC 4226, sección 5.3: HMAC del contador, truncamiento dinámico y los últimos {@value #DIGITOS} dígitos. Público porque es lo que
     * hace la app de autenticación: los tests de punta a punta lo usan para generar el código como lo haría el teléfono.
     */
    public String codigo(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(desdeBase32(secreto), "HmacSHA1"));
            byte[] h = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(paso).array());
            int o = h[h.length - 1] & 0x0f;
            int binario = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%0" + DIGITOS + "d", binario % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 no disponible", e);
        }
    }

    static String base32(byte[] datos) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) { sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31)); bits -= 5; }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    /** Las apps muestran el secreto en grupos y a veces en minúsculas: se ignoran espacios, guiones y el relleno {@code =}. */
    static byte[] desdeBase32(String texto) {
        String s = texto.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : s.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Secreto TOTP con un carácter fuera de Base32");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) { out.write((buffer >> (bits - 8)) & 0xff); bits -= 8; }
        }
        return out.toByteArray();
    }
}
