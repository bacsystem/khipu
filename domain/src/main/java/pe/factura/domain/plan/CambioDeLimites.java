package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

import java.time.Instant;

/** Un cambio de límites que ya se decidió pero todavía no manda: entra en {@code aplicaDesde}, el inicio del ciclo siguiente (#190). */
public record CambioDeLimites(Limites limites, Instant aplicaDesde) {
    public CambioDeLimites {
        if (limites == null) throw new DomainException("LIMITE_INVALIDO", "Faltan los límites del cambio");
        if (aplicaDesde == null) throw new DomainException("CAMBIO_INVALIDO", "El cambio de límites necesita la fecha desde la que aplica");
    }
}
