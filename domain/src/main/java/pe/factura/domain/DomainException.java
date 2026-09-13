package pe.factura.domain;

import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {
    private final String codigo;
    public DomainException(String codigo, String mensaje) { super(mensaje); this.codigo = codigo; }
    public DomainException(String codigo, String mensaje, Throwable causa) { super(mensaje, causa); this.codigo = codigo; }
}
