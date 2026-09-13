package pe.factura.application.port.out;

import lombok.Getter;

@Getter
public class SunatTransientException extends RuntimeException {
    private final String codigo;
    public SunatTransientException(String codigo, String mensaje, Throwable causa) { super(mensaje, causa); this.codigo = codigo; }
    public SunatTransientException(String codigo, String mensaje) { this(codigo, mensaje, null); }
}
