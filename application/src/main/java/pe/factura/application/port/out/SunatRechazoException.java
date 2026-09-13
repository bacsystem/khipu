package pe.factura.application.port.out;

import lombok.Getter;

@Getter
public class SunatRechazoException extends RuntimeException {
    private final String codigo, descripcion;
    public SunatRechazoException(String codigo, String descripcion) { super(codigo + " - " + descripcion); this.codigo = codigo; this.descripcion = descripcion; }
}
