package pe.factura.application.service;

import pe.factura.domain.plan.RecursoDelPlan;

import java.util.UUID;

/** Controla, al crear una API key, invitar o reactivar un usuario o sumar una empresa, que el plan de la cuenta todavía tenga lugar (#498). Corre dentro de la transacción que crea. */
public interface CupoDelPlan {
    /**
     * {@code CUPO_DEL_PLAN} si la cuenta ya ocupa todo lo que su plan permite de ese recurso. Lo que ya excede el tope (una cuenta a la que le bajaron el plan) no se toca: solo
     * se rechaza lo nuevo. Un plan sin tope no limita. Bloquea el cupo hasta el fin de la transacción.
     */
    void exigirLugar(UUID cuentaId, RecursoDelPlan recurso);
}
