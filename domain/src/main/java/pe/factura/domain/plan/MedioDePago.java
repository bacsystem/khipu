package pe.factura.domain.plan;

/** Cómo pagó el cliente (#194). Es un registro manual: no hay pasarela, así que esto solo dice por dónde llegó la plata. */
public enum MedioDePago {
    TRANSFERENCIA("Transferencia"),
    DEPOSITO("Depósito"),
    YAPE("Yape"),
    PLIN("Plin"),
    TARJETA("Tarjeta"),
    EFECTIVO("Efectivo"),
    OTRO("Otro");

    private final String nombre;

    MedioDePago(String nombre) { this.nombre = nombre; }

    /** Cómo se le dice a una persona (H7): un mensaje de error con «TRANSFERENCIA» muestra el código, no el medio. */
    public String nombre() { return nombre; }
}
