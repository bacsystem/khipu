package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Impuesto Selectivo al Consumo de una línea (tributo 2000, catálogo 08 para el sistema): al valor (01) se expresa como
 * tasa sobre el valor de venta; monto fijo (02) como importe por unidad; al valor según precio de venta al público (03)
 * como tasa sobre el PVP sugerido unitario ({@code basePvp}), que es la base del ISC en el XML (3108) aunque el valor de
 * venta sea menor. El ISC forma parte de la base del IGV de la línea (regla 204).
 */
public record Isc(String sistema, BigDecimal tasa, BigDecimal montoUnitario, BigDecimal basePvp) {

    public Isc(String sistema, BigDecimal tasa, BigDecimal montoUnitario) { this(sistema, tasa, montoUnitario, null); }

    public Isc {
        if (sistema == null || !sistema.matches("0[123]"))
            throw new DomainException("ISC_INVALIDO", "2041 - El sistema de cálculo del ISC debe ser 01, 02 o 03 (catálogo 08)");
        if ("02".equals(sistema)) {
            if (montoUnitario == null || montoUnitario.signum() <= 0 || montoUnitario.scale() > 5)
                throw new DomainException("ISC_INVALIDO", "El sistema 02 (monto fijo) exige monto_unitario positivo, hasta 5 decimales");
            if (tasa != null) throw new DomainException("ISC_INVALIDO", "El sistema 02 (monto fijo) no lleva tasa");
            if (basePvp != null) throw new DomainException("ISC_INVALIDO", "El sistema 02 (monto fijo) no lleva base_pvp");
        } else {
            if (tasa == null || tasa.signum() <= 0 || tasa.scale() > 5)
                throw new DomainException("ISC_INVALIDO", "3104 - El sistema " + sistema + " exige una tasa de ISC positiva, hasta 5 decimales");
            if (montoUnitario != null) throw new DomainException("ISC_INVALIDO", "El sistema " + sistema + " no lleva monto_unitario");
            if ("03".equals(sistema)) {
                if (basePvp == null || basePvp.signum() <= 0 || basePvp.scale() > 5)
                    throw new DomainException("ISC_INVALIDO", "El sistema 03 (precio de venta al público) exige base_pvp: PVP sugerido unitario positivo, hasta 5 decimales");
            } else if (basePvp != null) {
                throw new DomainException("ISC_INVALIDO", "El sistema 01 (al valor) no lleva base_pvp");
            }
        }
    }

    /** ISC por unidad, sin IGV: fijo (02) o PVP × tasa (03); en 01 depende del valor de venta y se calcula sobre la base. */
    public BigDecimal montoUnitarioEfectivo() {
        return switch (sistema) {
            case "02" -> montoUnitario;
            case "03" -> basePvp.multiply(tasa).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            default -> null;
        };
    }

    /** Monto del ISC de la línea: tasa × base (01), monto fijo × cantidad (02) o tasa × PVP × cantidad (03). */
    public BigDecimal montoSobre(BigDecimal base, BigDecimal cantidad) {
        return switch (sistema) {
            case "02" -> montoUnitario.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
            case "03" -> basePvp.multiply(cantidad).multiply(tasa).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            default -> base.multiply(tasa).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        };
    }

    /** Base del ISC que va en cbc:TaxableAmount de la línea (3108): el valor de venta (01/02) o el PVP sugerido × cantidad (03). */
    public BigDecimal baseSobre(BigDecimal valorVenta, BigDecimal cantidad) {
        return "03".equals(sistema) ? basePvp.multiply(cantidad).setScale(2, RoundingMode.HALF_UP) : valorVenta;
    }

    /** Tasa que se escribe en cbc:Percent (regla 3108: monto = tasa × base ±1): en monto fijo se deriva del importe. */
    public BigDecimal porcentajeSobre(BigDecimal base, BigDecimal monto) {
        if (!"02".equals(sistema)) return tasa;
        return base.signum() == 0 ? BigDecimal.ZERO : monto.multiply(new BigDecimal("100")).divide(base, 5, RoundingMode.HALF_UP);
    }
}
