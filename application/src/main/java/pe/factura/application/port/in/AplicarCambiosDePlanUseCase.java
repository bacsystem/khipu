package pe.factura.application.port.in;

/**
 * Pasa a vigente los cambios de plan que ya llegaron a su fecha (#191): la bajada que se programó para el inicio del ciclo siguiente. Lo llama un trabajo programado.
 * Cada cuenta va en su propia transacción: una que falla no frena a las demás.
 */
public interface AplicarCambiosDePlanUseCase {
    /** Cuántos cambios se aplicaron y cuántos fallaron (se reintentan en la siguiente pasada). */
    record Resultado(int aplicados, int fallidos) {}

    Resultado aplicarVencidos();
}
