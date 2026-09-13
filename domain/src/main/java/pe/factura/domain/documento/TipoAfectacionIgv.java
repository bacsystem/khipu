package pe.factura.domain.documento;

public enum TipoAfectacionIgv {
    GRAVADO("10", "1000", "IGV", "VAT"), EXONERADO("20", "9997", "EXO", "VAT"), INAFECTO("30", "9998", "INA", "FRE");

    private final String codigo, tributoId, tributoNombre, tributoTipo;
    TipoAfectacionIgv(String codigo, String tributoId, String tributoNombre, String tributoTipo) {
        this.codigo = codigo; this.tributoId = tributoId; this.tributoNombre = tributoNombre; this.tributoTipo = tributoTipo;
    }
    public String codigo() { return codigo; }
    public String tributoId() { return tributoId; }
    public String tributoNombre() { return tributoNombre; }
    public String tributoTipo() { return tributoTipo; }
    public boolean gravado() { return this == GRAVADO; }
    public static TipoAfectacionIgv porCodigo(String c) {
        for (TipoAfectacionIgv t : values()) if (t.codigo.equals(c)) return t;
        throw new IllegalArgumentException("Afectación IGV desconocida: " + c);
    }
}
