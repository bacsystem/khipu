package pe.factura.adapters.rest;

import pe.factura.domain.DomainException;

import java.time.YearMonth;
import java.util.regex.Pattern;

/** El parámetro {@code mes} de las consultas de consumo: {@code AAAA-MM} y nada más; vacío o ausente es «el mes en curso» (nulo). */
final class ParametroMes {
    private static final Pattern MES = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private ParametroMes() {}

    static YearMonth de(String texto) {
        if (texto == null || texto.isEmpty()) return null;
        if (!MES.matcher(texto).matches()) throw new DomainException("PARAMETRO_INVALIDO", "El mes debe tener el formato AAAA-MM (p. ej. 2026-10)");
        return YearMonth.parse(texto);
    }
}
