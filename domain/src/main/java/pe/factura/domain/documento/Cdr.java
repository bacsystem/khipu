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

    /**
     * Aceptado con observaciones: trae notas, o el propio ResponseCode es una observación (4000+). Lo segundo cubre un CDR que informa la observación solo en el
     * código, sin cbc:Note: sin esto quedaba ACEPTADO a secas (#331, la 4443 del resumen diario fuera de plazo, que falta ver en e-beta cómo llega).
     */
    public boolean esObservado() { return !esRechazo() && (tieneObservaciones() || Integer.parseInt(codigo) >= 4000); }
}
