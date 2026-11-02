package pe.factura.domain.plan;

import java.util.OptionalInt;

/**
 * Cuánto de su tope de documentos al mes lleva usado una cuenta, y desde cuándo hay que avisarle (#193). Es la única definición: la tabla de consumo, su filtro
 * «cerca del límite» y el CSV dicen lo mismo. El porcentaje se redondea **hacia abajo** y la alerta es «el porcentaje llegó al umbral», así que nunca se
 * contradicen (79,9 % no avisa y se ve como 79 %).
 */
public final class UsoDeLimite {
    /** Desde este porcentaje inclusive la cuenta está «cerca del límite»: hay que avisarle antes de que choque contra él. */
    public static final int UMBRAL_DE_ALERTA = 80;

    private UsoDeLimite() {}

    /** El porcentaje del tope usado, hacia abajo; puede pasar de 100 si la cuenta ya se pasó. Vacío si el plan no tiene tope de documentos. */
    public static OptionalInt porcentaje(long documentos, Limite tope) {
        if (tope.ilimitado()) return OptionalInt.empty();
        long pct = Math.multiplyExact(documentos, 100L) / tope.maximo();
        return OptionalInt.of((int) Math.min(pct, Integer.MAX_VALUE));
    }

    /** Si ya está en el umbral de alerta (o más). Un plan sin tope nunca alerta. */
    public static boolean enAlerta(long documentos, Limite tope) {
        return porcentaje(documentos, tope).orElse(0) >= UMBRAL_DE_ALERTA;
    }
}
