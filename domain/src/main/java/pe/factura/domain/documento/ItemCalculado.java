package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Importes de una línea calculados por khipu a partir del precio que envía el emisor.
 * <ul>
 *   <li>Onerosas: el precio es el precio de venta unitario con IGV (gravadas) o sin él (exoneradas/inafectas).
 *       {@code valorUnitario} es el valor sin IGV (cac:Price/PriceAmount). Si la línea lleva ISC o ICBPER, el precio
 *       enviado los incluye también: khipu los separa (ISC entra en la base del IGV, regla 204).</li>
 *   <li>Gratuitas: el precio es el <em>valor referencial</em> unitario sin IGV (PricingReference, código 02);
 *       {@code valorUnitario} es 0 (regla 2640) y la línea no suma al importe a pagar.</li>
 *   <li>{@code baseBruta}: valor × cantidad antes de descuentos; {@code valorVenta}: LineExtensionAmount
 *       (bruta − descuento 00); {@code isc}, {@code icbper}: tributos adicionales; {@code igv} sobre valorVenta + isc.</li>
 *   <li>{@code precioVenta}: lo que paga el cliente por la línea; {@code precioVentaUnitario} (regla 33).</li>
 * </ul>
 */
public record ItemCalculado(Item item, BigDecimal valorUnitario, BigDecimal baseBruta, BigDecimal descuento, boolean descuentoAfectaBase,
                            BigDecimal valorVenta, BigDecimal isc, BigDecimal iscPorcentaje, BigDecimal icbper, BigDecimal icbperUnitario,
                            BigDecimal igv, BigDecimal precioVenta, BigDecimal precioVentaUnitario, BigDecimal porcentajeIgv) {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    private static final BigDecimal UNO_MAS_IGV = BigDecimal.ONE.add(TASA_IGV);
    private static final BigDecimal CIEN = new BigDecimal("100");

    public static ItemCalculado de(Item item) { return de(item, Icbper.tasaVigente(java.time.LocalDate.of(2023, 1, 1))); }

    public static ItemCalculado de(Item item, BigDecimal tasaIcbper) {
        TipoAfectacionIgv af = item.afectacion();
        boolean onerosaGravada = af.gravado() && !af.gratuita();
        BigDecimal cantidad = item.cantidad();
        // ICBPER: monto fijo por unidad, fuera de la base del IGV; el precio enviado lo incluye.
        BigDecimal icbperUnitario = item.icbper() ? tasaIcbper : BigDecimal.ZERO.setScale(2);
        BigDecimal icbper = item.icbper() ? tasaIcbper.multiply(cantidad).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal precioSinIcbper = item.precioUnitario().subtract(icbperUnitario);
        // Precio con IGV e ISC → valor sin tributos: precio = valor × (1 + isc%) × 1.18 (sistemas 01/03) o (valor + iscFijo) × 1.18 (02).
        BigDecimal valorReferencial;
        if (!onerosaGravada) valorReferencial = precioSinIcbper.setScale(10, RoundingMode.HALF_UP);
        else if (item.tieneIsc() && "02".equals(item.isc().sistema())) valorReferencial = precioSinIcbper.divide(UNO_MAS_IGV, 10, RoundingMode.HALF_UP).subtract(item.isc().montoUnitario());
        else if (item.tieneIsc()) valorReferencial = precioSinIcbper.divide(UNO_MAS_IGV, 10, RoundingMode.HALF_UP).divide(BigDecimal.ONE.add(item.isc().tasa().divide(CIEN, 10, RoundingMode.HALF_UP)), 10, RoundingMode.HALF_UP);
        else valorReferencial = precioSinIcbper.divide(UNO_MAS_IGV, 10, RoundingMode.HALF_UP);
        BigDecimal baseBruta = valorReferencial.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
        BigDecimal descuento = item.tieneDescuento() ? item.descuento().montoSobre(baseBruta) : BigDecimal.ZERO.setScale(2);
        boolean afectaBase = item.tieneDescuento() && item.descuento().afectaBaseIgv();
        BigDecimal valorVenta = afectaBase ? baseBruta.subtract(descuento) : baseBruta;
        BigDecimal isc = item.tieneIsc() && !af.gratuita() ? item.isc().montoSobre(valorVenta, cantidad) : BigDecimal.ZERO.setScale(2);
        BigDecimal iscPorcentaje = item.tieneIsc() && !af.gratuita() ? item.isc().porcentajeSobre(valorVenta, isc) : BigDecimal.ZERO;
        BigDecimal igv = af.gravado() ? valorVenta.add(isc).multiply(TASA_IGV).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal descuentoNoAfecta = item.tieneDescuento() && !afectaBase ? descuento : BigDecimal.ZERO;
        BigDecimal precioVenta = af.gratuita() ? BigDecimal.ZERO.setScale(2) : valorVenta.add(isc).add(igv).add(icbper).subtract(descuentoNoAfecta);
        BigDecimal valorUnitario = af.gratuita() ? BigDecimal.ZERO.setScale(10) : valorReferencial;
        BigDecimal precioVentaUnitario = af.gratuita() ? valorReferencial : precioVenta.divide(cantidad, 10, RoundingMode.HALF_UP);
        BigDecimal pct = af.gravado() ? new BigDecimal("18.00") : new BigDecimal("0.00");
        return new ItemCalculado(item, valorUnitario, baseBruta, descuento, afectaBase, valorVenta, isc, iscPorcentaje, icbper, icbperUnitario,
                igv, precioVenta, precioVentaUnitario, pct);
    }

    /** Factor SUNAT del descuento de línea (MultiplierFactorNumeric): monto / base bruta. */
    public BigDecimal descuentoFactor() { return Descuento.factor(descuento, baseBruta); }

    /** Monto que reduce lo que se paga sin tocar el IGV (código 01): entra en AllowanceTotalAmount. */
    public BigDecimal descuentoNoAfectaBase() { return item.tieneDescuento() && !descuentoAfectaBase ? descuento : BigDecimal.ZERO.setScale(2); }

    public Tributo tributo() { return item.afectacion().tributo(); }
    public boolean gratuita() { return item.afectacion().gratuita(); }
    public boolean tieneIsc() { return isc.signum() > 0; }
    public boolean tieneIcbper() { return icbper.signum() > 0; }
    /** Base del IGV de la línea: valor de venta más ISC (regla 204). */
    public BigDecimal baseIgv() { return valorVenta.add(isc); }
    /** Suma de tributos de la línea (cac:InvoiceLine/cac:TaxTotal/cbc:TaxAmount, regla 3292). */
    public BigDecimal totalTributos() { return igv.add(isc).add(icbper); }
    /** Código de tipo de precio (catálogo 16): 01 precio de venta, 02 valor referencial en gratuitas. */
    public String tipoPrecio() { return gratuita() ? "02" : "01"; }
}
