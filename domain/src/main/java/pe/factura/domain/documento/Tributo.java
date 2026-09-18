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
    EXO("9997", "EXO", "VAT", "E"),
    INA("9998", "INA", "FRE", "O"),
    /** Operaciones gratuitas: su IGV se informa pero no se cobra ni entra en los totales a pagar. */
    GRA("9996", "GRA", "FRE", "Z");

    private final String codigo, nombre, tipoInternacional, categoria;
}
