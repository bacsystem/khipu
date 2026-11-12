package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

/**
 * Lo que un plan permite: documentos de Producción (al mes o en total, según {@code periodoDeDocumentos}, #613), RUC, usuarios, API keys, años de retención de XML y
 * CDR, y pruebas en Beta al mes por cada RUC del plan (#613). Todo son topes mayores que cero; solo documentos, usuarios, API keys y pruebas pueden ser ilimitados (un
 * plan siempre tiene un número finito de RUC y de años que se conserva lo emitido).
 *
 * <p>{@code documentosAlMes} conserva su nombre (columna, API) aunque con {@link PeriodoDelTope#TOTAL} el tope sea de toda la vida de la cuenta: es «el tope de
 * documentos», y el periodo dice sobre qué se cuenta.
 */
public record Limites(Limite documentosAlMes, int rucs, Limite usuarios, Limite apiKeys, int retencionAnios, PeriodoDelTope periodoDeDocumentos,
                      Limite pruebasBetaPorRuc) {
    /** Lo que da cada plan en Beta por cada RUC que permite, mientras nadie lo cambie (#613): 1 000 pruebas al mes. */
    public static final int PRUEBAS_BETA_POR_RUC_INICIAL = 1_000;

    public Limites {
        if (documentosAlMes == null || usuarios == null || apiKeys == null || periodoDeDocumentos == null || pruebasBetaPorRuc == null)
            throw new DomainException("LIMITE_INVALIDO", "Faltan límites del plan");
        if (rucs <= 0) throw new DomainException("LIMITE_INVALIDO", "Un plan debe permitir al menos un RUC: " + rucs);
        if (retencionAnios <= 0) throw new DomainException("RETENCION_INVALIDA", "La retención debe ser de al menos un año: " + retencionAnios);
        if (periodoDeDocumentos == PeriodoDelTope.TOTAL && documentosAlMes.ilimitado())
            throw new DomainException("LIMITE_INVALIDO", "Un tope de documentos en total necesita una cifra: ilimitado solo puede ser mensual");
    }

    /** Con tope mensual y las pruebas en Beta iniciales: lo de antes de #613. */
    public Limites(Limite documentosAlMes, int rucs, Limite usuarios, Limite apiKeys, int retencionAnios) {
        this(documentosAlMes, rucs, usuarios, apiKeys, retencionAnios, PeriodoDelTope.MENSUAL, Limite.de(PRUEBAS_BETA_POR_RUC_INICIAL));
    }

    /** El tope de un recurso del plan (#498); los RUC siempre son finitos. */
    public Limite de(RecursoDelPlan recurso) {
        return switch (recurso) {
            case API_KEYS -> apiKeys;
            case USUARIOS -> usuarios;
            case RUCS -> Limite.de(rucs);
        };
    }

    /**
     * Las pruebas en Beta al mes de toda la cuenta (#613): las de cada RUC por los RUC del plan, en una sola bolsa que comparten sus empresas. El producto se corta en
     * {@link Integer#MAX_VALUE}: una cifra así ya es «sin límite» en la práctica, y no desborda.
     */
    public Limite pruebasBetaAlMes() {
        if (pruebasBetaPorRuc.ilimitado()) return Limite.sinLimite();
        return Limite.de((int) Math.min(Integer.MAX_VALUE, (long) pruebasBetaPorRuc.maximo() * rucs));
    }
}
