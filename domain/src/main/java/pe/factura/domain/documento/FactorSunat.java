package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Factor SUNAT de un cargo o descuento (cbc:MultiplierFactorNumeric, hasta 5 decimales): monto / base. Es opcional en el
 * XML y, si va, SUNAT exige {@code base × factor = monto ± 1} (reglas 3290 por línea, 3307 global); con bases grandes el
 * redondeo a 5 decimales rompe esa tolerancia (1 700 000 × 0.00059 = 1 003 frente a 1 000), así que solo se informa
 * cuando la reproduce.
 */
final class FactorSunat {
    private FactorSunat() {}

    static Optional<BigDecimal> de(BigDecimal monto, BigDecimal base) {
        if (base.signum() == 0) return Optional.empty();
        BigDecimal factor = monto.divide(base, 5, RoundingMode.HALF_UP);
        boolean reproduceElMonto = base.multiply(factor).subtract(monto).abs().compareTo(BigDecimal.ONE) <= 0;
        return factor.signum() > 0 && reproduceElMonto ? Optional.of(factor) : Optional.empty();
    }
}
