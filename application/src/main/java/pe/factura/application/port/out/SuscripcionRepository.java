package pe.factura.application.port.out;

import pe.factura.domain.plan.PlanesDeCuenta;
import pe.factura.domain.plan.Suscripcion;

import java.util.Optional;
import java.util.UUID;

/**
 * Las suscripciones de las cuentas (#189). Toda cuenta tiene exactamente una activa: la base garantiza que nunca hay dos (índice único parcial) y que una cuenta
 * nueva nace con la del plan por defecto, así que {@link #deLaCuenta} solo viene vacío si la cuenta no existe.
 */
public interface SuscripcionRepository {
    Optional<PlanesDeCuenta> deLaCuenta(UUID cuentaId);

    /**
     * Pasa de {@code actual} a {@code nueva} en una sola sentencia: cierra la activa y abre la otra a la vez, así nunca queda la cuenta sin plan ni con dos.
     * {@code false} si {@code actual} ya no era la activa (alguien la cambió antes): no se cambia nada. Es lo que devuelve el agregado en {@code cambiarA}.
     */
    boolean cambiar(Suscripcion actual, Suscripcion nueva);
}
