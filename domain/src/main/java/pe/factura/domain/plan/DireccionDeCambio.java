package pe.factura.domain.plan;

/**
 * Qué clase de cambio de plan es, que decide cuándo entra (#191): subir de plan tiene efecto inmediato y bajar, al ciclo siguiente, como dice el plan comercial. Lo que
 * es «subir» o «bajar» lo decide el **precio**: es lo que paga el cliente. El mismo plan es una renovación (otro vencimiento u otra gracia). Dos planes distintos al
 * mismo precio no son una bajada: nadie paga menos, así que no hay por qué hacer esperar al cliente.
 */
public enum DireccionDeCambio {
    SUBIDA, BAJADA, RENOVACION;

    public static DireccionDeCambio entre(Plan actual, Plan nuevo) {
        if (actual.id().equals(nuevo.id())) return RENOVACION;
        return nuevo.precioMensual().compareTo(actual.precioMensual()) < 0 ? BAJADA : SUBIDA;
    }

    /** Todo menos la bajada entra al instante. */
    public boolean esInmediata() { return this != BAJADA; }
}
