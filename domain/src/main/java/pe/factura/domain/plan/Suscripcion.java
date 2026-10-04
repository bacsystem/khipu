package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Liga una cuenta con un plan durante un período. {@code venceEn}: hasta cuándo está pagada (nulo: sin vencimiento, el plan gratis); es exclusivo, en ese
 * instante ya empieza la gracia. {@code diasDeGracia}: cuánto se sigue sirviendo después de vencida. {@code terminaEn}: nulo mientras es la suscripción vigente
 * de la cuenta; con fecha, fue reemplazada por otra (queda como historial).
 */
public record Suscripcion(UUID id, UUID cuentaId, UUID planId, Instant iniciaEn, Instant venceEn, int diasDeGracia, Instant terminaEn) {
    public Suscripcion {
        if (id == null || cuentaId == null || planId == null || iniciaEn == null)
            throw new DomainException("SUSCRIPCION_INVALIDA", "Una suscripción necesita su cuenta, su plan y su fecha de inicio");
        if (diasDeGracia < 0) throw new DomainException("GRACIA_INVALIDA", "Los días de gracia no pueden ser negativos: " + diasDeGracia);
        if (venceEn != null && !venceEn.isAfter(iniciaEn)) throw new DomainException("SUSCRIPCION_FECHAS_INVALIDAS", "El vencimiento debe ser posterior al inicio");
        if (terminaEn != null && terminaEn.isBefore(iniciaEn)) throw new DomainException("SUSCRIPCION_FECHAS_INVALIDAS", "La suscripción no puede terminar antes de empezar");
    }

    /** Es la suscripción vigente de la cuenta (no fue reemplazada). */
    public boolean activa() { return terminaEn == null; }

    /** Hasta cuándo se sirve con este plan, gracia incluida; nulo si no vence. */
    public Instant hastaCuandoCubre() { return venceEn == null ? null : venceEn.plus(Duration.ofDays(diasDeGracia)); }

    public EstadoSuscripcion estadoEn(Instant ahora) {
        if (!activa()) return EstadoSuscripcion.REEMPLAZADA;
        if (venceEn == null || ahora.isBefore(venceEn)) return EstadoSuscripcion.VIGENTE;
        return ahora.isBefore(hastaCuandoCubre()) ? EstadoSuscripcion.EN_GRACIA : EstadoSuscripcion.VENCIDA;
    }

    /** La cierra porque otra la reemplaza. */
    public Suscripcion terminarEn(Instant cuando) {
        if (!activa()) throw new DomainException("SUSCRIPCION_YA_TERMINADA", "La suscripción ya terminó");
        return new Suscripcion(id, cuentaId, planId, iniciaEn, venceEn, diasDeGracia, cuando);
    }
}
