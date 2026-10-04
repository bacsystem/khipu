package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Todas las suscripciones de una cuenta, con sus dos invariantes (#189): **siempre hay exactamente una activa**. Una cuenta no puede quedarse sin plan ni tener
 * dos a la vez; el único camino para pasar de un plan a otro es {@link #cambiarA}, que cierra la activa y abre la nueva de un solo golpe.
 */
public record PlanesDeCuenta(UUID cuentaId, List<Suscripcion> suscripciones, CambioDePlan programado) {
    /** Sin ningún cambio programado. */
    public PlanesDeCuenta(UUID cuentaId, List<Suscripcion> suscripciones) { this(cuentaId, suscripciones, null); }

    public PlanesDeCuenta {
        suscripciones = suscripciones == null ? List.of() : List.copyOf(suscripciones);
        if (suscripciones.stream().anyMatch(s -> !s.cuentaId().equals(cuentaId)))
            throw new DomainException("SUSCRIPCION_AJENA", "Hay una suscripción que no es de esta cuenta");
        long activas = suscripciones.stream().filter(Suscripcion::activa).count();
        if (activas == 0) throw new DomainException("CUENTA_SIN_PLAN", "Una cuenta siempre debe tener un plan");
        if (activas > 1) throw new DomainException("SUSCRIPCIONES_ACTIVAS_MULTIPLES", "Una cuenta no puede tener dos suscripciones activas");
    }

    public Suscripcion activa() { return suscripciones.stream().filter(Suscripcion::activa).findFirst().orElseThrow(); }

    /** La activa termina en {@code desde} y la nueva empieza en ese mismo instante: no hay hueco sin plan ni solape. No muta este agregado. */
    public PlanesDeCuenta cambiarA(UUID idNueva, UUID planId, Instant desde, Instant venceEn, int diasDeGracia) {
        Suscripcion actual = activa();
        List<Suscripcion> lista = new ArrayList<>();
        for (Suscripcion s : suscripciones) lista.add(s == actual ? s.terminarEn(desde) : s);
        lista.add(new Suscripcion(idNueva, cuentaId, planId, desde, venceEn, diasDeGracia, null));
        // Un cambio inmediato (una subida, una renovación) deja sin efecto la bajada que estaba esperando.
        return new PlanesDeCuenta(cuentaId, lista, null);
    }

    /**
     * Deja anotado un cambio para más adelante (la bajada de plan, #191) sin tocar la suscripción activa: la cuenta sigue con el plan de hoy hasta {@code aplicaDesde}. Un
     * segundo cambio programado reemplaza al primero; nunca hay dos pendientes. No muta este agregado.
     */
    public PlanesDeCuenta programar(CambioDePlan cambio) {
        if (cambio.aplicaDesde().isBefore(activa().iniciaEn())) throw new DomainException("SUSCRIPCION_FECHAS_INVALIDAS", "El cambio no puede aplicar antes de que empiece la suscripción actual");
        return new PlanesDeCuenta(cuentaId, suscripciones, cambio);
    }

    public PlanesDeCuenta cancelarProgramado() { return new PlanesDeCuenta(cuentaId, suscripciones, null); }

    /** El plan que manda en {@code ahora}: el de la suscripción activa, o el programado si su fecha ya llegó (en el instante exacto ya manda). */
    public UUID planVigenteEn(Instant ahora) {
        return programado != null && !ahora.isBefore(programado.aplicaDesde()) ? programado.planId() : activa().planId();
    }

    /**
     * Llegada la fecha, convierte lo programado en la suscripción activa: la actual termina **en la fecha programada** y la nueva empieza ahí, con su vencimiento y su
     * gracia; así el historial dice cuándo cambió el plan de verdad, no cuándo se aplicó. Antes de la fecha, o sin nada programado, no cambia nada.
     */
    public PlanesDeCuenta aplicarProgramadoEn(Instant ahora, UUID idNueva) {
        if (programado == null || ahora.isBefore(programado.aplicaDesde())) return this;
        return cambiarA(idNueva, programado.planId(), programado.aplicaDesde(), programado.venceEn(), programado.diasDeGracia());
    }
}
