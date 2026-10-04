package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

/**
 * Lo que un plan permite: documentos al mes, RUC, usuarios, API keys y años de retención de XML y CDR. Todo son topes mayores que cero; solo documentos,
 * usuarios y API keys pueden ser ilimitados (un plan siempre tiene un número finito de RUC y de años que se conserva lo emitido).
 */
public record Limites(Limite documentosAlMes, int rucs, Limite usuarios, Limite apiKeys, int retencionAnios) {
    public Limites {
        if (documentosAlMes == null || usuarios == null || apiKeys == null) throw new DomainException("LIMITE_INVALIDO", "Faltan límites del plan");
        if (rucs <= 0) throw new DomainException("LIMITE_INVALIDO", "Un plan debe permitir al menos un RUC: " + rucs);
        if (retencionAnios <= 0) throw new DomainException("RETENCION_INVALIDA", "La retención debe ser de al menos un año: " + retencionAnios);
    }
}
