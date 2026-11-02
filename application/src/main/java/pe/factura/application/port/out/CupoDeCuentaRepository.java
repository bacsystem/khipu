package pe.factura.application.port.out;

import pe.factura.domain.plan.RecursoDelPlan;
import pe.factura.domain.plan.UsoDeRecursos;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que ocupa una cuenta de lo que su plan limita además de los documentos (#498). Las API keys se cuentan por cuenta, sumando las de todas sus empresas, como el
 * consumo de documentos; los usuarios son los activos más las invitaciones pendientes que todavía no vencieron (el lugar ya está dado); los RUC, las empresas de la cuenta.
 */
public interface CupoDeCuentaRepository {
    /**
     * Bloquea el cupo de ese recurso en esta cuenta hasta el fin de la transacción: dos altas simultáneas no ven las dos el último lugar libre. Solo dentro de una
     * transacción (la que crea el recurso).
     */
    void bloquear(UUID cuentaId, RecursoDelPlan recurso);

    long ocupados(UUID cuentaId, RecursoDelPlan recurso, Instant ahora);

    UsoDeRecursos uso(UUID cuentaId, Instant ahora);
}
