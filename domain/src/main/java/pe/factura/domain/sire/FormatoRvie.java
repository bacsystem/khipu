package pe.factura.domain.sire;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * El formato del archivo de reemplazo de la propuesta del Registro de Ventas e Ingresos Electrónico (RVIE) del SIRE (#322): un solo lugar para el separador,
 * el fin de línea, la codificación, el nombre del archivo y cómo se escribe cada tipo de dato. Lo que lee la conciliación ({@link PropuestaRvie}) usa las mismas
 * constantes.
 *
 * <p><b>EXPERIMENTAL: formato reconstruido, no validado con el PVSIRE ni con el SIRE.</b> Fuentes: el Anexo III de la RS 000138-2023/SUNAT («Estructura y reglas
 * para elaborar el archivo plano que permita la comparación con la propuesta RVIE o reemplazar esa última»), que fija los campos 2, 3, 7–13 y 29–32 y las notas 1,
 * 3–6, y el «Control de cambios del Aplicativo Cliente SIRE» (separador «palotes "|"» y cantidad de campos, validaciones 246 y 453; tipo de cambio en soles
 * {@code 0.000}, versión 3.17.0; campo 25 con signo libre, 3.38.0; vencimiento dentro del periodo, validación 210). Los campos 1, 4–6, 14–28 y 33–40, el nombre del
 * archivo, la codificación y el fin de línea no tienen fuente en el repositorio: son supuestos, y cada uno está aquí para corregirlo en un solo sitio
 * ({@code deploy/README.md} §15).
 * <ul>
 *   <li>Campos separados por {@value #SEPARADOR} (control de cambios, validación 246); también al final de la línea, como en el PLE (supuesto); fin de línea
 *       CRLF (supuesto).</li>
 *   <li>Sin fila de títulos.</li>
 *   <li>UTF-8 sin BOM.</li>
 *   <li>Fechas {@code dd/MM/yyyy}; importes con punto decimal, 2 decimales, sin separador de miles, negativos con {@code -}; tipo de cambio con 3 decimales.</li>
 *   <li>Texto sin el separador ni caracteres de control: el formato no tiene una regla de escape, así que se reemplazan por un espacio (ver {@link #texto}).</li>
 * </ul>
 */
public final class FormatoRvie {
    public static final char SEPARADOR = '|';
    public static final String FIN_DE_LINEA = "\r\n";
    public static final Charset CODIFICACION = StandardCharsets.UTF_8;
    /** Campos de una fila del RVIE (Anexo 3: del 1, RUC del emisor, al 40, CLU). */
    public static final int CAMPOS = 40;
    /** Código del libro del RVIE en el nombre del archivo. */
    public static final String LIBRO_RVIE = "140400";
    /**
     * Lo que sigue al libro en el nombre del archivo de reemplazo: {@code 02} (código de oportunidad), {@code 1} (indicador de operaciones), {@code 1} (con
     * información), {@code 1} (moneda: soles) y {@code 2} (generado para el SIRE). Supuesto: ver la nota de la clase.
     */
    static final String SUFIJO_REEMPLAZO = "021112";

    static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private FormatoRvie() {}

    /** {@code LE<RUC><AAAAMM>00140400021112}, sin extensión: el {@code .txt} va dentro del {@code .zip} del mismo nombre. */
    public static String nombreArchivo(String ruc, PeriodoSire periodo) {
        return "LE" + ruc + periodo.aaaamm() + "00" + LIBRO_RVIE + SUFIJO_REEMPLAZO;
    }

    /** Una fila del archivo: los {@value #CAMPOS} campos de {@link FilaRvie#campos()} separados y cerrados por {@value #SEPARADOR}, con su fin de línea. */
    public static String linea(FilaRvie fila) {
        List<String> campos = fila.campos();
        if (campos.size() != CAMPOS) throw new IllegalStateException("Una fila del RVIE tiene " + CAMPOS + " campos; esta tiene " + campos.size());
        StringBuilder sb = new StringBuilder(256);
        for (String c : campos) {
            if (c.indexOf(SEPARADOR) >= 0 || c.indexOf('\r') >= 0 || c.indexOf('\n') >= 0)
                throw new IllegalStateException("Un campo del RVIE no puede llevar el separador ni un salto de línea: " + c);
            sb.append(c).append(SEPARADOR);
        }
        return sb.append(FIN_DE_LINEA).toString();
    }

    /** {@code 1234.50}, {@code -10.00}, {@code 0.00}: 2 decimales redondeados al centavo, nunca {@code -0.00} ni notación científica. */
    public static String importe(BigDecimal v) {
        BigDecimal r = (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
        return (r.signum() == 0 ? r.abs() : r).toPlainString();
    }

    /** El tipo de cambio con 3 decimales ({@code 3.745}); vacío si no hay. */
    public static String tipoCambio(BigDecimal v) { return v == null ? "" : v.setScale(3, RoundingMode.HALF_UP).toPlainString(); }

    public static String fecha(LocalDate f) { return f == null ? "" : f.format(FECHA); }

    /**
     * Texto libre (razón social, número de documento) apto para el archivo: el separador, los saltos de línea, los tabuladores y cualquier otro carácter de control
     * pasan a ser un espacio, los espacios repetidos se juntan y se quitan los de los extremos. Se corta en {@code maximo} caracteres (puntos de código, no
     * unidades UTF-16, para no partir un carácter). Un texto nulo es vacío.
     */
    public static String texto(String s, int maximo) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        boolean espacio = false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            boolean blanco = cp == SEPARADOR || Character.isISOControl(cp) || Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0xFEFF;
            if (blanco) { espacio = true; continue; }
            if (espacio && !sb.isEmpty()) sb.append(' ');
            espacio = false;
            sb.appendCodePoint(cp);
        }
        String r = sb.toString();
        return r.codePointCount(0, r.length()) <= maximo ? r : r.substring(0, r.offsetByCodePoints(0, maximo)).stripTrailing();
    }
}
