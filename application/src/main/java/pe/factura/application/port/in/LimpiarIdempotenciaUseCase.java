package pe.factura.application.port.in;

/** Borra las claves de idempotencia vencidas (#115): pasado ese tiempo, la misma clave vuelve a ser una operación nueva. */
public interface LimpiarIdempotenciaUseCase {
    /** Cuántas claves borró. */
    int limpiar();
}
