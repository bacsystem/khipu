package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Retención del IGV informada en la factura cuando el adquirente es agente de retención (catálogo 53, código 62;
 * reglas 3262–3264, 3263): el cliente paga el importe menos la retención y la entera a SUNAT. Es informativa:
 * no altera los totales del comprobante.
 */
public record RetencionIgv(BigDecimal porcentaje, BigDecimal monto) {

    public static final String CODIGO = "62";
    public static final BigDecimal TASA_LEGAL = new BigDecimal("3");

    public RetencionIgv {
        porcentaje = porcentaje == null ? TASA_LEGAL : porcentaje;
        if (porcentaje.signum() <= 0 || porcentaje.compareTo(new BigDecimal("100")) >= 0 || porcentaje.scale() > 3)
            throw new DomainException("RETENCION_INVALIDA", "El porcentaje de retención debe ser mayor que 0 y menor que 100 (tasa legal 3 %)");
        if (monto != null && (monto.signum() <= 0 || monto.scale() > 2))
            throw new DomainException("RETENCION_INVALIDA", "2968 - El importe de la retención debe ser positivo con hasta 2 decimales");
    }

    public static BigDecimal montoSobre(BigDecimal base, BigDecimal porcentaje) {
        return base.multiply(porcentaje).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /** Completa el monto si no vino y comprueba que coincide con base × % (tolerancia ±1, regla 3263). */
    RetencionIgv completarContra(BigDecimal importeTotal) {
        BigDecimal esperado = montoSobre(importeTotal, porcentaje);
        if (monto == null) return new RetencionIgv(porcentaje, esperado);
        if (monto.subtract(esperado).abs().compareTo(BigDecimal.ONE) > 0)
            throw new DomainException("RETENCION_INVALIDA", "3263 - El importe de la retención (" + monto + ") no corresponde a " + porcentaje + " % del importe total (" + esperado + ")");
        return this;
    }

    public BigDecimal factor() { return porcentaje.divide(new BigDecimal("100"), 5, RoundingMode.HALF_UP); }
}
