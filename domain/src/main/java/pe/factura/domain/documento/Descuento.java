package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Descuento comercial sobre una línea o sobre el comprobante (catálogo 53). Se expresa como porcentaje sobre la base
 * o como monto fijo; {@code afectaBaseIgv} decide el código SUNAT: por línea {@code 00} (afecta la base imponible) /
 * {@code 01} (no la afecta); global {@code 02} / {@code 03}. Un descuento que no afecta la base rebaja lo que se paga
 * pero no el IGV.
 */
public record Descuento(Tipo tipo, BigDecimal valor, boolean afectaBaseIgv) {

    public enum Tipo { PORCENTAJE, MONTO }

    public Descuento {
        if (tipo == null || valor == null || valor.signum() <= 0)
            throw new DomainException("DESCUENTO_INVALIDO", "2968 - El descuento debe ser un porcentaje o monto positivo");
        if (tipo == Tipo.PORCENTAJE && valor.compareTo(new BigDecimal("100")) >= 0)
            throw new DomainException("DESCUENTO_INVALIDO", "El porcentaje de descuento debe ser menor que 100");
        if (valor.scale() > (tipo == Tipo.PORCENTAJE ? 5 : 2))
            throw new DomainException("DESCUENTO_INVALIDO", "2968 - El descuento admite hasta 2 decimales (monto) o 5 (porcentaje)");
    }

    public static Descuento porcentaje(BigDecimal pct, boolean afectaBaseIgv) { return new Descuento(Tipo.PORCENTAJE, pct, afectaBaseIgv); }
    public static Descuento monto(BigDecimal monto, boolean afectaBaseIgv) { return new Descuento(Tipo.MONTO, monto, afectaBaseIgv); }

    /** Monto del descuento sobre una base (valor de venta sin IGV), a 2 decimales; nunca supera la base. */
    public BigDecimal montoSobre(BigDecimal base) {
        BigDecimal monto = tipo == Tipo.PORCENTAJE
                ? base.multiply(valor).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : valor.setScale(2, RoundingMode.HALF_UP);
        if (monto.compareTo(base) >= 0)
            throw new DomainException("DESCUENTO_INVALIDO", "El descuento (" + monto + ") debe ser menor que la base (" + base + ")");
        return monto;
    }

    /**
     * Factor SUNAT (MultiplierFactorNumeric, hasta 5 decimales): monto / base. Es opcional en el XML y, si va, SUNAT exige
     * {@code base × factor = monto ± 1} (reglas 3290 por línea, 3307 global); con bases grandes el redondeo a 5 decimales
     * rompe esa tolerancia (1 700 000 × 0.00059 = 1 003 frente a 1 000), así que solo se informa cuando la reproduce.
     */
    public static Optional<BigDecimal> factor(BigDecimal monto, BigDecimal base) {
        if (base.signum() == 0) return Optional.empty();
        BigDecimal factor = monto.divide(base, 5, RoundingMode.HALF_UP);
        boolean reproduceElMonto = base.multiply(factor).subtract(monto).abs().compareTo(BigDecimal.ONE) <= 0;
        return factor.signum() > 0 && reproduceElMonto ? Optional.of(factor) : Optional.empty();
    }

    /** Código del catálogo 53 según nivel y si afecta la base del IGV. */
    public String codigoSunat(boolean global) {
        return global ? (afectaBaseIgv ? "02" : "03") : (afectaBaseIgv ? "00" : "01");
    }
}
