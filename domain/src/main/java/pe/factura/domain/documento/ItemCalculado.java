package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Importes de una línea calculados por khipu a partir del precio que envía el emisor.
 * <ul>
 *   <li>Onerosas: el precio es el precio de venta unitario con IGV (gravadas) o sin él (exoneradas/inafectas).
 *       {@code valorUnitario} es el valor sin IGV (cac:Price/PriceAmount). Si la línea lleva ISC o ICBPER, el precio
 *       enviado los incluye también: khipu los separa (ISC entra en la base del IGV, regla 204).</li>
 *   <li>Gratuitas: el precio es el <em>valor referencial</em> unitario sin IGV (PricingReference, código 02);
 *       {@code valorUnitario} es 0 (regla 2640) y la línea no suma al importe a pagar.</li>
 *   <li>{@code baseBruta}: valor × cantidad antes de descuentos y cargos; {@code valorVenta}: LineExtensionAmount
 *       (bruta − descuento 00 + cargos 47, regla 38); {@code isc}, {@code icbper}: tributos adicionales; {@code igv} sobre valorVenta + isc.</li>
 *   <li>{@code cargos}: cargos de línea calculados sobre la base bruta; los 48 no tocan el IGV y van a ChargeTotalAmount.</li>
 *   <li>{@code precioVenta}: lo que paga el cliente por la línea (con cargos 48, sin descuento 01); {@code precioVentaUnitario} (regla 33).</li>
 * </ul>
 */
public record ItemCalculado(Item item, BigDecimal valorUnitario, BigDecimal baseBruta, BigDecimal descuento, boolean descuentoAfectaBase,
                            BigDecimal valorVenta, BigDecimal isc, BigDecimal iscPorcentaje, BigDecimal iscBase, BigDecimal icbper, BigDecimal icbperUnitario,
                            BigDecimal igv, BigDecimal precioVenta, BigDecimal precioVentaUnitario, BigDecimal porcentajeIgv, List<CargoCalculado> cargos) {

    private static final BigDecimal CIEN = new BigDecimal("100");

    public static ItemCalculado de(Item item) { return de(item, Icbper.tasaVigente(java.time.LocalDate.of(2023, 1, 1))); }

    public static ItemCalculado de(Item item, BigDecimal tasaIcbper) { return de(item, tasaIcbper, TasaIgv.GENERAL); }

    /** {@code tasaIgv} en porcentaje (18.00 o la reducida del padrón): decide el IGV de la línea y el cbc:Percent del XML. */
    public static ItemCalculado de(Item item, BigDecimal tasaIcbper, BigDecimal tasaIgv) {
        TipoAfectacionIgv af = item.afectacion();
        // Una línea IVAP (17) tributa el 4 % del IVAP en vez del IGV: misma mecánica de precio con impuesto incluido.
        BigDecimal tasaLinea = af.ivap() ? TasaIgv.IVAP : tasaIgv;
        BigDecimal factorIgv = TasaIgv.factor(tasaLinea);
        BigDecimal unoMasIgv = BigDecimal.ONE.add(factorIgv);
        if (af.ivap() && (item.tieneIsc() || item.icbper()))
            throw new DomainException("AFECTACION_INVALIDA", "2650 - Una línea afecta al IVAP (17) no lleva ISC ni ICBPER (combinación de tributos no permitida, 3223)");
        if (af.exportacion() && (item.tieneIsc() || item.icbper()))
            throw new DomainException("AFECTACION_INVALIDA", "3223 - Una línea de exportación (40) no lleva ISC ni ICBPER (combinación de tributos no permitida)");
        boolean onerosaGravada = af.gravado() && !af.gratuita();
        BigDecimal cantidad = item.cantidad();
        // ICBPER: monto fijo por unidad, fuera de la base del IGV; el precio enviado lo incluye.
        BigDecimal icbperUnitario = item.icbper() ? tasaIcbper : BigDecimal.ZERO.setScale(2);
        BigDecimal icbper = item.icbper() ? tasaIcbper.multiply(cantidad).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal precioSinIcbper = item.precioUnitario().subtract(icbperUnitario);
        // Precio con IGV e ISC → valor sin tributos: precio = valor × (1 + isc%) × (1 + igv) (sistemas 01/03) o (valor + iscFijo) × (1 + igv) (02).
        BigDecimal valorReferencial;
        if (!onerosaGravada) valorReferencial = precioSinIcbper.setScale(10, RoundingMode.HALF_UP);
        else if (item.tieneIsc() && !"01".equals(item.isc().sistema())) valorReferencial = precioSinIcbper.divide(unoMasIgv, 10, RoundingMode.HALF_UP).subtract(item.isc().montoUnitarioEfectivo());
        else if (item.tieneIsc()) valorReferencial = precioSinIcbper.divide(unoMasIgv, 10, RoundingMode.HALF_UP).divide(BigDecimal.ONE.add(item.isc().tasa().divide(CIEN, 10, RoundingMode.HALF_UP)), 10, RoundingMode.HALF_UP);
        else valorReferencial = precioSinIcbper.divide(unoMasIgv, 10, RoundingMode.HALF_UP);
        BigDecimal baseBruta = valorReferencial.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
        BigDecimal descuento = item.tieneDescuento() ? item.descuento().montoSobre(baseBruta) : BigDecimal.ZERO.setScale(2);
        boolean afectaBase = item.tieneDescuento() && item.descuento().afectaBaseIgv();
        if (item.tieneCargos() && af.gratuita())
            throw new DomainException("CARGO_INVALIDO", "Una línea gratuita (afectación " + af.codigo() + ") no admite cargos: no se cobra");
        List<CargoCalculado> cargos = item.cargos().stream().map(cg -> CargoCalculado.de(cg, baseBruta)).toList();
        BigDecimal cargosAfectanBase = sumaCargos(cargos, true);
        BigDecimal valorVenta = (afectaBase ? baseBruta.subtract(descuento) : baseBruta).add(cargosAfectanBase);
        BigDecimal isc = item.tieneIsc() && !af.gratuita() ? item.isc().montoSobre(valorVenta, cantidad) : BigDecimal.ZERO.setScale(2);
        BigDecimal iscBase = item.tieneIsc() && !af.gratuita() ? item.isc().baseSobre(valorVenta, cantidad) : BigDecimal.ZERO.setScale(2);
        BigDecimal iscPorcentaje = item.tieneIsc() && !af.gratuita() ? item.isc().porcentajeSobre(iscBase, isc) : BigDecimal.ZERO;
        BigDecimal igv = af.gravado() ? valorVenta.add(isc).multiply(factorIgv).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        // 3111 (Factura2_0, NotaCredito2_0 f211, NotaDebito2_0 f192): con tributo 1000/1016 y base > 0.06, el impuesto de la
        // línea no puede ser 0.00. Solo pasa con el IVAP al 4 % (base 0.07–0.12); antes salía numerada y SUNAT la rechazaba.
        if (onerosaGravada && valorVenta.add(isc).compareTo(new BigDecimal("0.06")) > 0 && igv.signum() == 0)
            throw new DomainException("ITEM_INVALIDO", "3111 - Con base imponible mayor a 0.06 el " + (af.ivap() ? "IVAP" : "IGV") + " de la línea «" + item.descripcion() + "» no puede redondear a 0.00: suba el importe");
        BigDecimal descuentoNoAfecta = item.tieneDescuento() && !afectaBase ? descuento : BigDecimal.ZERO;
        BigDecimal precioVenta = af.gratuita() ? BigDecimal.ZERO.setScale(2) : valorVenta.add(isc).add(igv).add(icbper).subtract(descuentoNoAfecta).add(sumaCargos(cargos, false));
        BigDecimal valorUnitario = af.gratuita() ? BigDecimal.ZERO.setScale(10) : valorReferencial;
        BigDecimal precioVentaUnitario = af.gratuita() ? valorReferencial : precioVenta.divide(cantidad, 10, RoundingMode.HALF_UP);
        BigDecimal pct = af.gravado() ? tasaLinea : new BigDecimal("0.00");
        return new ItemCalculado(item, valorUnitario, baseBruta, descuento, afectaBase, valorVenta, isc, iscPorcentaje, iscBase, icbper, icbperUnitario,
                igv, precioVenta, precioVentaUnitario, pct, cargos);
    }

    private static BigDecimal sumaCargos(List<CargoCalculado> cargos, boolean afectanBase) {
        return cargos.stream().filter(cg -> cg.afectaBase() == afectanBase).map(CargoCalculado::monto).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Factor SUNAT del descuento de línea (MultiplierFactorNumeric): monto / base bruta; vacío si no reproduce el monto (regla 3290). */
    public java.util.Optional<BigDecimal> descuentoFactor() { return FactorSunat.de(descuento, baseBruta); }

    /** Monto que reduce lo que se paga sin tocar el IGV (código 01): entra en AllowanceTotalAmount. */
    public BigDecimal descuentoNoAfectaBase() { return item.tieneDescuento() && !descuentoAfectaBase ? descuento : BigDecimal.ZERO.setScale(2); }
    /** Cargos que se cobran sin IGV (código 48): entran en ChargeTotalAmount (regla 3301). */
    public BigDecimal cargoNoAfectaBase() { return sumaCargos(cargos, false); }

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

    /**
     * Sistema 03 (#68, regla 3108): el PVP sugerido no puede ser menor que el valor unitario sin tributos. Se exige
     * solo al emitir ({@link Comprobante.FacturaBuilder#crear}/{@link Comprobante.NotaBuilder#crear}); nunca al
     * rehidratar un comprobante ya persistido, para no revalidar contra una regla que pudo cambiar después (#89).
     */
    public void exigirBasePvpValida() {
        if (item.tieneIsc() && "03".equals(item.isc().sistema()) && valorUnitario.compareTo(item.isc().basePvp()) > 0)
            throw new DomainException("ISC_INVALIDO", "El PVP sugerido (base_pvp " + item.isc().basePvp() + ") no puede ser menor que el valor unitario sin tributos (" + valorUnitario.setScale(2, RoundingMode.HALF_UP) + ")");
    }
}
