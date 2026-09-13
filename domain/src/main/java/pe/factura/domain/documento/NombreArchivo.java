package pe.factura.domain.documento;

public final class NombreArchivo {
    private NombreArchivo() {}
    public static String de(String ruc, TipoDocumento tipo, String serie, long numero) {
        return ruc + "-" + tipo.codigo() + "-" + serie + "-" + numero;
    }
}
