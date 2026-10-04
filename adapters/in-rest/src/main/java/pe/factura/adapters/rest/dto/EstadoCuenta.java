package pe.factura.adapters.rest.dto;

import java.time.Instant;

/**
 * Estado de una cuenta en el backoffice (#182). Una cuenta suspendida no entra al portal ni emite por API, pero no se borra nada y
 * reactivarla lo devuelve todo. «Sin verificar» no es un estado de la cuenta sino del correo de cada usuario (#22).
 */
public enum EstadoCuenta {
    ACTIVA, SUSPENDIDA;

    /** Nulo = activa: es la única fuente de verdad, así el estado y la fecha nunca se contradicen. */
    public static EstadoCuenta de(Instant suspendidaEn) {
        return suspendidaEn == null ? ACTIVA : SUSPENDIDA;
    }
}
