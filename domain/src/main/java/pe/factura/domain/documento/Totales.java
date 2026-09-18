package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Totales del comprobante (LegalMonetaryTotal y TaxTotal del UBL), calculados una sola vez a partir de los subtotales
 * por tributo y del descuento global.
 * <ul>
 *   <li>{@code gravado/exonerado/inafecto}: bases onerosas por tributo (1000/9997/9998), netas de descuentos de línea 00
 *       y del global 02. {@code gratuito}: base de las operaciones gratuitas (9996), que no se cobra.</li>
 *   <li>{@code igv}: IGV que se cobra (tributo 1000). {@code igvGratuitas}: IGV de las gratuitas, solo informativo.</li>
 *   <li>{@code totalValorVenta}: suma de bases onerosas (LineExtensionAmount global, regla 54).</li>
 *   <li>{@code totalPrecioVenta}: valor de venta + IGV (TaxInclusiveAmount).</li>
 *   <li>{@code totalDescuentos}: descuentos que no afectan la base (línea 01 + global 03) — AllowanceTotalAmount.</li>
 *   <li>{@code total}: importe a pagar (PayableAmount) = precio de venta − descuentos que no afectan la base.</li>
 * </ul>
 */
public record Totales(BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto, BigDecimal gratuito, BigDecimal igv, BigDecimal igvGratuitas,
                      BigDecimal totalValorVenta, BigDecimal totalPrecioVenta, BigDecimal totalDescuentos, BigDecimal total,
                      List<ItemCalculado> items, List<SubtotalTributo> subtotales, DescuentoGlobalCalculado descuentoGlobal) {

    /** Base imponible e impuesto acumulados de los ítems de un mismo tributo (catálogo 05). */
    public record SubtotalTributo(Tributo tributo, BigDecimal base, BigDecimal impuesto) {}

    /** Descuento global aplicado: monto, base sobre la que se calculó y factor SUNAT (catálogo 53: 02 afecta base, 03 no). */
    public record DescuentoGlobalCalculado(Descuento descuento, BigDecimal base, BigDecimal monto) {
        public BigDecimal factor() { return Descuento.factor(monto, base); }
        public String codigo() { return descuento.codigoSunat(true); }
        public boolean afectaBase() { return descuento.afectaBaseIgv(); }
    }

    public static Totales calcular(List<Item> items) { return calcular(items, null); }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal) {
        List<ItemCalculado> calculados = items.stream().map(ItemCalculado::de).toList();
        List<SubtotalTributo> brutos = Arrays.stream(Tributo.values())
                .map(tr -> new SubtotalTributo(tr, suma(calculados, tr, ItemCalculado::valorVenta), suma(calculados, tr, ItemCalculado::igv)))
                .filter(st -> st.base().signum() > 0)
                .toList();

        DescuentoGlobalCalculado global = null;
        List<SubtotalTributo> subtotales = new ArrayList<>(brutos);
        if (descuentoGlobal != null) {
            if (descuentoGlobal.afectaBaseIgv()) {
                // Código 02: SUNAT lo resta de la base gravada (reglas 46/47), así que exige líneas gravadas onerosas.
                BigDecimal baseGravada = base(brutos, Tributo.IGV);
                if (baseGravada.signum() == 0)
                    throw new DomainException("DESCUENTO_INVALIDO", "Un descuento global que afecta la base del IGV requiere ítems gravados");
                BigDecimal monto = descuentoGlobal.montoSobre(baseGravada);
                global = new DescuentoGlobalCalculado(descuentoGlobal, baseGravada, monto);
                BigDecimal baseNeta = baseGravada.subtract(monto);
                subtotales.replaceAll(st -> st.tributo() == Tributo.IGV
                        ? new SubtotalTributo(st.tributo(), baseNeta, baseNeta.multiply(ItemCalculado.TASA_IGV).setScale(2, RoundingMode.HALF_UP))
                        : st);
            } else {
                BigDecimal baseOnerosa = brutos.stream().filter(st -> st.tributo() != Tributo.GRA).map(SubtotalTributo::base).reduce(z(), BigDecimal::add);
                global = new DescuentoGlobalCalculado(descuentoGlobal, baseOnerosa, descuentoGlobal.montoSobre(baseOnerosa));
            }
        }

        BigDecimal gravado = base(subtotales, Tributo.IGV);
        BigDecimal exonerado = base(subtotales, Tributo.EXO);
        BigDecimal inafecto = base(subtotales, Tributo.INA);
        BigDecimal gratuito = base(subtotales, Tributo.GRA);
        BigDecimal igv = subtotales.stream().filter(st -> st.tributo() == Tributo.IGV).map(SubtotalTributo::impuesto).findFirst().orElse(z());
        BigDecimal igvGratuitas = subtotales.stream().filter(st -> st.tributo() == Tributo.GRA).map(SubtotalTributo::impuesto).findFirst().orElse(z());
        BigDecimal totalValorVenta = gravado.add(exonerado).add(inafecto);
        BigDecimal totalPrecioVenta = totalValorVenta.add(igv);
        BigDecimal totalDescuentos = calculados.stream().map(ItemCalculado::descuentoNoAfectaBase).reduce(z(), BigDecimal::add)
                .add(global != null && !global.afectaBase() ? global.monto() : z());
        BigDecimal total = totalPrecioVenta.subtract(totalDescuentos);
        return new Totales(gravado, exonerado, inafecto, gratuito, igv, igvGratuitas, totalValorVenta, totalPrecioVenta, totalDescuentos, total,
                calculados, List.copyOf(subtotales), global);
    }

    public boolean tieneGratuitas() { return gratuito.signum() > 0; }

    private static BigDecimal suma(List<ItemCalculado> items, Tributo tr, Function<ItemCalculado, BigDecimal> monto) {
        return items.stream().filter(i -> i.tributo() == tr).map(monto).reduce(z(), BigDecimal::add);
    }

    private static BigDecimal base(List<SubtotalTributo> subtotales, Tributo tr) {
        return subtotales.stream().filter(st -> st.tributo() == tr).map(SubtotalTributo::base).findFirst().orElse(z());
    }

    private static BigDecimal z() { return BigDecimal.ZERO.setScale(2); }
}
