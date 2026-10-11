package pe.factura.domain.documento;

import pe.factura.domain.DomainException;
import pe.factura.domain.catalogo.CatalogoSunat;

import java.math.BigDecimal;
import java.util.List;

/**
 * Línea del comprobante tal como la envía el emisor. Opcionales: {@code descuento} (catálogo 53, nivel línea),
 * {@code cargos} (catálogo 53: 47 afecta la base del IGV, 48 no), {@code isc} (tributo 2000), {@code icbper}
 * (tributo 7152, una bolsa por unidad), {@code codigoSunat} (catálogo 25), {@code gtin} (GS1) y los datos sectoriales de la
 * detracción: {@code hidrobiologico} (tipo de operación 1002) y {@code transporte} (1004), que el comprobante exige o prohíbe según la operación.
 */
public record Item(String codigo, String descripcion, String unidad, BigDecimal cantidad,
                   BigDecimal precioUnitario, TipoAfectacionIgv afectacion, Descuento descuento, Isc isc, boolean icbper, List<Cargo> cargos,
                   CodigoProductoSunat codigoSunat, Gtin gtin, Hidrobiologico hidrobiologico, TransporteCarga transporte) {

    public Item {
        cargos = cargos == null ? List.of() : List.copyOf(cargos);
        if (cargos.stream().anyMatch(Cargo::global))
            throw new DomainException("CARGO_INVALIDO", "4268 - Un cargo de línea debe usar los códigos 47 o 48 del catálogo 53");
        descripcion = descripcion == null ? null : descripcion.strip();
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

    public Item(String codigo, String descripcion, String unidad, BigDecimal cantidad, BigDecimal precioUnitario, TipoAfectacionIgv afectacion,
                Descuento descuento, Isc isc, boolean icbper, List<Cargo> cargos, CodigoProductoSunat codigoSunat, Gtin gtin) {
        this(codigo, descripcion, unidad, cantidad, precioUnitario, afectacion, descuento, isc, icbper, cargos, codigoSunat, gtin, null, null);
    }

    /**
     * Reglas de la línea que SUNAT rechaza (2024–2027, 2883, 2936) — antes de numerar (#35, #308). No se llama al rehidratar: un
     * ítem ya emitido, aunque no cumpla una regla añadida después, se sigue leyendo tal cual.
     */
    void exigirValidoParaFactura() {
        if (descripcion == null || descripcion.isBlank())
            throw new DomainException("ITEM_INVALIDO", "2026 - Cada ítem necesita una descripción");
        if (descripcion.length() > 500 || descripcion.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t'))
            throw new DomainException("ITEM_INVALIDO", "2027 - La descripción del ítem admite hasta 500 caracteres");
        if (unidad == null || unidad.isBlank())
            throw new DomainException("ITEM_INVALIDO", "2883 - Es obligatorio indicar la unidad de medida del ítem (catálogo 03)");
        // 2936: SUNAT la cruza con la rec 20 completa; antes solo se miraba el formato y «XYZ» consumía número para volver rechazada.
        if (!unidadEnCatalogo())
            throw new DomainException("ITEM_INVALIDO", "2936 - La unidad de medida " + unidad + " no existe en el catálogo 03 (UN/ECE rec 20; "
                    + "GET /v1/catalogos/03): NIU unidad (bienes), ZZ unidad (servicios), KGM, HUR…");
        if (cantidad == null || cantidad.signum() <= 0)
            throw new DomainException("ITEM_INVALIDO", "2024 - La cantidad del ítem debe ser mayor que cero");
        exigirFormatoNumerico(cantidad, "2025 - La cantidad admite hasta 12 enteros y 10 decimales");
        if (precioUnitario == null || precioUnitario.signum() < 0)
            throw new DomainException("ITEM_INVALIDO", "El precio unitario no puede ser negativo");
        exigirFormatoNumerico(precioUnitario, "El precio unitario admite hasta 12 enteros y 10 decimales");
        if (codigo != null && (codigo.length() > 30 || codigo.chars().anyMatch(Character::isISOControl)))
            throw new DomainException("ITEM_INVALIDO", "El código interno del ítem admite hasta 30 caracteres (an..30 de la hoja Factura2_0)");
    }

    /** La unidad existe en el catálogo 03 (UN/ECE rec 20), contra el que SUNAT la cruza en la factura, la boleta y las notas (2936). */
    boolean unidadEnCatalogo() {
        return unidad != null && CatalogoSunat.porId("03").orElseThrow().contiene(unidad);
    }

    /** An..12,10 (hasta 12 enteros y 10 decimales): mismo formato numérico que SUNAT exige tanto a la cantidad como al precio. */
    private static void exigirFormatoNumerico(BigDecimal valor, String mensaje) {
        if (valor.scale() > 10 || valor.precision() - valor.scale() > 12) throw new DomainException("ITEM_INVALIDO", mensaje);
    }

    public boolean tieneDescuento() { return descuento != null; }
    public boolean tieneIsc() { return isc != null; }
    public boolean tieneCargos() { return !cargos.isEmpty(); }
    public boolean tieneCodigoSunat() { return codigoSunat != null; }
    public boolean tieneGtin() { return gtin != null; }
    public boolean tieneHidrobiologico() { return hidrobiologico != null; }
    public boolean tieneTransporte() { return transporte != null; }
}
