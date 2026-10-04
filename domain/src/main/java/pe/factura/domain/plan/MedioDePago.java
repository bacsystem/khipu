package pe.factura.domain.plan;

/** Cómo pagó el cliente (#194). Es un registro manual: no hay pasarela, así que esto solo dice por dónde llegó la plata. */
public enum MedioDePago {
    TRANSFERENCIA,
    DEPOSITO,
    YAPE,
    PLIN,
    TARJETA,
    EFECTIVO,
    OTRO
}
