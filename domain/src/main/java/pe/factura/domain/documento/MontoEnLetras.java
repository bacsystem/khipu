package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MontoEnLetras {
    private MontoEnLetras() {}

    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
            "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO", "VEINTISEIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
            "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    public static String de(BigDecimal monto, String moneda) {
        BigDecimal m = monto.setScale(2, RoundingMode.HALF_UP);
        long entero = m.longValue();
        int centavos = m.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String nombreMoneda = switch (moneda) { case "USD" -> "DOLARES AMERICANOS"; case "EUR" -> "EUROS"; default -> "SOLES"; };
        return numero(entero) + " CON " + String.format("%02d", centavos) + "/100 " + nombreMoneda;
    }

    static String numero(long n) {
        if (n == 0) return "CERO";
        StringBuilder sb = new StringBuilder();
        long millones = n / 1_000_000, miles = (n % 1_000_000) / 1000, resto = n % 1000;
        if (millones == 1) sb.append("UN MILLON ");
        else if (millones > 1) sb.append(cientos(millones)).append(" MILLONES ");
        if (miles == 1) sb.append("MIL ");
        else if (miles > 1) sb.append(cientos(miles)).append(" MIL ");
        if (resto > 0) sb.append(cientos(resto));
        return sb.toString().trim();
    }

    private static String cientos(long n) {
        if (n == 100) return "CIEN";
        int c = (int) (n / 100), d = (int) (n % 100);
        String s = CENTENAS[c];
        if (d > 0) {
            String parte = d < 30 ? UNIDADES[d] : DECENAS[d / 10] + (d % 10 > 0 ? " Y " + UNIDADES[d % 10] : "");
            s = s.isEmpty() ? parte : s + " " + parte;
        }
        return s;
    }
}
