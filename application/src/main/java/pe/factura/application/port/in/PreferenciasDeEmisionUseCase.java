package pe.factura.application.port.in;

import java.util.UUID;

/**
 * Las preferencias de emisión de la empresa en el portal. Hoy una sola: si el modal de emisión ofrece las opciones avanzadas (tipo de operación/exportación, anticipos,
 * percepción, retención, más opciones y referencias). Solo cambia lo que muestra el portal; la API acepta los mismos campos con o sin ella.
 */
public interface PreferenciasDeEmisionUseCase {
    record PreferenciasDeEmision(boolean opcionesAvanzadas) {}

    PreferenciasDeEmision obtener(UUID tenantId);

    /** {@code DomainException("NO_ENCONTRADO")} si la empresa no existe. */
    PreferenciasDeEmision guardar(UUID tenantId, PreferenciasDeEmision preferencias);
}
