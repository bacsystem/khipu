package pe.factura.adapters.persistence;

import pe.factura.domain.documento.TipoDocumento;

import java.util.stream.Collectors;

/**
 * {@link TipoDocumento#esVenta()} para el SQL: la lista de códigos del catálogo 01 que son ventas, lista para un {@code d.tipo IN (...)}. Sale del enum, no de un texto copiado,
 * así que el resumen (#15: emitidos, aceptados y facturado) y el registro de ventas del SIRE excluyen lo mismo que el dominio. La emisión por día (#24) no lo usa: trae
 * el tipo de cada celda y el servicio filtra con {@link TipoDocumento#esVenta(String)}. Códigos del enum, nunca texto de fuera: sin inyección posible.
 */
final class TiposDeVenta {
    private TiposDeVenta() {}

    static final String SQL = TipoDocumento.ventas().stream().map(t -> "'" + t.codigo() + "'").sorted().collect(Collectors.joining(", "));
}
