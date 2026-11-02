package pe.factura.application.port.out;

import java.time.Instant;
import java.util.OptionalLong;

/** Códigos de un solo uso por tiempo (TOTP, RFC 6238) del segundo factor del administrador (#177). */
public interface SegundoFactor {
    /** Secreto nuevo en Base32, el formato que aceptan las apps de autenticación. */
    String nuevoSecreto();

    /** URI {@code otpauth://} que la app de autenticación lee del QR. */
    String uri(String secreto, String cuenta);

    /**
     * El paso de tiempo (ventanas de 30 s) en el que {@code codigo} es válido para {@code secreto}, tolerando un paso de reloj
     * hacia cada lado; vacío si no coincide. Devolver el paso, no un booleano, deja que quien llama impida reusar un código.
     */
    OptionalLong paso(String secreto, String codigo, Instant ahora);
}
