package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.Limites;
import pe.factura.domain.plan.PeriodoDelTope;

/**
 * Lo que un plan permite (#190, #613). Usuarios, documentos, API keys y pruebas en Beta pueden ser ilimitados; los RUC y los años de retención siempre llevan cifra.
 * {@code pruebasBetaAlMes} es de solo lectura: sale de multiplicar las pruebas por RUC por los RUC, y al guardar se ignora.
 */
public record LimitesDto(
        @Schema(description = "Tope de documentos de Producción (los aceptados por SUNAT): al mes calendario (America/Lima) o en total, según `periodoDocumentos`") LimiteDto documentosAlMes,
        @Schema(example = "3", description = "RUC (empresas) que puede tener la cuenta; mayor que cero") Integer rucs,
        @Schema(description = "Usuarios de la cuenta") LimiteDto usuarios,
        @Schema(description = "API keys vigentes por empresa") LimiteDto apiKeys,
        @Schema(example = "5", description = "Años que se conservan el XML y el CDR; mayor que cero") Integer retencionAnios,
        @Schema(example = "MENSUAL", description = "`MENSUAL`: el tope de documentos se cuenta en el mes y se renueva el día 1. `TOTAL`: en toda la vida de la cuenta, sin "
                + "renovarse (necesita una cifra: ilimitado solo puede ser mensual)") PeriodoDelTope periodoDocumentos,
        @Schema(description = "Pruebas en Beta al mes que da el plan por cada RUC que permite") LimiteDto pruebasBetaPorRuc,
        @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Pruebas en Beta al mes de toda la cuenta: `pruebasBetaPorRuc` por `rucs`, compartidas entre sus "
                + "empresas. Solo lectura") LimiteDto pruebasBetaAlMes) {

    public static LimitesDto de(Limites l) {
        return new LimitesDto(LimiteDto.de(l.documentosAlMes()), l.rucs(), LimiteDto.de(l.usuarios()), LimiteDto.de(l.apiKeys()), l.retencionAnios(),
                l.periodoDeDocumentos(), LimiteDto.de(l.pruebasBetaPorRuc()), LimiteDto.de(l.pruebasBetaAlMes()));
    }

    /** {@code LIMITE_INVALIDO} o {@code RETENCION_INVALIDA} si falta algo; el resto de las reglas (mayor que cero, total con cifra) las pone el dominio. */
    public Limites aDominio() {
        if (rucs == null) throw new DomainException("LIMITE_INVALIDO", "Falta el límite de RUC");
        if (retencionAnios == null) throw new DomainException("RETENCION_INVALIDA", "Falta la retención en años");
        if (periodoDocumentos == null) throw new DomainException("LIMITE_INVALIDO", "Falta el periodo del tope de documentos (MENSUAL o TOTAL)");
        return new Limites(LimiteDto.aDominio(documentosAlMes, "documentos_al_mes"), rucs, LimiteDto.aDominio(usuarios, "usuarios"),
                LimiteDto.aDominio(apiKeys, "api_keys"), retencionAnios, periodoDocumentos, LimiteDto.aDominio(pruebasBetaPorRuc, "pruebas_beta_por_ruc"));
    }
}
