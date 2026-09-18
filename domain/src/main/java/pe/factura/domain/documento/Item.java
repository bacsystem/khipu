package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Línea del comprobante tal como la envía el emisor. Opcionales: {@code descuento} (catálogo 53, nivel línea),
 * {@code cargos} (catálogo 53: 47 afecta la base del IGV, 48 no), {@code isc} (tributo 2000) e {@code icbper}
 * (tributo 7152, una bolsa por unidad).
 */
public record Item(String codigo, String descripcion, String unidad, BigDecimal cantidad,
                   BigDecimal precioUnitario, TipoAfectacionIgv afectacion, Descuento descuento, Isc isc, boolean icbper, List<Cargo> cargos) {

    public Item {
        cargos = cargos == null ? List.of() : List.copyOf(cargos);
        if (cargos.stream().anyMatch(Cargo::global))
            throw new DomainException("CARGO_INVALIDO", "4268 - Un cargo de línea debe usar los códigos 47 o 48 del catálogo 53");
    }

    public Item(String codigo, String descripcion, String unidad, BigDecimal cantidad, BigDecimal precioUnitario, TipoAfectacionIgv afectacion) {
        this(codigo, descripcion, unidad, cantidad, precioUnitario, afectacion, null, null, false, List.of());
    }

    public Item(String codigo, String descripcion, String unidad, BigDecimal cantidad, BigDecimal precioUnitario, TipoAfectacionIgv afectacion, Descuento descuento) {
        this(codigo, descripcion, unidad, cantidad, precioUnitario, afectacion, descuento, null, false, List.of());
    }

    public Item(String codigo, String descripcion, String unidad, BigDecimal cantidad, BigDecimal precioUnitario, TipoAfectacionIgv afectacion,
                Descuento descuento, Isc isc, boolean icbper) {
        this(codigo, descripcion, unidad, cantidad, precioUnitario, afectacion, descuento, isc, icbper, List.of());
    }

    public boolean tieneDescuento() { return descuento != null; }
    public boolean tieneIsc() { return isc != null; }
    public boolean tieneCargos() { return !cargos.isEmpty(); }
}
