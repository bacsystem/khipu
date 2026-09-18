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
 *   <li>{@code totalAnticipos}: importes ya pagados con facturas de anticipo, IGV incluido (PrepaidAmount, regla 66).</li>
 *   <li>{@code total}: importe a pagar (PayableAmount) = precio de venta − descuentos que no afectan la base − anticipos (regla 3280).</li>
 * </ul>
 * Con anticipos, SUNAT resta su valor sin IGV de la base del tributo que corresponda (04 gravado, 05 exonerado, 06 inafecto:
 * reglas 3277, 3291) pero no del total valor/precio de venta (3278, 3279): por eso {@code gravado/exonerado/inafecto} y el
 * {@code igv} son netos de anticipos mientras {@code totalValorVenta} y {@code totalPrecioVenta} son brutos.
 */
public record Totales(BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto, BigDecimal gratuito, BigDecimal igv, BigDecimal igvGratuitas,
                      BigDecimal isc, BigDecimal icbper,
                      BigDecimal totalValorVenta, BigDecimal totalPrecioVenta, BigDecimal totalDescuentos, BigDecimal totalAnticipos, BigDecimal total,
                      List<ItemCalculado> items, List<SubtotalTributo> subtotales, DescuentoGlobalCalculado descuentoGlobal, List<AnticipoCalculado> anticipos) {

    /** Base imponible e impuesto acumulados de los ítems de un mismo tributo (catálogo 05). */
    public record SubtotalTributo(Tributo tributo, BigDecimal base, BigDecimal impuesto) {}

    /** Descuento global aplicado: monto, base sobre la que se calculó y factor SUNAT (catálogo 53: 02 afecta base, 03 no). */
    public record DescuentoGlobalCalculado(Descuento descuento, BigDecimal base, BigDecimal monto) {
        /** Factor para el XML, vacío cuando el redondeo a 5 decimales no reproduce el monto (regla 3307). */
        public java.util.Optional<BigDecimal> factor() { return Descuento.factor(monto, base); }
        public String codigo() { return descuento.codigoSunat(true); }
        public boolean afectaBase() { return descuento.afectaBaseIgv(); }
    }

    /** Anticipo aplicado: base bruta del tributo sobre la que se descuenta su valor sin IGV (cbc:BaseAmount del AllowanceCharge 04/05/06). */
    public record AnticipoCalculado(Anticipo anticipo, BigDecimal base) {
        public String codigo() { return anticipo.codigoSunat(); }
        public BigDecimal monto() { return anticipo.monto(); }
        public BigDecimal importePagado() { return anticipo.importePagado(); }
    }

    public static Totales calcular(List<Item> items) { return calcular(items, null); }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal) { return calcular(items, descuentoGlobal, Icbper.tasaVigente(java.time.LocalDate.of(2023, 1, 1))); }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal, BigDecimal tasaIcbper) { return calcular(items, descuentoGlobal, List.of(), tasaIcbper); }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal, List<Anticipo> anticipos, BigDecimal tasaIcbper) {
        List<ItemCalculado> calculados = items.stream().map(i -> ItemCalculado.de(i, tasaIcbper)).toList();
        // Un subtotal por tributo: la base del IGV incluye el ISC (regla 204); ISC e ICBPER son subtotales propios (reglas 48, 49-A).
        List<SubtotalTributo> brutos = new ArrayList<>();
        for (Tributo tr : Tributo.values()) {
            SubtotalTributo st = switch (tr) {
                case IGV -> new SubtotalTributo(tr, suma(calculados, tr, ItemCalculado::baseIgv), suma(calculados, tr, ItemCalculado::igv));
                case ISC -> new SubtotalTributo(tr, sumaSi(calculados, ItemCalculado::tieneIsc, ItemCalculado::valorVenta), sumaSi(calculados, ItemCalculado::tieneIsc, ItemCalculado::isc));
                case ICBPER -> new SubtotalTributo(tr, z(), sumaSi(calculados, ItemCalculado::tieneIcbper, ItemCalculado::icbper));
                default -> new SubtotalTributo(tr, suma(calculados, tr, ItemCalculado::valorVenta), suma(calculados, tr, ItemCalculado::igv));
            };
            if (st.base().signum() > 0 || st.impuesto().signum() > 0) brutos.add(st);
        }

        DescuentoGlobalCalculado global = null;
        List<SubtotalTributo> subtotales = new ArrayList<>(brutos);
        if (descuentoGlobal != null) {
            if (descuentoGlobal.afectaBaseIgv()) {
                // Código 02: SUNAT lo resta de la base gravada (reglas 46/47), así que exige líneas gravadas onerosas.
                BigDecimal baseGravada = suma(calculados, Tributo.IGV, ItemCalculado::valorVenta);   // sin ISC (regla 46)
                if (baseGravada.signum() == 0)
                    throw new DomainException("DESCUENTO_INVALIDO", "Un descuento global que afecta la base del IGV requiere ítems gravados");
                BigDecimal monto = descuentoGlobal.montoSobre(baseGravada);
                global = new DescuentoGlobalCalculado(descuentoGlobal, baseGravada, monto);
                BigDecimal baseNeta = base(brutos, Tributo.IGV).subtract(monto);   // la base del IGV conserva el ISC (regla 47)
                subtotales.replaceAll(st -> st.tributo() == Tributo.IGV
                        ? new SubtotalTributo(st.tributo(), baseNeta, baseNeta.multiply(ItemCalculado.TASA_IGV).setScale(2, RoundingMode.HALF_UP))
                        : st);
            } else {
                BigDecimal baseOnerosa = suma(calculados, Tributo.IGV, ItemCalculado::valorVenta).add(base(brutos, Tributo.EXO)).add(base(brutos, Tributo.INA));
                global = new DescuentoGlobalCalculado(descuentoGlobal, baseOnerosa, descuentoGlobal.montoSobre(baseOnerosa));
            }
        }

        BigDecimal isc = impuesto(subtotales, Tributo.ISC);
        BigDecimal icbper = impuesto(subtotales, Tributo.ICBPER);
        // Totales brutos (reglas 3278, 3279): los anticipos no los reducen, solo a las bases por tributo y al importe a pagar.
        BigDecimal totalValorVenta = base(subtotales, Tributo.IGV).subtract(isc).add(base(subtotales, Tributo.EXO)).add(base(subtotales, Tributo.INA));
        BigDecimal totalPrecioVenta = totalValorVenta.add(isc).add(icbper).add(impuesto(subtotales, Tributo.IGV));   // regla 55

        List<AnticipoCalculado> aplicados = aplicarAnticipos(subtotales, anticipos == null ? List.of() : anticipos);

        BigDecimal gravado = base(subtotales, Tributo.IGV).subtract(isc);   // valor de venta gravado, sin el ISC que SUNAT suma a la base del IGV
        BigDecimal exonerado = base(subtotales, Tributo.EXO);
        BigDecimal inafecto = base(subtotales, Tributo.INA);
        BigDecimal gratuito = base(subtotales, Tributo.GRA);
        BigDecimal igv = impuesto(subtotales, Tributo.IGV);
        BigDecimal igvGratuitas = impuesto(subtotales, Tributo.GRA);
        BigDecimal totalDescuentos = calculados.stream().map(ItemCalculado::descuentoNoAfectaBase).reduce(z(), BigDecimal::add)
                .add(global != null && !global.afectaBase() ? global.monto() : z());
        BigDecimal totalAnticipos = aplicados.stream().map(AnticipoCalculado::importePagado).reduce(z(), BigDecimal::add);
        BigDecimal total = totalPrecioVenta.subtract(totalDescuentos).subtract(totalAnticipos);
        return new Totales(gravado, exonerado, inafecto, gratuito, igv, igvGratuitas, isc, icbper, totalValorVenta, totalPrecioVenta, totalDescuentos, totalAnticipos, total,
                calculados, List.copyOf(subtotales), global, aplicados);
    }

    /**
     * Resta cada anticipo de la base del tributo de su afectación (04 → 1000, 05 → 9997, 06 → 9998) y recalcula el IGV sobre
     * la base neta (reglas 3277, 3291). Un anticipo no puede superar lo facturado en esa afectación: la base quedaría negativa.
     */
    private static List<AnticipoCalculado> aplicarAnticipos(List<SubtotalTributo> subtotales, List<Anticipo> anticipos) {
        List<AnticipoCalculado> aplicados = new ArrayList<>();
        List<SubtotalTributo> brutos = List.copyOf(subtotales);
        for (Anticipo a : anticipos) {
            Tributo tr = a.afectacion().tributo();
            BigDecimal pendiente = base(subtotales, tr);
            if (a.monto().compareTo(pendiente) > 0)
                throw new DomainException("ANTICIPO_INVALIDO", "El anticipo " + a.comprobante() + " (" + a.monto() + ") supera el valor de venta "
                        + a.afectacion().name().toLowerCase() + " pendiente de esta factura (" + pendiente + ")");
            BigDecimal baseNeta = pendiente.subtract(a.monto());
            BigDecimal impuesto = tr == Tributo.IGV ? baseNeta.multiply(ItemCalculado.TASA_IGV).setScale(2, RoundingMode.HALF_UP) : z();
            subtotales.replaceAll(st -> st.tributo() == tr ? new SubtotalTributo(tr, baseNeta, impuesto) : st);
            aplicados.add(new AnticipoCalculado(a, base(brutos, tr)));
        }
        return List.copyOf(aplicados);
    }

    public boolean tieneAnticipos() { return !anticipos.isEmpty(); }

    public boolean tieneGratuitas() { return gratuito.signum() > 0; }

    private static BigDecimal suma(List<ItemCalculado> items, Tributo tr, Function<ItemCalculado, BigDecimal> monto) {
        return items.stream().filter(i -> i.tributo() == tr).map(monto).reduce(z(), BigDecimal::add);
    }

    private static BigDecimal sumaSi(List<ItemCalculado> items, java.util.function.Predicate<ItemCalculado> cond, Function<ItemCalculado, BigDecimal> monto) {
        return items.stream().filter(cond).map(monto).reduce(z(), BigDecimal::add);
    }

    private static BigDecimal impuesto(List<SubtotalTributo> subtotales, Tributo tr) {
        return subtotales.stream().filter(st -> st.tributo() == tr).map(SubtotalTributo::impuesto).findFirst().orElse(z());
    }

    private static BigDecimal base(List<SubtotalTributo> subtotales, Tributo tr) {
        return subtotales.stream().filter(st -> st.tributo() == tr).map(SubtotalTributo::base).findFirst().orElse(z());
    }

    private static BigDecimal z() { return BigDecimal.ZERO.setScale(2); }
}
