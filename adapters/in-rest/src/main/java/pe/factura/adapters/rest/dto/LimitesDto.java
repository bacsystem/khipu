package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.Limites;

/** Lo que un plan permite (#190). Usuarios, documentos y API keys pueden ser ilimitados; los RUC y los años de retención siempre llevan cifra. */
public record LimitesDto(
        @Schema(description = "Documentos aceptados por mes calendario (America/Lima)") LimiteDto documentosAlMes,
        @Schema(example = "3", description = "RUC (empresas) que puede tener la cuenta; mayor que cero") Integer rucs,
        @Schema(description = "Usuarios de la cuenta") LimiteDto usuarios,
        @Schema(description = "API keys vigentes por empresa") LimiteDto apiKeys,
        @Schema(example = "5", description = "Años que se conservan el XML y el CDR; mayor que cero") Integer retencionAnios) {

    public static LimitesDto de(Limites l) {
        return new LimitesDto(LimiteDto.de(l.documentosAlMes()), l.rucs(), LimiteDto.de(l.usuarios()), LimiteDto.de(l.apiKeys()), l.retencionAnios());
    }

    /** {@code LIMITE_INVALIDO} o {@code RETENCION_INVALIDA} si falta algo; el resto de las reglas (mayor que cero) las pone el dominio. */
    public Limites aDominio() {
        if (rucs == null) throw new DomainException("LIMITE_INVALIDO", "Falta el límite de RUC");
        if (retencionAnios == null) throw new DomainException("RETENCION_INVALIDA", "Falta la retención en años");
        return new Limites(LimiteDto.aDominio(documentosAlMes, "documentos_al_mes"), rucs, LimiteDto.aDominio(usuarios, "usuarios"),
                LimiteDto.aDominio(apiKeys, "api_keys"), retencionAnios);
    }
}
