package pe.factura.application.port.in;

import pe.factura.domain.plan.CambioDePlan;
import pe.factura.domain.plan.DireccionDeCambio;
import pe.factura.domain.plan.EstadoSuscripcion;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plan.Suscripcion;
import pe.factura.domain.plataforma.ActorAdmin;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Asignar un plan a una cuenta y cambiárselo cuando paga o deja de pagar, a mano mientras no haya pasarela de pago (#191). **Subir de plan tiene efecto inmediato;
 * bajar, al ciclo siguiente** (el mes calendario en Lima), como dice el plan comercial: bajar a mitad de mes no le corta a nadie lo que ya pagó. Lo que es subir o bajar
 * lo decide el precio ({@link DireccionDeCambio}). Cada cambio queda en la bitácora, con quién, qué plan y desde cuál.
 */
public interface CambiarPlanDeCuentaUseCase {
    int GRACIA_MAX_DIAS = 90;

    enum Efecto { INMEDIATO, CICLO_SIGUIENTE }

    /** Un cambio que está esperando su fecha: a qué plan pasa, cuándo y con qué vencimiento. */
    record Programado(Plan plan, CambioDePlan cambio) {}

    /** El plan de una cuenta hoy: cuál, en qué estado de pago está y si hay una bajada esperando. {@code plan} lleva los límites que mandan hoy. */
    record PlanDeCuenta(UUID cuentaId, Plan plan, Suscripcion suscripcion, EstadoSuscripcion estado, Instant hastaCuandoCubre, Programado programado) {}

    /**
     * Lo que pasaría con un cambio, para mostrarlo antes de confirmar: cuándo entra y qué pasa con el consumo del mes en curso. {@code superaElLimite}: el consumo de
     * este mes ya pasa el tope de documentos del plan nuevo (solo es una advertencia: un cambio inmediato dejaría a la cuenta por encima de su límite; una bajada no
     * toca este mes).
     */
    record Previsualizacion(UUID cuentaId, Plan planActual, Plan planNuevo, DireccionDeCambio direccion, Efecto efecto, Instant aplicaDesde,
                            YearMonth mes, long consumoDelMes, Limite limiteDeDocumentos, boolean superaElLimite) {}

    /** {@code NO_ENCONTRADO} si no existe la cuenta; no escribe nada. */
    PlanDeCuenta plan(UUID cuentaId);

    /** {@code NO_ENCONTRADO} (cuenta o plan), {@code PLAN_INACTIVO}. No escribe nada. */
    Previsualizacion previsualizar(UUID cuentaId, UUID planId);

    /**
     * Cambia el plan. Una subida o una renovación (el mismo plan, con otro vencimiento) entran ya y cancelan la bajada que esperaba; una bajada queda programada para el
     * inicio del ciclo siguiente y la cuenta sigue con el plan de hoy hasta entonces. {@code venceEn} es obligatorio para un plan de pago y opcional para uno gratis;
     * {@code diasDeGracia} es 0 si no se indica y llega a {@value #GRACIA_MAX_DIAS}. {@code NO_ENCONTRADO}, {@code PLAN_INACTIVO}, {@code VENCIMIENTO_REQUERIDO},
     * {@code GRACIA_INVALIDA}, {@code SUSCRIPCION_FECHAS_INVALIDAS} y {@code CAMBIO_CONCURRENTE} (otro administrador cambió el plan en el medio: hay que mirar de nuevo).
     */
    PlanDeCuenta cambiar(ActorAdmin actor, UUID cuentaId, UUID planId, Instant venceEn, Integer diasDeGracia);
}
