package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Importes de una línea calculados por khipu a partir del precio que envía el emisor.
 * <ul>
 *   <li>Onerosas: el precio es el precio de venta unitario con IGV (gravadas) o sin él (exoneradas/inafectas).
 *       {@code valorUnitario} es el valor sin IGV (cac:Price/PriceAmount).</li>
 *   <li>Gratuitas: el precio es el <em>valor referencial</em> unitario sin IGV (PricingReference, código 02);
 *       {@code valorUnitario} es 0 (regla 2640) y la línea no suma al importe a pagar.</li>
 *   <li>{@code baseBruta}: valor × cantidad antes de descuentos; {@code valorVenta}: base sobre la que se calcula el
 *       IGV — LineExtensionAmount (bruta − descuento 00).</li>
 *   <li>{@code precioVenta}: lo que paga el cliente por la línea; {@code precioVentaUnitario} (regla 33).</li>
 * </ul>
 */
public record ItemCalculado(Item item, BigDecimal valorUnitario, BigDecimal baseBruta, BigDecimal descuento, boolean descuentoAfectaBase,
                            BigDecimal valorVenta, BigDecimal igv, BigDecimal precioVenta, BigDecimal precioVentaUnitario, BigDecimal porcentajeIgv) {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    private static final BigDecimal UNO_MAS_IGV = BigDecimal.ONE.add(TASA_IGV);

    public static ItemCalculado de(Item item) {
        TipoAfectacionIgv af = item.afectacion();
        BigDecimal precio = item.precioUnitario();
        // El precio con IGV solo lo traen las gravadas onerosas; gratuitas y no gravadas envían un valor sin IGV.
        BigDecimal valorReferencial = af.gravado() && !af.gratuita() ? precio.divide(UNO_MAS_IGV, 10, RoundingMode.HALF_UP) : precio.setScale(10, RoundingMode.HALF_UP);
        BigDecimal baseBruta = valorReferencial.multiply(item.cantidad()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal descuento = item.tieneDescuento() ? item.descuento().montoSobre(baseBruta) : BigDecimal.ZERO.setScale(2);
        boolean afectaBase = item.tieneDescuento() && item.descuento().afectaBaseIgv();
        BigDecimal valorVenta = afectaBase ? baseBruta.subtract(descuento) : baseBruta;
        BigDecimal igv = af.gravado() ? valorVenta.multiply(TASA_IGV).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal descuentoNoAfecta = item.tieneDescuento() && !afectaBase ? descuento : BigDecimal.ZERO;
        BigDecimal precioVenta = af.gratuita() ? BigDecimal.ZERO.setScale(2) : valorVenta.add(igv).subtract(descuentoNoAfecta);
        BigDecimal valorUnitario = af.gratuita() ? BigDecimal.ZERO.setScale(10) : valorReferencial;
        // Regla 33 (onerosas): (valor de venta + tributos − descuentos que no afectan la base) / cantidad. Gratuitas: el referencial (3224).
        BigDecimal precioVentaUnitario = af.gratuita() ? valorReferencial : precioVenta.divide(item.cantidad(), 10, RoundingMode.HALF_UP);
        BigDecimal pct = af.gravado() ? new BigDecimal("18.00") : new BigDecimal("0.00");
        return new ItemCalculado(item, valorUnitario, baseBruta, descuento, afectaBase, valorVenta, igv, precioVenta, precioVentaUnitario, pct);
    }

    /** Factor SUNAT del descuento de línea (MultiplierFactorNumeric): monto / base bruta; vacío si no reproduce el monto (regla 3290). */
    public java.util.Optional<BigDecimal> descuentoFactor() { return Descuento.factor(descuento, baseBruta); }

    /** Monto que reduce lo que se paga sin tocar el IGV (código 01): entra en AllowanceTotalAmount. */
    public BigDecimal descuentoNoAfectaBase() { return item.tieneDescuento() && !descuentoAfectaBase ? descuento : BigDecimal.ZERO.setScale(2); }

    public Tributo tributo() { return item.afectacion().tributo(); }
    public boolean gratuita() { return item.afectacion().gratuita(); }
    /** Código de tipo de precio (catálogo 16): 01 precio de venta, 02 valor referencial en gratuitas. */
    public String tipoPrecio() { return gratuita() ? "02" : "01"; }
}
