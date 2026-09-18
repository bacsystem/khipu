package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Impuesto Selectivo al Consumo de una línea (tributo 2000, catálogo 08 para el sistema): al valor (01) y precio de
 * venta al público (03) se expresan como tasa sobre la base; monto fijo (02) como importe por unidad. El ISC forma
 * parte de la base del IGV de la línea (regla 204).
 */
public record Isc(String sistema, BigDecimal tasa, BigDecimal montoUnitario) {

    public Isc {
        if (sistema == null || !sistema.matches("0[123]"))
            throw new DomainException("ISC_INVALIDO", "2041 - El sistema de cálculo del ISC debe ser 01, 02 o 03 (catálogo 08)");
        if ("02".equals(sistema)) {
            if (montoUnitario == null || montoUnitario.signum() <= 0 || montoUnitario.scale() > 5)
                throw new DomainException("ISC_INVALIDO", "El sistema 02 (monto fijo) exige monto_unitario positivo, hasta 5 decimales");
            if (tasa != null) throw new DomainException("ISC_INVALIDO", "El sistema 02 (monto fijo) no lleva tasa");
        } else {
            if (tasa == null || tasa.signum() <= 0 || tasa.scale() > 5)
                throw new DomainException("ISC_INVALIDO", "3104 - Los sistemas 01 y 03 exigen una tasa de ISC positiva, hasta 5 decimales");
            if (montoUnitario != null) throw new DomainException("ISC_INVALIDO", "Los sistemas 01 y 03 no llevan monto_unitario");
        }
    }

    /** Monto del ISC de la línea: tasa × base, o monto fijo × cantidad. */
    public BigDecimal montoSobre(BigDecimal base, BigDecimal cantidad) {
        return "02".equals(sistema)
                ? montoUnitario.multiply(cantidad).setScale(2, RoundingMode.HALF_UP)
                : base.multiply(tasa).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /** Tasa que se escribe en cbc:Percent (regla 3108: monto = tasa × base ±1): en monto fijo se deriva del importe. */
    public BigDecimal porcentajeSobre(BigDecimal base, BigDecimal monto) {
        if (!"02".equals(sistema)) return tasa;
        return base.signum() == 0 ? BigDecimal.ZERO : monto.multiply(new BigDecimal("100")).divide(base, 5, RoundingMode.HALF_UP);
    }
}
