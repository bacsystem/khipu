package pe.factura.domain.sire;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Lee las filas de la propuesta del RVIE que el contribuyente descarga del SIRE (#322), para conciliarla con lo que khipu emitió. Supone la misma estructura de
 * 40 campos que escribe {@link FormatoRvie} (separador {@value FormatoRvie#SEPARADOR}, fechas {@code dd/MM/yyyy}, importes con punto decimal) y tolera lo que
 * no cambia el contenido: una fila de títulos al principio, líneas en blanco, el separador final presente o ausente, fin de línea LF o CRLF. Lee solo los campos
 * que se comparan ({@link ImportesRvie}); un campo que no se puede leer es un error con su línea y su número de campo, no una fila que se salta en silencio.
 */
public final class PropuestaRvie {
    /** Hasta la moneda (campo 27): una fila más corta no trae lo que se compara. El estado (35) es opcional. */
    static final int CAMPOS_MINIMOS = 27;
    private static final Pattern RUC = Pattern.compile("\\d{11}");
    private static final Pattern IMPORTE = Pattern.compile("-?\\d{1,12}(\\.\\d{1,2})?");
    private static final Pattern NUMERO = Pattern.compile("\\d{1,10}");
    private static final Pattern SERIE = Pattern.compile("[A-Z0-9]{4}");

    private final String rucEmpresa;
    private final PeriodoSire periodo;

    public PropuestaRvie(String rucEmpresa, PeriodoSire periodo) { this.rucEmpresa = rucEmpresa; this.periodo = periodo; }

    /**
     * La fila {@code linea} (numerada desde 1, para los mensajes), o nula si es una línea en blanco o la fila de títulos de la línea 1 (la que no empieza con un RUC).
     */
    public ImportesRvie leer(String linea, long numeroDeLinea) {
        String l = linea.endsWith("\r") ? linea.substring(0, linea.length() - 1) : linea;
        if (numeroDeLinea == 1 && !l.isEmpty() && l.charAt(0) == '﻿') l = l.substring(1);
        if (l.isBlank()) return null;
        String[] f = l.split(Pattern.quote(String.valueOf(FormatoRvie.SEPARADOR)), -1);
        if (numeroDeLinea == 1 && !RUC.matcher(f[0].strip()).matches()) return null;
        if (f.length < CAMPOS_MINIMOS)
            throw error(numeroDeLinea, "la fila tiene " + f.length + " campos separados por «" + FormatoRvie.SEPARADOR + "» y el registro de ventas trae " + FormatoRvie.CAMPOS
                    + " (al menos " + CAMPOS_MINIMOS + ", hasta la moneda)");
        String ruc = f[0].strip();
        if (!RUC.matcher(ruc).matches()) throw error(numeroDeLinea, campo(1, "RUC") + "no es un RUC: «" + corto(ruc) + "»");
        if (!ruc.equals(rucEmpresa)) throw error(numeroDeLinea, "la fila es del RUC " + ruc + ", no del de esta empresa (" + rucEmpresa + ")");
        String per = f[2].strip();
        if (!per.equals(periodo.aaaamm())) throw error(numeroDeLinea, campo(3, "periodo") + "es «" + corto(per) + "» y se concilia el " + periodo.aaaamm());
        String tipo = f[6].strip();
        if (!tipo.matches("\\d{2}")) throw error(numeroDeLinea, campo(7, "tipo de comprobante") + "no es un código de 2 dígitos: «" + corto(tipo) + "»");
        String serie = f[7].strip().toUpperCase(java.util.Locale.ROOT);
        if (!SERIE.matcher(serie).matches()) throw error(numeroDeLinea, campo(8, "serie") + "no es una serie de 4 caracteres: «" + corto(serie) + "»");
        String num = f[8].strip();
        if (!NUMERO.matcher(num).matches()) throw error(numeroDeLinea, campo(9, "número") + "no es un número: «" + corto(num) + "»");
        String estado = f.length > 34 ? f[34].strip() : "";
        return new ImportesRvie(tipo, serie, Long.parseLong(num), fecha(f[4], 5, numeroDeLinea), f[26].strip(), estado,
                importe(f[14], 15, "base gravada", numeroDeLinea), importe(f[16], 17, "IGV", numeroDeLinea), importe(f[18], 19, "exonerado", numeroDeLinea),
                importe(f[19], 20, "inafecto", numeroDeLinea), importe(f[25], 26, "importe total", numeroDeLinea));
    }

    private static LocalDate fecha(String s, int n, long linea) {
        String t = s.strip();
        try {
            return LocalDate.parse(t, FormatoRvie.FECHA);
        } catch (DateTimeParseException e) {
            throw error(linea, campo(n, "fecha de emisión") + "no es una fecha dd/mm/aaaa: «" + corto(t) + "»");
        }
    }

    private static BigDecimal importe(String s, int n, String nombre, long linea) {
        String t = s.strip();
        if (t.isEmpty()) return BigDecimal.ZERO.setScale(2);
        if (!IMPORTE.matcher(t).matches()) throw error(linea, campo(n, nombre) + "no es un importe con punto decimal y hasta 2 decimales: «" + corto(t) + "»");
        return new BigDecimal(t);
    }

    private static String campo(int n, String nombre) { return "el campo " + n + " (" + nombre + ") "; }

    private static String corto(String s) { return s.length() > 40 ? s.substring(0, 40) + "…" : s; }

    private static DomainException error(long linea, String que) {
        return new DomainException("RVIE_ARCHIVO_INVALIDO", "La propuesta del RVIE no se puede leer (línea " + linea + "): " + que);
    }
}
