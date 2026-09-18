package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Importes de una línea calculados por khipu a partir del precio con IGV que envía el emisor.
 * <ul>
 *   <li>{@code valorUnitario}: precio sin IGV (10 decimales) — cac:Price/PriceAmount.</li>
 *   <li>{@code baseBruta}: valorUnitario × cantidad, antes de descuentos.</li>
 *   <li>{@code descuento}: monto del descuento de línea (0 si no hay); {@code descuentoAfectaBase} si es código 00.</li>
 *   <li>{@code valorVenta}: base sobre la que se calcula el IGV — LineExtensionAmount (bruta − descuento 00).</li>
 *   <li>{@code precioVentaUnitario}: (valorVenta + igv − descuento 01) / cantidad — PricingReference (regla 33).</li>
 * </ul>
 */
public record ItemCalculado(Item item, BigDecimal valorUnitario, BigDecimal baseBruta, BigDecimal descuento, boolean descuentoAfectaBase,
                            BigDecimal valorVenta, BigDecimal igv, BigDecimal precioVenta, BigDecimal precioVentaUnitario, BigDecimal porcentajeIgv) {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    private static final BigDecimal UNO_MAS_IGV = BigDecimal.ONE.add(TASA_IGV);

    public static ItemCalculado de(Item item) {
        boolean gravado = item.afectacion().gravado();
        BigDecimal precio = item.precioUnitario();
        BigDecimal valorUnitario = gravado ? precio.divide(UNO_MAS_IGV, 10, RoundingMode.HALF_UP) : precio.setScale(10, RoundingMode.HALF_UP);
        BigDecimal baseBruta = valorUnitario.multiply(item.cantidad()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal descuento = item.tieneDescuento() ? item.descuento().montoSobre(baseBruta) : BigDecimal.ZERO.setScale(2);
        boolean afectaBase = item.tieneDescuento() && item.descuento().afectaBaseIgv();
        BigDecimal valorVenta = afectaBase ? baseBruta.subtract(descuento) : baseBruta;
        BigDecimal igv = gravado ? valorVenta.multiply(TASA_IGV).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal descuentoNoAfecta = item.tieneDescuento() && !afectaBase ? descuento : BigDecimal.ZERO;
        BigDecimal precioVenta = valorVenta.add(igv).subtract(descuentoNoAfecta);
        BigDecimal precioVentaUnitario = precioVenta.divide(item.cantidad(), 10, RoundingMode.HALF_UP);
        BigDecimal pct = gravado ? new BigDecimal("18.00") : new BigDecimal("0.00");
        return new ItemCalculado(item, valorUnitario, baseBruta, descuento, afectaBase, valorVenta, igv, precioVenta, precioVentaUnitario, pct);
    }

    /** Factor SUNAT del descuento de línea (MultiplierFactorNumeric): monto / base bruta; vacío si no reproduce el monto (regla 3290). */
    public java.util.Optional<BigDecimal> descuentoFactor() { return Descuento.factor(descuento, baseBruta); }

    /** Monto que reduce lo que se paga sin tocar el IGV (código 01): entra en AllowanceTotalAmount. */
    public BigDecimal descuentoNoAfectaBase() { return item.tieneDescuento() && !descuentoAfectaBase ? descuento : BigDecimal.ZERO.setScale(2); }
}
