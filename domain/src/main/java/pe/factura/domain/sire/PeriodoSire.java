package pe.factura.domain.sire;

import pe.factura.domain.DomainException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * El periodo tributario del SIRE (#322): un mes, escrito {@code AAAAMM} como lo usa SUNAT en el campo 3 del registro de ventas y en el nombre del archivo
 * ({@code LE<RUC><AAAAMM>…}). Se valida al leerlo, antes de tocar la base: un periodo mal escrito no llega a abrir la descarga.
 */
public record PeriodoSire(YearMonth mes) {
    private static final Pattern FORMATO = Pattern.compile("\\d{4}(0[1-9]|1[0-2])");
    /** El SEE electrónico arranca en 2014; antes no hay comprobantes electrónicos que exportar y un año así es casi seguro un error de tipeo. */
    static final int PRIMER_ANIO = 2014;

    public PeriodoSire {
        if (mes == null) throw new DomainException("PARAMETRO_INVALIDO", "periodo: obligatorio, con el formato AAAAMM (p. ej. 202610)");
    }

    public static PeriodoSire de(String texto) {
        if (texto == null || !FORMATO.matcher(texto.strip()).matches())
            throw new DomainException("PARAMETRO_INVALIDO", "periodo: debe tener el formato AAAAMM (p. ej. 202610); recibido " + (texto == null ? "nada" : "«" + recortar(texto) + "»"));
        String t = texto.strip();
        YearMonth m = YearMonth.of(Integer.parseInt(t.substring(0, 4)), Integer.parseInt(t.substring(4)));
        if (m.getYear() < PRIMER_ANIO) throw new DomainException("PARAMETRO_INVALIDO", "periodo: " + t + " es anterior a " + PRIMER_ANIO + ", cuando no había comprobantes electrónicos");
        return new PeriodoSire(m);
    }

    /** Un periodo que todavía no empezó (según {@code hoy}, en hora de Lima) no tiene comprobantes: se rechaza en vez de responder un archivo vacío. */
    public void exigirIniciado(LocalDate hoy) {
        if (mes.isAfter(YearMonth.from(hoy)))
            throw new DomainException("PARAMETRO_INVALIDO", "periodo: " + aaaamm() + " todavía no empieza (hoy es " + hoy + ")");
    }

    public String aaaamm() { return "%04d%02d".formatted(mes.getYear(), mes.getMonthValue()); }

    public LocalDate desde() { return mes.atDay(1); }

    public LocalDate hasta() { return mes.atEndOfMonth(); }

    private static String recortar(String s) { return s.length() > 20 ? s.substring(0, 20) + "…" : s; }

    @Override public String toString() { return aaaamm(); }
}
