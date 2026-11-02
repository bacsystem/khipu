package pe.factura.domain.plataforma;

import pe.factura.domain.DomainException;

import java.time.Duration;
import java.time.Instant;

/**
 * El aviso de mantenimiento que ven todos los clientes en su portal (#199): un texto y el tramo en que se muestra. Solo hay uno a la vez. **Siempre termina**: sin fin, un banner
 * olvidado se quedaría para siempre, así que la vigencia es obligatoria y no puede pasar de {@link #MAX_DURACION}. El texto es plano y de una línea; el portal lo muestra como
 * texto, nunca como HTML.
 */
public record BannerDeMantenimiento(String texto, Instant desde, Instant hasta) {
    public static final int MAX_TEXTO = 300;
    public static final Duration MAX_DURACION = Duration.ofDays(90);

    /** Valida lo que se va a publicar a las {@code ahora}: un banner que ya venció no se publica. */
    public static BannerDeMantenimiento de(String texto, Instant desde, Instant hasta, Instant ahora) {
        String t = texto == null ? "" : texto.strip();
        if (t.isEmpty()) throw invalido("El texto del aviso no puede estar vacío");
        if (t.length() > MAX_TEXTO) throw invalido("El texto del aviso admite hasta " + MAX_TEXTO + " caracteres");
        if (t.chars().anyMatch(Character::isISOControl)) throw invalido("El texto del aviso va en una sola línea");
        if (desde == null || hasta == null) throw invalido("Indica desde cuándo y hasta cuándo se muestra");
        if (!hasta.isAfter(desde)) throw invalido("El aviso tiene que terminar después de empezar");
        if (!hasta.isAfter(ahora)) throw invalido("El aviso ya venció: elige un fin que todavía no haya pasado");
        if (Duration.between(desde, hasta).compareTo(MAX_DURACION) > 0) throw invalido("Un aviso puede durar hasta " + MAX_DURACION.toDays() + " días");
        return new BannerDeMantenimiento(t, desde, hasta);
    }

    /** Se muestra desde {@code desde} (inclusive) hasta {@code hasta} (exclusive). */
    public boolean vigenteEn(Instant instante) { return !instante.isBefore(desde) && instante.isBefore(hasta); }

    private static DomainException invalido(String mensaje) { return new DomainException("BANNER_INVALIDO", mensaje); }
}
