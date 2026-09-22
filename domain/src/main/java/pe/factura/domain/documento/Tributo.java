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
    /** Exportación de bienes o servicios (afectación 40, tipos de operación 0200–0208): sin IGV, categoría G (free export). */
    EXP("9995", "EXP", "FRE", "G"),
    EXO("9997", "EXO", "VAT", "E"),
    INA("9998", "INA", "FRE", "O"),
    /** Operaciones gratuitas: su IGV se informa pero no se cobra ni entra en los totales a pagar. */
    GRA("9996", "GRA", "FRE", "Z");

    private final String codigo, nombre, tipoInternacional, categoria;
}
