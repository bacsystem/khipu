package pe.factura.application.port.in;

import pe.factura.domain.plan.MedioDePago;
import pe.factura.domain.plan.Pago;
import pe.factura.domain.plataforma.ActorAdmin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registrar a mano que un cliente pagó (#194). No hay pasarela de pago, a propósito: el administrador anota el pago y, si quiere, el pago **extiende el vencimiento** de la
 * suscripción vigente hasta el final del periodo que cubre. Todo en una transacción con su registro de bitácora: o quedan el pago, la extensión y la bitácora, o nada.
 */
public interface RegistrarPagoUseCase {
    /**
     * {@code extenderVencimiento}: que el vencimiento de la suscripción pase a ser la medianoche (Lima) del día siguiente al fin del periodo. Solo si eso lo adelanta, y solo
     * en un plan que vence (el gratis no). No cambia el plan ni los días de gracia ni el cambio de plan programado.
     */
    record Comando(LocalDate periodoDesde, LocalDate periodoHasta, BigDecimal monto, MedioDePago medio, LocalDate fechaDePago, String referencia, String nota, boolean extenderVencimiento) {}

    /**
     * Errores: {@code NO_ENCONTRADO} (la cuenta no existe); {@code PERIODO_INVALIDO}, {@code MONTO_INVALIDO}, {@code MEDIO_INVALIDO}, {@code FECHA_DE_PAGO_INVALIDA},
     * {@code FECHA_DE_PAGO_FUTURA}, {@code REFERENCIA_INVALIDA}, {@code NOTA_INVALIDA} (datos); {@code PAGO_DUPLICADO} (la cuenta ya tiene un pago por ese medio con esa referencia);
     * {@code PLAN_SIN_VENCIMIENTO} y {@code EXTENSION_SIN_EFECTO} (la extensión pedida no tiene sentido); {@code CAMBIO_CONCURRENTE} (otro administrador movió el vencimiento).
     */
    Pago registrar(ActorAdmin actor, UUID cuentaId, Comando comando);
}
