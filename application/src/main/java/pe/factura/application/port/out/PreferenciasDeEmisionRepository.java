package pe.factura.application.port.out;

import java.util.UUID;

/** Las preferencias de emisión de la empresa (columnas de {@code tenant}), aparte del {@code Tenant} para no reescribir el resto de la empresa al cambiarlas. */
public interface PreferenciasDeEmisionRepository {
    /** Si el portal ofrece las opciones avanzadas al emitir; {@code false} si la empresa no existe. */
    boolean opcionesAvanzadas(UUID tenantId);

    /** Guarda la preferencia; {@code false} si la empresa no existe (no se escribió nada). */
    boolean fijarOpcionesAvanzadas(UUID tenantId, boolean opcionesAvanzadas);
}
