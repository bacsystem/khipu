package pe.factura.domain.documento;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TipoAfectacionIgv {
    GRAVADO("10", "1000", "IGV", "VAT"), EXONERADO("20", "9997", "EXO", "VAT"), INAFECTO("30", "9998", "INA", "FRE");

    private final String codigo, tributoId, tributoNombre, tributoTipo;
    public boolean gravado() { return this == GRAVADO; }
    public static TipoAfectacionIgv porCodigo(String c) {
        for (TipoAfectacionIgv t : values()) if (t.codigo.equals(c)) return t;
        throw new IllegalArgumentException("Afectación IGV desconocida: " + c);
    }
}
