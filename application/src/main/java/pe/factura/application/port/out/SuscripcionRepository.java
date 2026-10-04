package pe.factura.application.port.out;

import pe.factura.domain.plan.CambioDePlan;
import pe.factura.domain.plan.PlanesDeCuenta;
import pe.factura.domain.plan.Suscripcion;

import java.time.Instant;
import java.util.List;
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
     * En la misma sentencia deja sin efecto el cambio programado de la cuenta (#191): un cambio inmediato, o el que se aplica al llegar su fecha, lo reemplaza.
     */
    boolean cambiar(Suscripcion actual, Suscripcion nueva);

    /** Deja anotado un cambio para más adelante (la bajada de plan, #191). Como mucho uno por cuenta: reemplaza al que hubiera. No toca la suscripción activa. */
    void programar(UUID cuentaId, CambioDePlan cambio);

    /** Quita el cambio programado de la cuenta, si lo hay. */
    void cancelarProgramado(UUID cuentaId);

    /** Las cuentas cuyo cambio programado ya llegó a su fecha (en el instante exacto ya cuenta), las más antiguas primero y como mucho {@code limite}. */
    List<UUID> cuentasConCambioVencido(Instant ahora, int limite);
}
