package pe.factura.application.port.in;

/**
 * Borra las claves de idempotencia vencidas (#115): pasado ese tiempo, la misma clave vuelve a ser una operación nueva. También olvida
 * antes las respuestas guardadas, que pueden llevar un secreto (#219).
 */
public interface LimpiarIdempotenciaUseCase {
    /** Cuántas claves borró más cuántas respuestas olvidó. */
    int limpiar();
}
