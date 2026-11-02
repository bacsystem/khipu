package pe.factura.application.port.in;

/**
 * Qué hacer con las cuentas dadas de baja (#201) en un listado del backoffice. Por defecto se ocultan: un listado operativo es de quien
 * está en servicio. Verlas es una decisión explícita del administrador, no un efecto de otro filtro.
 */
public enum VisibilidadDeBajas {
    /** Solo las que no están de baja. Es lo que muestra un listado sin pedir nada más. */
    OCULTAS,
    /** Las que están en servicio y las dadas de baja, mezcladas. */
    INCLUIDAS,
    /** Solo las dadas de baja. */
    SOLO
}
