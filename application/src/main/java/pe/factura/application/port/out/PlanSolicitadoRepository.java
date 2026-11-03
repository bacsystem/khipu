package pe.factura.application.port.out;

import java.util.UUID;

/**
 * El plan que una cuenta pidió al registrarse (#581): el que eligió en la página de precios. Es un dato para el operador, no una suscripción: la cuenta nace en
 * el plan por defecto y el plan pagado lo activa el backoffice cuando se registra el pago. Aparte de {@link CuentaRepository} a propósito: solo lo escribe el
 * registro, y el guardado de la cuenta (que reescribe sus columnas) no puede borrarlo.
 */
public interface PlanSolicitadoRepository {
    /** Anota el plan pedido. Tiene que correr en la transacción que crea la cuenta. */
    void anotar(UUID cuentaId, UUID planId);
}
