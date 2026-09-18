package pe.factura.domain.documento;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TipoAfectacionIgv {
    // Catálogo 07 (afectación) → catálogo 05 (tributo: código, nombre, tipo internacional y categoría UN/ECE 5305).
    GRAVADO("10", "1000", "IGV", "VAT", "S"), EXONERADO("20", "9997", "EXO", "VAT", "E"), INAFECTO("30", "9998", "INA", "FRE", "O");

    private final String codigo, tributoId, tributoNombre, tributoTipo, categoria;
    public boolean gravado() { return this == GRAVADO; }
    public static TipoAfectacionIgv porCodigo(String c) {
        for (TipoAfectacionIgv t : values()) if (t.codigo.equals(c)) return t;
        throw new IllegalArgumentException("Afectación IGV desconocida: " + c);
    }
}
