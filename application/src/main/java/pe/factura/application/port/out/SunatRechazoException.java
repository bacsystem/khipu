package pe.factura.application.port.out;

public class SunatRechazoException extends RuntimeException {
    private final String codigo, descripcion;
    public SunatRechazoException(String codigo, String descripcion) { super(codigo + " - " + descripcion); this.codigo = codigo; this.descripcion = descripcion; }
    public String codigo() { return codigo; }
    public String descripcion() { return descripcion; }
}
