package pe.factura.domain.documento;

import java.math.BigDecimal;

/** Normalización y formato de los textos libres de la guía de remisión: sin espacios en los extremos, una sola línea (sin tabs ni saltos), largo acotado. */
final class TextoDeGuia {
    private TextoDeGuia() {}

    /** {@code null} si viene vacío; si no, sin espacios en los extremos. */
    static String limpio(String s) { return s == null || s.isBlank() ? null : s.strip(); }

    /** Texto en una sola línea de {@code min} a {@code max} caracteres; {@code null} no cumple. */
    static boolean unaLinea(String s, int min, int max) {
        return s != null && s.length() >= min && s.length() <= max && s.chars().noneMatch(Character::isISOControl);
    }

    /** Mayúsculas y sin guiones ni espacios: así escriben las placas y las licencias los formularios, y así las pide SUNAT. */
    static String compacto(String s) {
        String l = limpio(s);
        return l == null ? null : l.toUpperCase(java.util.Locale.ROOT).replace("-", "").replace(" ", "");
    }

    /** Un número positivo con hasta {@code enteros} enteros y {@code decimales} decimales. */
    static boolean positivo(BigDecimal n, int enteros, int decimales) {
        if (n == null || n.signum() <= 0) return false;
        BigDecimal s = n.stripTrailingZeros();
        int escala = Math.max(s.scale(), 0);
        return escala <= decimales && s.precision() - s.scale() <= enteros;
    }
}
