package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Totales del comprobante. Se calculan una sola vez a partir de los subtotales por afectación, que son lo que el UBL lista en cac:TaxTotal. */
public record Totales(BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto,
                      BigDecimal igv, BigDecimal total, List<ItemCalculado> items, List<SubtotalTributo> subtotales) {

    /** Base imponible e impuesto acumulados de los ítems con una misma afectación (catálogo 07). */
    public record SubtotalTributo(TipoAfectacionIgv afectacion, BigDecimal base, BigDecimal impuesto) {}

    public static Totales calcular(List<Item> items) {
        List<ItemCalculado> calculados = items.stream().map(ItemCalculado::de).toList();
        // Un subtotal por afectación con base > 0, en el orden del catálogo (gravado, exonerado, inafecto).
        List<SubtotalTributo> subtotales = Arrays.stream(TipoAfectacionIgv.values())
                .map(af -> new SubtotalTributo(af, suma(calculados, af, ItemCalculado::valorVenta), suma(calculados, af, ItemCalculado::igv)))
                .filter(st -> st.base().signum() > 0)
                .toList();
        BigDecimal gravado = base(subtotales, TipoAfectacionIgv.GRAVADO);
        BigDecimal exonerado = base(subtotales, TipoAfectacionIgv.EXONERADO);
        BigDecimal inafecto = base(subtotales, TipoAfectacionIgv.INAFECTO);
        BigDecimal igv = subtotales.stream().map(SubtotalTributo::impuesto).reduce(z(), BigDecimal::add);
        BigDecimal total = gravado.add(exonerado).add(inafecto).add(igv);
        return new Totales(gravado, exonerado, inafecto, igv, total, calculados, subtotales);
    }

    private static BigDecimal suma(List<ItemCalculado> items, TipoAfectacionIgv af, Function<ItemCalculado, BigDecimal> monto) {
        return items.stream().filter(i -> i.item().afectacion() == af).map(monto).reduce(z(), BigDecimal::add);
    }

    private static BigDecimal base(List<SubtotalTributo> subtotales, TipoAfectacionIgv af) {
        return subtotales.stream().filter(st -> st.afectacion() == af).map(SubtotalTributo::base).findFirst().orElse(z());
    }

    private static BigDecimal z() { return BigDecimal.ZERO.setScale(2); }
}
