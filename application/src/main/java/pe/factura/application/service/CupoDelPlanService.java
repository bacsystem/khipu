package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.out.CupoDeCuentaRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.plan.RecursoDelPlan;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que el plan deja crear además de documentos (#498): API keys activas, usuarios y RUCs. Con los límites que mandan hoy (una bajada ya vencida cuenta), sin romper lo existente:
 * una cuenta que ya está por encima de su tope sigue como está y solo se le niega lo nuevo.
 */
@RequiredArgsConstructor
public class CupoDelPlanService implements CupoDelPlan {
    private final CambiarPlanDeCuentaUseCase planes;
    private final CupoDeCuentaRepository ocupados;
    private final Clock clock;

    @Override
    public void exigirLugar(UUID cuentaId, RecursoDelPlan recurso) {
        Instant ahora = clock.instant();
        Plan plan = planes.plan(cuentaId).planQueMandaEn(ahora);
        Limite tope = plan.limites().de(recurso);
        if (tope.ilimitado()) return;
        ocupados.bloquear(cuentaId, recurso);
        long usados = ocupados.ocupados(cuentaId, recurso, ahora);
        if (!tope.llenoCon(usados)) return;
        throw new DomainException("CUPO_DEL_PLAN", "Tu plan " + plan.nombre() + " incluye " + recurso.cantidad(tope.maximo()) + " y ya tienes " + recurso.cantidad(usados)
                + recurso.loQueCuenta() + ". " + recurso.paraLiberar());
    }
}
