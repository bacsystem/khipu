package pe.factura.domain.documento;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tributos del catálogo 05 que khipu escribe en cac:TaxScheme: código, nombre, tipo internacional (UN/ECE 5153)
 * y categoría (UN/ECE 5305). Un subtotal global (cac:TaxTotal/cac:TaxSubtotal) por cada tributo con base > 0.
 */
@Getter
@RequiredArgsConstructor
public enum Tributo {
    IGV("1000", "IGV", "VAT", "S"),
    /** Impuesto a la Venta de Arroz Pilado (Ley 28211): 4 % en lugar del IGV; afectación 17 del catálogo 07. */
    IVAP("1016", "IVAP", "VAT", "S"),
    /** Impuesto Selectivo al Consumo: forma parte de la base del IGV. */
    ISC("2000", "ISC", "EXC", "S"),
    /** Impuesto a las bolsas de plástico: monto fijo por unidad, sin tasa. */
    ICBPER("7152", "ICBPER", "OTH", "S"),
    /** Otros tributos de la línea (#317): una tasa sobre el valor de venta que suma al total y no entra en la base del IGV. */
    OTROS("9999", "OTROS", "OTH", "S"),
    /** Exportación de bienes o servicios (afectación 40, tipos de operación 0200–0208): sin IGV, categoría G (free export). */
    EXP("9995", "EXP", "FRE", "G"),
    EXO("9997", "EXO", "VAT", "E"),
    INA("9998", "INA", "FRE", "O"),
    /** Operaciones gratuitas: su IGV se informa pero no se cobra ni entra en los totales a pagar. */
    GRA("9996", "GRA", "FRE", "Z"),
    /**
     * Retención del Impuesto a la Renta de la liquidación de compra (#327): el comprador la retiene al vendedor sobre el valor de venta de cada línea. Nunca es la afectación
     * de un ítem (ningún {@link TipoAfectacionIgv} lo usa), así que {@link Totales} no le arma subtotal; la plantilla de la liquidación lo escribe aparte. Nombre «IR» y código
     * internacional «TOX» del catálogo 05 (reglas 3051, 2377 y 2964, 2961); la categoría UN/ECE 5305 no tiene regla SUNAT.
     */
    RENTA("3000", "IR", "TOX", "S");

    private final String codigo, nombre, tipoInternacional, categoria;
}
