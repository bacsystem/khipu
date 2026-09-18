package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Impuesto Selectivo al Consumo de una línea (tributo 2000, catálogo 08 para el sistema): al valor (01) se expresa como
 * tasa sobre el valor de venta; monto fijo (02) como importe por unidad. El sistema 03 (precio de venta al público) no
 * se soporta porque su base es el PVP sugerido, no el valor de venta. El ISC forma parte de la base del IGV de la línea (regla 204).
 */
public record Isc(String sistema, BigDecimal tasa, BigDecimal montoUnitario) {

    public Isc {
        if (sistema == null || !sistema.matches("0[123]"))
            throw new DomainException("ISC_INVALIDO", "2041 - El sistema de cálculo del ISC debe ser 01, 02 o 03 (catálogo 08)");
        // El sistema 03 grava el precio de venta al público sugerido, una base que la API no recibe: calcularlo sobre el valor de
        // venta pasaría la validación de SUNAT (3108) con un ISC infradeclarado, así que se rechaza hasta soportar esa base.
        if ("03".equals(sistema))
            throw new DomainException("ISC_INVALIDO", "El sistema 03 (precio de venta al público) aún no está soportado: use 01 (al valor) o 02 (monto fijo)");
        if ("02".equals(sistema)) {
            if (montoUnitario == null || montoUnitario.signum() <= 0 || montoUnitario.scale() > 5)
                throw new DomainException("ISC_INVALIDO", "El sistema 02 (monto fijo) exige monto_unitario positivo, hasta 5 decimales");
            if (tasa != null) throw new DomainException("ISC_INVALIDO", "El sistema 02 (monto fijo) no lleva tasa");
        } else {
            if (tasa == null || tasa.signum() <= 0 || tasa.scale() > 5)
                throw new DomainException("ISC_INVALIDO", "3104 - El sistema 01 exige una tasa de ISC positiva, hasta 5 decimales");
            if (montoUnitario != null) throw new DomainException("ISC_INVALIDO", "El sistema 01 no lleva monto_unitario");
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
