package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Impuesto al consumo de bolsas de plástico (Ley 30884, tributo 7152): monto fijo por bolsa según el año de emisión.
 * La línea debe ir en unidades (NIU) y la cantidad de bolsas es la cantidad del ítem (reglas 3236, 3237, 4320).
 */
public final class Icbper {
    private Icbper() {}

    /** Tasa vigente por año (art. 12 de la Ley 30884): S/ 0.10 en 2019, +0.10 por año hasta S/ 0.50 desde 2023. */
    public static BigDecimal tasaVigente(LocalDate fecha) {
        int anio = fecha.getYear();
        if (anio <= 2019) return new BigDecimal("0.10");
        if (anio >= 2023) return new BigDecimal("0.50");
        return BigDecimal.valueOf(anio - 2018L, 1);   // 2020 → 0.2, 2021 → 0.3, 2022 → 0.4
    }
}
