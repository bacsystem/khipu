package pe.factura.adapters.rest.dto;

import java.time.Instant;

/**
 * Estado de una cuenta en el backoffice. Una cuenta suspendida (#182) no entra al portal ni emite por API, pero no se borra nada y reactivarla lo
 * devuelve todo. Una cuenta de baja (#201) es la de un cliente que se fue: sale de los listados y del cobro, y se conserva todo. «Sin verificar» no es
 * un estado de la cuenta sino del correo de cada usuario (#22).
 */
public enum EstadoCuenta {
    ACTIVA, SUSPENDIDA, BAJA;

    /** Nulo = activa: es la única fuente de verdad, así el estado y la fecha nunca se contradicen. */
    public static EstadoCuenta de(Instant suspendidaEn) {
        return de(suspendidaEn, null);
    }

    /** La baja manda: una cuenta de baja que además estaba suspendida es {@code BAJA} (la fecha de suspensión se sigue informando aparte). */
    public static EstadoCuenta de(Instant suspendidaEn, Instant bajaEn) {
        if (bajaEn != null) return BAJA;
        return suspendidaEn == null ? ACTIVA : SUSPENDIDA;
    }
}
