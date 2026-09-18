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
 * por afectación y del descuento global.
 * <ul>
 *   <li>{@code gravado/exonerado/inafecto}: bases por afectación, ya netas de descuentos de línea 00 y del global 02.</li>
 *   <li>{@code totalValorVenta}: suma de bases (LineExtensionAmount global).</li>
 *   <li>{@code totalPrecioVenta}: valor de venta + tributos (TaxInclusiveAmount).</li>
 *   <li>{@code totalDescuentos}: descuentos que no afectan la base (línea 01 + global 03) — AllowanceTotalAmount.</li>
 *   <li>{@code total}: importe a pagar (PayableAmount) = precio de venta − descuentos que no afectan la base.</li>
 * </ul>
 */
public record Totales(BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto, BigDecimal igv,
                      BigDecimal totalValorVenta, BigDecimal totalPrecioVenta, BigDecimal totalDescuentos, BigDecimal total,
                      List<ItemCalculado> items, List<SubtotalTributo> subtotales, DescuentoGlobalCalculado descuentoGlobal) {

    /** Base imponible e impuesto acumulados de los ítems con una misma afectación (catálogo 07). */
    public record SubtotalTributo(TipoAfectacionIgv afectacion, BigDecimal base, BigDecimal impuesto) {}

    /** Descuento global aplicado: monto, base sobre la que se calculó y factor SUNAT (catálogo 53: 02 afecta base, 03 no). */
    public record DescuentoGlobalCalculado(Descuento descuento, BigDecimal base, BigDecimal monto) {
        public BigDecimal factor() { return Descuento.factor(monto, base); }
        public String codigo() { return descuento.codigoSunat(true); }
        public boolean afectaBase() { return descuento.afectaBaseIgv(); }
    }

    public static Totales calcular(List<Item> items) { return calcular(items, null); }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal) {
        List<ItemCalculado> calculados = items.stream().map(ItemCalculado::de).toList();
        List<SubtotalTributo> brutos = Arrays.stream(TipoAfectacionIgv.values())
                .map(af -> new SubtotalTributo(af, suma(calculados, af, ItemCalculado::valorVenta), suma(calculados, af, ItemCalculado::igv)))
                .filter(st -> st.base().signum() > 0)
                .toList();

        DescuentoGlobalCalculado global = null;
        List<SubtotalTributo> subtotales = new ArrayList<>(brutos);
        if (descuentoGlobal != null) {
            if (descuentoGlobal.afectaBaseIgv()) {
                // Código 02: SUNAT lo resta de la base gravada (reglas 46/47), así que exige líneas gravadas.
                BigDecimal baseGravada = base(brutos, TipoAfectacionIgv.GRAVADO);
                if (baseGravada.signum() == 0)
                    throw new DomainException("DESCUENTO_INVALIDO", "Un descuento global que afecta la base del IGV requiere ítems gravados");
                BigDecimal monto = descuentoGlobal.montoSobre(baseGravada);
                global = new DescuentoGlobalCalculado(descuentoGlobal, baseGravada, monto);
                BigDecimal baseNeta = baseGravada.subtract(monto);
                subtotales.replaceAll(st -> st.afectacion() == TipoAfectacionIgv.GRAVADO
                        ? new SubtotalTributo(st.afectacion(), baseNeta, baseNeta.multiply(ItemCalculado.TASA_IGV).setScale(2, RoundingMode.HALF_UP))
                        : st);
            } else {
                BigDecimal baseTotal = brutos.stream().map(SubtotalTributo::base).reduce(z(), BigDecimal::add);
                global = new DescuentoGlobalCalculado(descuentoGlobal, baseTotal, descuentoGlobal.montoSobre(baseTotal));
            }
        }

        BigDecimal gravado = base(subtotales, TipoAfectacionIgv.GRAVADO);
        BigDecimal exonerado = base(subtotales, TipoAfectacionIgv.EXONERADO);
        BigDecimal inafecto = base(subtotales, TipoAfectacionIgv.INAFECTO);
        BigDecimal igv = subtotales.stream().map(SubtotalTributo::impuesto).reduce(z(), BigDecimal::add);
        BigDecimal totalValorVenta = gravado.add(exonerado).add(inafecto);
        BigDecimal totalPrecioVenta = totalValorVenta.add(igv);
        BigDecimal totalDescuentos = calculados.stream().map(ItemCalculado::descuentoNoAfectaBase).reduce(z(), BigDecimal::add)
                .add(global != null && !global.afectaBase() ? global.monto() : z());
        BigDecimal total = totalPrecioVenta.subtract(totalDescuentos);
        return new Totales(gravado, exonerado, inafecto, igv, totalValorVenta, totalPrecioVenta, totalDescuentos, total,
                calculados, List.copyOf(subtotales), global);
    }

    private static BigDecimal suma(List<ItemCalculado> items, TipoAfectacionIgv af, Function<ItemCalculado, BigDecimal> monto) {
        return items.stream().filter(i -> i.item().afectacion() == af).map(monto).reduce(z(), BigDecimal::add);
    }

    private static BigDecimal base(List<SubtotalTributo> subtotales, TipoAfectacionIgv af) {
        return subtotales.stream().filter(st -> st.afectacion() == af).map(SubtotalTributo::base).findFirst().orElse(z());
    }

    private static BigDecimal z() { return BigDecimal.ZERO.setScale(2); }
}
