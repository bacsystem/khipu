package pe.factura.domain.documento;

import java.math.BigDecimal;

/** Línea del comprobante tal como la envía el emisor; {@code descuento} es opcional (catálogo 53, nivel línea). */
public record Item(String codigo, String descripcion, String unidad, BigDecimal cantidad,
                   BigDecimal precioUnitario, TipoAfectacionIgv afectacion, Descuento descuento) {

    public Item(String codigo, String descripcion, String unidad, BigDecimal cantidad, BigDecimal precioUnitario, TipoAfectacionIgv afectacion) {
        this(codigo, descripcion, unidad, cantidad, precioUnitario, afectacion, null);
    }

    public boolean tieneDescuento() { return descuento != null; }
}
