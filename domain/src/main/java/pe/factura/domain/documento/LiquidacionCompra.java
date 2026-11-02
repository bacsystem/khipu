package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Lo propio de una liquidación de compra (04, #327; hoja LiquidacionCompra2_0): el comprador —el emisor, con RUC— la emite a quien le vende sin RUC (agricultor, pescador
 * artesanal, recolector) y le retiene el Impuesto a la Renta. El vendedor va en {@link Comprobante#receptor()} (documento, nombre y la dirección de su domicilio); aquí
 * quedan el resto de su domicilio (ubigeo y condición), el lugar donde se realizó la operación y la tasa de la retención.
 * <ul>
 *   <li>{@code ubigeoVendedor}, {@code condicionVendedor}: {@code RegistrationAddress/cbc:ID} (2453; 4339 si no está en el catálogo 13) y {@code cbc:AddressTypeCode}
 *       (catálogo 60: 2456, 2457) del domicilio del vendedor.</li>
 *   <li>{@code lugarOperacion}: {@code cac:DeliveryTerms/cac:DeliveryLocation} (2454, 2778, 2455, 2917, 2458, 2459).</li>
 *   <li>{@code tasaRetencionRenta}: porcentaje del tributo 3000 de cada línea (2464, 4237).</li>
 * </ul>
 */
public record LiquidacionCompra(String ubigeoVendedor, String condicionVendedor, Ubicacion lugarOperacion, BigDecimal tasaRetencionRenta) {

    /**
     * Retención del Impuesto a la Renta que hace el comprador al emitir la liquidación de compra: 1.5 % del importe de la operación como pago a cuenta del vendedor (régimen de
     * retenciones del IR de las liquidaciones de compra, la tasa que SUNAT difunde en su orientación sobre el comprobante). La hoja no publica la lista: solo dice que la tasa
     * debe ser «alguna de las tasas vigentes de retención de renta a la fecha de emisión» y que si no lo es la observa (4237). khipu admite solo esta, así que un comprobante con
     * otra tasa no llega a SUNAT; si la norma cambia, se agrega aquí. e-beta (2026-10-20) aceptó el 1.50 sin observaciones y observó el 2.00 con la 4237.
     */
    public static final BigDecimal TASA_RETENCION_RENTA = new BigDecimal("1.5");
    static final Set<BigDecimal> TASAS_RETENCION_RENTA = Set.of(TASA_RETENCION_RENTA);

    /**
     * Catálogo 51 de la liquidación de compra: 0501 compra interna y 0502 anticipos, que la hoja no distingue con ninguna regla. Queda fuera el 0503 (compra de oro): exige en
     * cada línea las propiedades 6000, 6004, 6005 y 6006 del catálogo 55 (código, ley mineral, naturaleza y nombre del derecho minero; reglas 2466–2469, 3064) que khipu no arma
     * y SUNAT lo rechazaría con el número ya consumido, igual que hizo #394 con los tipos de la factura.
     */
    static final Set<String> OPERACIONES = Set.of("0501", "0502");
    static final String OPERACION_POR_DEFECTO = "0501";
    static final String OPERACION_ORO = "0503";

    public LiquidacionCompra {
        ubigeoVendedor = ubigeoVendedor == null ? null : ubigeoVendedor.strip();
        condicionVendedor = condicionVendedor == null ? null : condicionVendedor.strip();
        tasaRetencionRenta = tasaRetencionRenta == null ? TASA_RETENCION_RENTA : tasaRetencionRenta;
    }

    /**
     * Lugar donde se realiza la operación (dirección y datos que permitan ubicarlo, ubigeo y su condición, catálogo 60). La dirección se guarda sin los espacios de los extremos:
     * la regla 2778 rechaza la que empieza con espacio o tabulador.
     */
    public record Ubicacion(String direccion, String ubigeo, String condicion) {
        public Ubicacion {
            direccion = direccion == null ? null : direccion.strip();
            ubigeo = ubigeo == null ? null : ubigeo.strip();
            condicion = condicion == null ? null : condicion.strip();
        }
    }

    /** Valida lo que no es del vendedor como persona (eso lo hace {@link Receptor}): ubigeo y condición de su domicilio, el lugar de la operación y la tasa. Solo al emitir. */
    void validar() {
        exigirUbigeo(ubigeoVendedor, "2453", "4339", "del domicilio del vendedor");
        exigirCondicion(condicionVendedor, "2456", "2457", "del domicilio del vendedor");
        if (lugarOperacion == null)
            throw new DomainException("LIQUIDACION_INVALIDA", "2454 - La liquidación de compra lleva el lugar donde se realiza la operación (dirección, ubigeo y condición)");
        exigirDireccion(lugarOperacion.direccion(), "2454", "2778", "del lugar de la operación", "LIQUIDACION_INVALIDA");
        exigirUbigeo(lugarOperacion.ubigeo(), "2455", "2917", "del lugar de la operación");
        exigirCondicion(lugarOperacion.condicion(), "2458", "2459", "del lugar de la operación");
        if (TASAS_RETENCION_RENTA.stream().noneMatch(t -> t.compareTo(tasaRetencionRenta) == 0))
            throw new DomainException("LIQUIDACION_INVALIDA", "4237 - La tasa de retención del Impuesto a la Renta debe ser una de las vigentes ("
                    + String.join(", ", TASAS_RETENCION_RENTA.stream().map(BigDecimal::toPlainString).sorted().toList()) + " %); recibida " + tasaRetencionRenta.toPlainString());
    }

    /** 2452/2593 (domicilio del vendedor) y 2454/2778 (lugar): de 3 a 250 caracteres en una sola línea; sin espacios al inicio porque se guardan sin ellos. */
    static void exigirDireccion(String direccion, String reglaFalta, String reglaFormato, String de, String codigoError) {
        if (direccion == null || direccion.isEmpty())
            throw new DomainException(codigoError, reglaFalta + " - Falta la dirección " + de);
        if (direccion.length() < 3 || direccion.length() > 250 || direccion.chars().anyMatch(Character::isISOControl))
            throw new DomainException(codigoError, reglaFormato + " - La dirección " + de + " tiene de 3 a 250 caracteres, sin saltos de línea ni tabuladores");
    }

    /**
     * El ubigeo es obligatorio y debe existir en el catálogo 13. Para el lugar de la operación SUNAT rechaza el que no existe (2917); para el domicilio del vendedor solo lo
     * observa (4339), pero khipu lo exige igual: un domicilio que no se puede ubicar no sustenta la compra.
     */
    private static void exigirUbigeo(String ubigeo, String reglaFalta, String reglaCatalogo, String de) {
        if (ubigeo == null || ubigeo.isEmpty())
            throw new DomainException("LIQUIDACION_INVALIDA", reglaFalta + " - Falta el ubigeo " + de + " (catálogo 13, 6 dígitos)");
        if (!ubigeo.matches("\\d{6}") || !CatalogoSunat.porId("13").map(c -> c.contiene(ubigeo)).orElse(false))
            throw new DomainException("LIQUIDACION_INVALIDA", reglaCatalogo + " - El ubigeo " + de + " no existe en el catálogo 13 (INEI): " + ubigeo);
    }

    private static void exigirCondicion(String condicion, String reglaFalta, String reglaCatalogo, String de) {
        if (condicion == null || condicion.isEmpty())
            throw new DomainException("LIQUIDACION_INVALIDA", reglaFalta + " - Falta la condición " + de + " (catálogo 60: 01 punto de venta, 02 producción, 03 extracción, 04 explotación, 05 otros)");
        if (!CatalogoSunat.porId("60").map(c -> c.contiene(condicion)).orElse(false))
            throw new DomainException("LIQUIDACION_INVALIDA", reglaCatalogo + " - La condición " + de + " debe ser del catálogo 60 (01 punto de venta, 02 producción, 03 extracción, 04 explotación, 05 otros); recibida " + condicion);
    }

    /** Retención de renta de una línea: su valor de venta × la tasa, al céntimo (2464, tolerancia ±1). */
    public BigDecimal rentaDe(ItemCalculado linea) {
        return linea.valorVenta().multiply(tasaRetencionRenta).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    /** Base de la retención global: el valor de venta de las líneas, que en una liquidación de compra son todas onerosas (4345). */
    public BigDecimal baseRetencion(Totales totales) {
        return totales.items().stream().filter(i -> !i.gratuita()).map(ItemCalculado::valorVenta).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Importe global de la retención: la suma de la de cada línea, no base × tasa, para que coincida con lo que SUNAT suma (4346). */
    public BigDecimal retencionRenta(Totales totales) {
        return totales.items().stream().filter(i -> !i.gratuita()).map(this::rentaDe).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
