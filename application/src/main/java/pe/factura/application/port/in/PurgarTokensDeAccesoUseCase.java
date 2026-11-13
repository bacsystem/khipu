package pe.factura.application.port.in;

/**
 * Borra los enlaces de recuperación y de verificación de correo vencidos hace más de la retención configurada (#634): sin esto cada enlace pedido agrega
 * una fila y las tablas solo crecen.
 */
public interface PurgarTokensDeAccesoUseCase {
    /** Devuelve cuántos borró, sumando las dos tablas. */
    int purgar();
}
