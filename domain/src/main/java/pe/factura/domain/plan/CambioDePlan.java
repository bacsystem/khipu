package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

/**
 * Un cambio de plan ya decidido que todavía no manda: entra en {@code aplicaDesde}, el inicio del ciclo siguiente (#191). Lleva el vencimiento y la gracia con que
 * empezará la suscripción nueva, para que nadie tenga que acordarse de ellos cuando llegue la fecha.
 */
public record CambioDePlan(UUID planId, Instant aplicaDesde, Instant venceEn, int diasDeGracia) {
    public CambioDePlan {
        if (planId == null || aplicaDesde == null) throw new DomainException("CAMBIO_INVALIDO", "El cambio de plan necesita el plan y la fecha desde la que aplica");
        if (venceEn != null && !venceEn.isAfter(aplicaDesde)) throw new DomainException("SUSCRIPCION_FECHAS_INVALIDAS", "El vencimiento debe ser posterior al inicio");
        if (diasDeGracia < 0) throw new DomainException("GRACIA_INVALIDA", "Los días de gracia no pueden ser negativos: " + diasDeGracia);
    }
}
