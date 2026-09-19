package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Tasa del IGV que se aplica a las líneas gravadas, en porcentaje con 2 decimales (cbc:Percent). La general es 18 % (16 %
 * IGV + 2 % IPM). Los emisores inscritos en el Padrón de Tasa Especial del IGV (MYPE de restaurantes y hoteles, Ley 31556)
 * aplican una tasa reducida: 10 % desde el 2023-01-01 y 10.5 % desde el 2026-02-13 (control de cambios de las reglas de
 * validación de SUNAT). SUNAT valida el IGV "según la tasa indicada en la línea" (3279, 3291), exige la misma tasa en todas
 * las líneas (3462) y observa (4439) al que declara la reducida sin estar en el padrón; al que está y declara 18 % no le dice nada.
 */
public final class TasaIgv {
    private TasaIgv() {}

    public static final BigDecimal GENERAL = new BigDecimal("18.00");
    private static final BigDecimal REDUCIDA_INICIAL = new BigDecimal("10.00");
    private static final BigDecimal REDUCIDA = new BigDecimal("10.50");
    private static final LocalDate INICIO_REDUCIDA = LocalDate.of(2023, 1, 1);
    private static final LocalDate INICIO_10_5 = LocalDate.of(2026, 2, 13);

    /** Tasa vigente a la fecha de emisión para un emisor con o sin tasa especial. */
    public static BigDecimal vigente(LocalDate fechaEmision, boolean tasaEspecial) {
        if (!tasaEspecial || fechaEmision.isBefore(INICIO_REDUCIDA)) return GENERAL;
        return fechaEmision.isBefore(INICIO_10_5) ? REDUCIDA_INICIAL : REDUCIDA;
    }

    /** {@code 18.00} → {@code 0.18}. */
    public static BigDecimal factor(BigDecimal porcentaje) {
        return porcentaje.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
    }

    /** Porcentaje tal como se persiste y se escribe en el XML: 2 decimales; nulo → tasa general. */
    public static BigDecimal normalizar(BigDecimal porcentaje) {
        return porcentaje == null ? GENERAL : porcentaje.setScale(2, RoundingMode.HALF_UP);
    }
}
