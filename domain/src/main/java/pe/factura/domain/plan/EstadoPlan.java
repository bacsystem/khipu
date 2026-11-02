package pe.factura.domain.plan;

/** {@code INACTIVO} saca al plan de la oferta (no se puede asignar a cuentas nuevas) sin tocar a las cuentas que ya lo tienen (#190). */
public enum EstadoPlan { ACTIVO, INACTIVO }
