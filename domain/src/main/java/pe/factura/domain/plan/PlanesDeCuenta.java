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
public record PlanesDeCuenta(UUID cuentaId, List<Suscripcion> suscripciones) {
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
        return new PlanesDeCuenta(cuentaId, lista);
    }
}
