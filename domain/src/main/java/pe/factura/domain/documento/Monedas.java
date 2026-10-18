package pe.factura.domain.documento;

import java.util.Map;

/**
 * Monedas del catálogo 02 (ISO 4217 alpha) que khipu emite (#316). SUNAT acepta el código ISO (3088) pero su lista no está publicada con fecha y los códigos
 * creados después de 2001 pueden no estar en ella, así que khipu admite solo las monedas con las que comercia el Perú y de las que sabe escribir el monto en letras
 * (leyenda 1000). Cada una se probó contra e-beta en {@code HomologacionBetaTest}.
 */
public final class Monedas {
    public static final String SOLES = "PEN";

    /** Nombre de la moneda en el monto en letras de la leyenda 1000 («SON … CON 00/100 DOLARES AMERICANOS»). */
    private static final Map<String, String> EN_LETRAS = Map.ofEntries(
            Map.entry("PEN", "SOLES"),
            Map.entry("USD", "DOLARES AMERICANOS"),
            Map.entry("EUR", "EUROS"),
            Map.entry("GBP", "LIBRAS ESTERLINAS"),
            Map.entry("CAD", "DOLARES CANADIENSES"),
            Map.entry("AUD", "DOLARES AUSTRALIANOS"),
            Map.entry("CHF", "FRANCOS SUIZOS"),
            Map.entry("JPY", "YENES"),
            Map.entry("CNY", "YUANES"),
            Map.entry("MXN", "PESOS MEXICANOS"),
            Map.entry("BRL", "REALES"),
            Map.entry("CLP", "PESOS CHILENOS"),
            Map.entry("COP", "PESOS COLOMBIANOS"),
            Map.entry("ARS", "PESOS ARGENTINOS"),
            Map.entry("BOB", "BOLIVIANOS"),
            Map.entry("UYU", "PESOS URUGUAYOS"),
            Map.entry("PYG", "GUARANIES"));

    private Monedas() {}

    public static boolean admitida(String moneda) { return moneda != null && EN_LETRAS.containsKey(moneda); }

    /** Nombre en mayúsculas para el monto en letras; soles para una moneda que khipu no conoce (no debería llegar: se rechaza al emitir). */
    public static String nombreEnLetras(String moneda) { return EN_LETRAS.getOrDefault(moneda, "SOLES"); }

    /** Códigos admitidos, en orden alfabético (para mensajes y documentación). */
    public static java.util.List<String> codigos() { return EN_LETRAS.keySet().stream().sorted().toList(); }
}
