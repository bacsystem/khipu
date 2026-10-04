package pe.factura.domain.plan;

import pe.factura.domain.DomainException;

/**
 * Tope de un recurso de un plan (documentos al mes, usuarios, API keys). {@code null} es «sin límite»: se dice con {@link #sinLimite()}, nunca con un número
 * mágico, y cero o un negativo no existen (un plan que no deja emitir un documento no se vende).
 */
public record Limite(Integer maximo) {
    public Limite {
        if (maximo != null && maximo <= 0) throw new DomainException("LIMITE_INVALIDO", "Un límite debe ser mayor que cero (o ilimitado): " + maximo);
    }

    public static Limite de(int maximo) { return new Limite(maximo); }

    public static Limite sinLimite() { return new Limite(null); }

    public boolean ilimitado() { return maximo == null; }
}
