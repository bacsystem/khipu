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
 * por tributo, del descuento global y de los cargos globales.
 * <ul>
 *   <li>{@code gravado/exonerado/inafecto}: bases onerosas por tributo (1000/9997/9998), netas de descuentos de línea 00
 *       y del global 02. {@code gratuito}: base de las operaciones gratuitas (9996), que no se cobra.</li>
 *   <li>{@code igv}: IGV que se cobra (tributo 1000). {@code igvGratuitas}: IGV de las gratuitas, solo informativo.</li>
 *   <li>{@code totalValorVenta}: suma de bases onerosas (LineExtensionAmount global, regla 54).</li>
 *   <li>{@code totalPrecioVenta}: valor de venta + IGV (TaxInclusiveAmount).</li>
 *   <li>{@code totalDescuentos}: descuentos que no afectan la base (línea 01 + global 03) — AllowanceTotalAmount.</li>
 *   <li>{@code totalCargos}: cargos que no afectan la base (línea 48 + globales 46/50) — ChargeTotalAmount (regla 3301).</li>
 *   <li>{@code totalAnticipos}: importes ya pagados con facturas de anticipo, IGV incluido (PrepaidAmount, regla 66).</li>
 *   <li>{@code redondeo}: ajuste del importe total, |x| ≤ 1 (PayableRoundingAmount, regla 3303), para cobrar en efectivo sin céntimos.</li>
 *   <li>{@code total}: importe a pagar (PayableAmount) = precio de venta + cargos − descuentos que no afectan la base − anticipos + redondeo (regla 3280).</li>
 * </ul>
 * Con anticipos, SUNAT resta su valor sin IGV de la base del tributo que corresponda (04 gravado, 05 exonerado, 06 inafecto:
 * reglas 3277, 3291) pero no del total valor/precio de venta (3278, 3279): por eso {@code gravado/exonerado/inafecto} y el
 * {@code igv} son netos de anticipos mientras {@code totalValorVenta} y {@code totalPrecioVenta} son brutos.
 */
public record Totales(BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto, BigDecimal gratuito, BigDecimal igv, BigDecimal igvGratuitas,
                      BigDecimal ivap, BigDecimal exportacion, BigDecimal isc, BigDecimal icbper,
                      BigDecimal totalValorVenta, BigDecimal totalPrecioVenta, BigDecimal totalDescuentos, BigDecimal totalCargos, BigDecimal totalAnticipos, BigDecimal redondeo, BigDecimal total,
                      List<ItemCalculado> items, List<SubtotalTributo> subtotales, DescuentoGlobalCalculado descuentoGlobal, List<CargoCalculado> cargosGlobales,
                      List<AnticipoCalculado> anticipos, BigDecimal tasaIgv) {

    /** Base imponible e impuesto acumulados de los ítems de un mismo tributo (catálogo 05). */
    public record SubtotalTributo(Tributo tributo, BigDecimal base, BigDecimal impuesto) {}

    /** Descuento global aplicado: monto, base sobre la que se calculó y factor SUNAT (catálogo 53: 02 afecta base, 03 no). */
    public record DescuentoGlobalCalculado(Descuento descuento, BigDecimal base, BigDecimal monto) {
        /** Factor para el XML, vacío cuando el redondeo a 5 decimales no reproduce el monto (regla 3307). */
        public java.util.Optional<BigDecimal> factor() { return FactorSunat.de(monto, base); }
        public String codigo() { return descuento.codigoSunat(true); }
        public boolean afectaBase() { return descuento.afectaBaseIgv(); }
    }

    /** Anticipo aplicado: base bruta del tributo sobre la que se descuenta su valor sin IGV (cbc:BaseAmount del AllowanceCharge 04/05/06). */
    public record AnticipoCalculado(Anticipo anticipo, BigDecimal base, BigDecimal tasaIgv) {
        public String codigo() { return anticipo.codigoSunat(); }
        public BigDecimal monto() { return anticipo.monto(); }
        public BigDecimal importePagado() { return anticipo.importePagado(tasaIgv); }
    }

    public static Totales calcular(List<Item> items) { return calcular(items, null); }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal) { return calcular(items, descuentoGlobal, Icbper.tasaVigente(java.time.LocalDate.of(2023, 1, 1))); }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal, BigDecimal tasaIcbper) { return calcular(items, descuentoGlobal, List.of(), tasaIcbper); }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal, List<Anticipo> anticipos, BigDecimal tasaIcbper) {
        return calcular(items, descuentoGlobal, List.of(), anticipos, tasaIcbper);
    }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal, List<Cargo> cargosGlobales, List<Anticipo> anticipos, BigDecimal tasaIcbper) {
        return calcular(items, descuentoGlobal, cargosGlobales, anticipos, tasaIcbper, null);
    }

    public static Totales calcular(List<Item> items, Descuento descuentoGlobal, List<Cargo> cargosGlobales, List<Anticipo> anticipos, BigDecimal tasaIcbper, BigDecimal redondeo) {
        return calcular(items, descuentoGlobal, cargosGlobales, anticipos, tasaIcbper, redondeo, TasaIgv.GENERAL);
    }

    /** {@code tasaIgv} en porcentaje ({@link TasaIgv}): la misma para todas las líneas gravadas del comprobante (regla 3462). */
    public static Totales calcular(List<Item> items, Descuento descuentoGlobal, List<Cargo> cargosGlobales, List<Anticipo> anticipos, BigDecimal tasaIcbper, BigDecimal redondeo, BigDecimal tasaIgv) {
        if (redondeo != null && (redondeo.scale() > 2 || redondeo.abs().compareTo(BigDecimal.ONE) > 0))
            throw new DomainException("REDONDEO_INVALIDO", "3303 - El redondeo del importe total admite 2 decimales y no puede superar 1.00 en valor absoluto");
        BigDecimal ajuste = redondeo == null ? z() : redondeo.setScale(2, RoundingMode.HALF_UP);
        List<ItemCalculado> calculados = items.stream().map(i -> ItemCalculado.de(i, tasaIcbper, tasaIgv)).toList();
        // IVAP (Ley 28211): el comprobante entero tributa IVAP; SUNAT calcula su total precio de venta sin IGV (campo 55, 3279 IVAP),
        // así que no se mezcla con líneas gravadas/exoneradas/inafectas ni gratuitas.
        boolean hayIvap = calculados.stream().anyMatch(i -> i.item().afectacion().ivap());
        if (hayIvap && calculados.stream().anyMatch(i -> !i.item().afectacion().ivap()))
            throw new DomainException("AFECTACION_INVALIDA", "Un comprobante afecto al IVAP (17) no admite ítems con otra afectación (10, 20, 30 ni gratuitas)");
        // Exportación (afectación 40, tributo 9995): todas las líneas o ninguna (2642, 3107); descuentos y cargos globales van sobre su base (3273).
        boolean hayExportacion = calculados.stream().anyMatch(i -> i.item().afectacion().exportacion());
        if (hayExportacion && calculados.stream().anyMatch(i -> !i.item().afectacion().exportacion()))
            throw new DomainException("AFECTACION_INVALIDA", "3107 - Una factura de exportación (afectación 40) no admite ítems con otra afectación (10, 17, 20, 30 ni gratuitas)");
        Tributo gravadoCon = hayIvap ? Tributo.IVAP : Tributo.IGV;
        BigDecimal tasaGravado = hayIvap ? TasaIgv.IVAP : tasaIgv;
        BigDecimal factorIgv = TasaIgv.factor(tasaGravado);
        // Un subtotal por tributo. Para el IGV, SUNAT pide la base global SIN ISC (suma de LineExtensionAmount, regla 3277) aunque el
        // impuesto se calcule sobre las bases de línea CON ISC (reglas 204 y 3291); ISC e ICBPER son subtotales propios (reglas 48, 49-A).
        List<SubtotalTributo> brutos = new ArrayList<>();
        for (Tributo tr : Tributo.values()) {
            SubtotalTributo st = switch (tr) {
                case IGV -> new SubtotalTributo(tr, suma(calculados, tr, ItemCalculado::valorVenta), suma(calculados, tr, ItemCalculado::igv));
                case ISC -> new SubtotalTributo(tr, sumaSi(calculados, ItemCalculado::tieneIsc, ItemCalculado::iscBase), sumaSi(calculados, ItemCalculado::tieneIsc, ItemCalculado::isc));
                case ICBPER -> new SubtotalTributo(tr, z(), sumaSi(calculados, ItemCalculado::tieneIcbper, ItemCalculado::icbper));
                default -> new SubtotalTributo(tr, suma(calculados, tr, ItemCalculado::valorVenta), suma(calculados, tr, ItemCalculado::igv));
            };
            if (st.base().signum() > 0 || st.impuesto().signum() > 0) brutos.add(st);
        }

        BigDecimal isc = impuesto(brutos, Tributo.ISC);
        BigDecimal icbper = impuesto(brutos, Tributo.ICBPER);
        DescuentoGlobalCalculado global = null;
        List<SubtotalTributo> subtotales = new ArrayList<>(brutos);
        BigDecimal baseGravada = base(brutos, gravadoCon);
        BigDecimal baseOnerosa = baseGravada.add(base(brutos, Tributo.EXO)).add(base(brutos, Tributo.INA)).add(base(brutos, Tributo.EXP));
        // Descuento 02 y cargos 49 se calculan sobre la base gravada bruta y la ajustan (reglas 3277, 3278, 3291); exigen líneas gravadas onerosas.
        BigDecimal ajusteBaseGravada = z();
        if (descuentoGlobal != null) {
            if (descuentoGlobal.afectaBaseIgv()) {
                if (baseGravada.signum() == 0)
                    throw new DomainException("DESCUENTO_INVALIDO", "Un descuento global que afecta la base del IGV requiere ítems gravados");
                global = new DescuentoGlobalCalculado(descuentoGlobal, baseGravada, descuentoGlobal.montoSobre(baseGravada));
                ajusteBaseGravada = ajusteBaseGravada.subtract(global.monto());
            } else {
                global = new DescuentoGlobalCalculado(descuentoGlobal, baseOnerosa, descuentoGlobal.montoSobre(baseOnerosa));
            }
        }
        List<CargoCalculado> cargos = new ArrayList<>();
        for (Cargo cg : cargosGlobales == null ? List.<Cargo>of() : cargosGlobales) {
            if (!cg.global())
                throw new DomainException("CARGO_INVALIDO", "4291 - Un cargo global debe usar los códigos 46, 49 o 50 del catálogo 53");
            if (cg.afectaBaseIgv() && baseGravada.signum() == 0)
                throw new DomainException("CARGO_INVALIDO", "Un cargo global que afecta la base del IGV (49) requiere ítems gravados");
            CargoCalculado calculado = CargoCalculado.de(cg, cg.afectaBaseIgv() ? baseGravada : baseOnerosa);
            if (calculado.afectaBase()) ajusteBaseGravada = ajusteBaseGravada.add(calculado.monto());
            cargos.add(calculado);
        }
        if (ajusteBaseGravada.signum() != 0) {
            BigDecimal baseNeta = baseGravada.add(ajusteBaseGravada);
            subtotales.replaceAll(st -> st.tributo() == gravadoCon ? new SubtotalTributo(st.tributo(), baseNeta, igvSobre(baseNeta, isc, factorIgv)) : st);
        }

        // Totales brutos (reglas 3278, 3279): los anticipos no los reducen, solo a las bases por tributo y al importe a pagar.
        BigDecimal totalValorVenta = base(subtotales, gravadoCon).add(base(subtotales, Tributo.EXO)).add(base(subtotales, Tributo.INA)).add(base(subtotales, Tributo.EXP));
        BigDecimal totalPrecioVenta = totalValorVenta.add(isc).add(icbper).add(impuesto(subtotales, gravadoCon));   // regla 55 (IGV o IVAP)

        List<AnticipoCalculado> aplicados = aplicarAnticipos(subtotales, anticipos == null ? List.of() : anticipos, isc, tasaGravado, factorIgv, gravadoCon);

        BigDecimal gravado = base(subtotales, gravadoCon);
        BigDecimal exonerado = base(subtotales, Tributo.EXO);
        BigDecimal inafecto = base(subtotales, Tributo.INA);
        BigDecimal gratuito = base(subtotales, Tributo.GRA);
        BigDecimal igv = impuesto(subtotales, Tributo.IGV);
        BigDecimal ivap = impuesto(subtotales, Tributo.IVAP);
        BigDecimal exportacion = base(subtotales, Tributo.EXP);
        BigDecimal igvGratuitas = impuesto(subtotales, Tributo.GRA);
        BigDecimal totalDescuentos = calculados.stream().map(ItemCalculado::descuentoNoAfectaBase).reduce(z(), BigDecimal::add)
                .add(global != null && !global.afectaBase() ? global.monto() : z());
        BigDecimal totalCargos = calculados.stream().map(ItemCalculado::cargoNoAfectaBase).reduce(z(), BigDecimal::add)
                .add(cargos.stream().filter(cg -> !cg.afectaBase()).map(CargoCalculado::monto).reduce(z(), BigDecimal::add));
        BigDecimal totalAnticipos = aplicados.stream().map(AnticipoCalculado::importePagado).reduce(z(), BigDecimal::add);
        BigDecimal total = totalPrecioVenta.add(totalCargos).subtract(totalDescuentos).subtract(totalAnticipos).add(ajuste);
        if (total.signum() < 0)
            throw new DomainException("REDONDEO_INVALIDO", "3303 - El redondeo deja el importe total en negativo (" + total + ")");
        return new Totales(gravado, exonerado, inafecto, gratuito, igv, igvGratuitas, ivap, exportacion, isc, icbper, totalValorVenta, totalPrecioVenta, totalDescuentos, totalCargos, totalAnticipos, ajuste, total,
                calculados, List.copyOf(subtotales), global, List.copyOf(cargos), aplicados, tasaIgv);
    }

    /**
     * Resta cada anticipo de la base del tributo de su afectación (04 → 1000, 05 → 9997, 06 → 9998) y recalcula el IGV sobre
     * la base neta (reglas 3277, 3291). Un anticipo no puede superar lo facturado en esa afectación: la base quedaría negativa.
     */
    private static List<AnticipoCalculado> aplicarAnticipos(List<SubtotalTributo> subtotales, List<Anticipo> anticipos, BigDecimal isc, BigDecimal tasaIgv, BigDecimal factorIgv, Tributo gravadoCon) {
        List<AnticipoCalculado> aplicados = new ArrayList<>();
        List<SubtotalTributo> brutos = List.copyOf(subtotales);
        for (Anticipo a : anticipos) {
            Tributo tr = a.afectacion().tributo() == Tributo.IGV ? gravadoCon : a.afectacion().tributo();
            BigDecimal pendiente = base(subtotales, tr);
            if (a.monto().compareTo(pendiente) > 0)
                throw new DomainException("ANTICIPO_INVALIDO", "El anticipo " + a.comprobante() + " (" + a.monto() + ") supera el valor de venta "
                        + a.afectacion().name().toLowerCase() + " pendiente de esta factura (" + pendiente + ")");
            BigDecimal baseNeta = pendiente.subtract(a.monto());
            BigDecimal impuesto = tr == gravadoCon ? igvSobre(baseNeta, isc, factorIgv) : z();
            subtotales.replaceAll(st -> st.tributo() == tr ? new SubtotalTributo(tr, baseNeta, impuesto) : st);
            aplicados.add(new AnticipoCalculado(a, base(brutos, tr), tasaIgv));
        }
        return List.copyOf(aplicados);
    }

    public boolean tieneAnticipos() { return !anticipos.isEmpty(); }

    public boolean tieneCargosGlobales() { return !cargosGlobales.isEmpty(); }

    public boolean tieneRedondeo() { return redondeo.signum() != 0; }

    public boolean tieneGratuitas() { return gratuito.signum() > 0; }

    public boolean esExportacion() { return exportacion.signum() > 0; }

    /** Comprobante afecto al IVAP: lleva la leyenda 2007 y su total precio de venta no incluye IGV (campo 55). */
    public boolean tieneIvap() { return ivap.signum() > 0 || subtotales.stream().anyMatch(st -> st.tributo() == Tributo.IVAP); }

    /** IGV global (regla 3291): (bases de línea con ISC − descuentos que afectan la base) × tasa = (base sin ISC + ISC) × tasa del comprobante. */
    private static BigDecimal igvSobre(BigDecimal baseSinIsc, BigDecimal isc, BigDecimal factorIgv) {
        return baseSinIsc.add(isc).multiply(factorIgv).setScale(2, RoundingMode.HALF_UP);
    }

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
