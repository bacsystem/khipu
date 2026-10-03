package pe.factura.domain.plataforma;

/**
 * Acciones del administrador que deja la bitácora. Cada acción nueva del backoffice (suspender una cuenta,
 * impersonar, cambiar de plan…) agrega su valor aquí y escribe su registro en la misma transacción que la acción.
 */
public enum AccionAdmin {
    CREAR_TENANT,
    CREAR_ADMINISTRADOR,
    /** Alta asistida de un cliente: cuenta, empresa, serie y API key en un solo paso (#188). */
    CREAR_CUENTA
}
