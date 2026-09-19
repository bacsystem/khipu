package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Línea del comprobante tal como la envía el emisor. Opcionales: {@code descuento} (catálogo 53, nivel línea),
 * {@code cargos} (catálogo 53: 47 afecta la base del IGV, 48 no), {@code isc} (tributo 2000), {@code icbper}
 * (tributo 7152, una bolsa por unidad), {@code codigoSunat} (catálogo 25) y {@code gtin} (GS1).
 */
public record Item(String codigo, String descripcion, String unidad, BigDecimal cantidad,
                   BigDecimal precioUnitario, TipoAfectacionIgv afectacion, Descuento descuento, Isc isc, boolean icbper, List<Cargo> cargos,
                   CodigoProductoSunat codigoSunat, Gtin gtin) {

    /** Reglas de la línea que SUNAT rechaza (2024–2027, 2883, 2936) y que conviene atajar antes de consumir número (#35). */
    public Item {
        cargos = cargos == null ? List.of() : List.copyOf(cargos);
        if (cargos.stream().anyMatch(Cargo::global))
            throw new DomainException("CARGO_INVALIDO", "4268 - Un cargo de línea debe usar los códigos 47 o 48 del catálogo 53");
        if (descripcion == null || descripcion.isBlank())
            throw new DomainException("ITEM_INVALIDO", "2026 - Cada ítem necesita una descripción");
        descripcion = descripcion.strip();
        if (descripcion.length() > 500 || descripcion.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t'))
            throw new DomainException("ITEM_INVALIDO", "2027 - La descripción del ítem admite hasta 500 caracteres");
        if (unidad == null || !unidad.matches("[A-Z0-9]{2,3}"))
            throw new DomainException("ITEM_INVALIDO", "2883 - La unidad de medida es un código del catálogo 03 (UN/ECE rec 20: NIU, ZZ, KGM, HUR…): " + unidad);
        if (cantidad == null || cantidad.signum() <= 0)
            throw new DomainException("ITEM_INVALIDO", "2024 - La cantidad del ítem debe ser mayor que cero");
        if (cantidad.scale() > 10 || cantidad.precision() - cantidad.scale() > 12)
            throw new DomainException("ITEM_INVALIDO", "2025 - La cantidad admite hasta 12 enteros y 10 decimales");
        if (precioUnitario == null || precioUnitario.signum() < 0)
            throw new DomainException("ITEM_INVALIDO", "El precio unitario no puede ser negativo");
        if (precioUnitario.scale() > 10 || precioUnitario.precision() - precioUnitario.scale() > 12)
            throw new DomainException("ITEM_INVALIDO", "El precio unitario admite hasta 12 enteros y 10 decimales");
        if (codigo != null && (codigo.length() > 30 || codigo.chars().anyMatch(Character::isISOControl)))
            throw new DomainException("ITEM_INVALIDO", "El código interno del ítem admite hasta 30 caracteres (an..30 de la hoja Factura2_0)");
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

    public Item(String codigo, String descripcion, String unidad, BigDecimal cantidad, BigDecimal precioUnitario, TipoAfectacionIgv afectacion,
                Descuento descuento, Isc isc, boolean icbper, List<Cargo> cargos) {
        this(codigo, descripcion, unidad, cantidad, precioUnitario, afectacion, descuento, isc, icbper, cargos, null, null);
    }

    public boolean tieneDescuento() { return descuento != null; }
    public boolean tieneIsc() { return isc != null; }
    public boolean tieneCargos() { return !cargos.isEmpty(); }
    public boolean tieneCodigoSunat() { return codigoSunat != null; }
    public boolean tieneGtin() { return gtin != null; }
}
