package pe.factura.domain.plataforma;

/**
 * Acciones del administrador que deja la bitácora. Cada acción nueva del backoffice (suspender una cuenta,
 * impersonar, cambiar de plan…) agrega su valor aquí y escribe su registro en la misma transacción que la acción.
 */
public enum AccionAdmin {
    CREAR_TENANT,
    CREAR_ADMINISTRADOR,
    /** Alta asistida de un cliente: cuenta, empresa, serie y API key en un solo paso (#188). */
    CREAR_CUENTA,
    /** Login completo, con el segundo factor ya verificado (#177). El detalle dice si se usó un código de recuperación. */
    INICIAR_SESION,
    /** Primera configuración del segundo factor, que también inicia la sesión (#177). */
    CONFIGURAR_SEGUNDO_FACTOR
}
