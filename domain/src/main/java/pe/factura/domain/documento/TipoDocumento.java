package pe.factura.domain.documento;

import lombok.Getter;

import java.util.regex.Pattern;

public enum TipoDocumento {
    FACTURA("01", "F"), BOLETA("03", "B"), NOTA_CREDITO("07", "FB"), NOTA_DEBITO("08", "FB");

    @Getter private final String codigo;
    private final Pattern serie;

    TipoDocumento(String codigo, String prefijos) {
        this.codigo = codigo;
        this.serie = Pattern.compile("[" + prefijos + "][A-Z0-9]{3}");
    }
    public boolean serieValida(String s) { return s != null && serie.matcher(s).matches(); }
    public static TipoDocumento porCodigo(String codigo) {
        for (TipoDocumento t : values()) if (t.codigo.equals(codigo)) return t;
        throw new IllegalArgumentException("Tipo de documento desconocido: " + codigo);
    }
}
