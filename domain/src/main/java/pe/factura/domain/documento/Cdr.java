package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.util.List;

public record Cdr(String codigo, String descripcion, List<String> observaciones) {
    public boolean esAceptado() { return "0".equals(codigo); }
    public boolean esRechazo() {
        if (codigo == null || codigo.isBlank() || !codigo.matches("-?\\d+"))
            throw new DomainException("CDR_INVALIDO", "Código de CDR no numérico: " + codigo);
        int c = Integer.parseInt(codigo);
        return c >= 2000 && c <= 3999;
    }
    public boolean tieneObservaciones() { return observaciones != null && !observaciones.isEmpty(); }
}
