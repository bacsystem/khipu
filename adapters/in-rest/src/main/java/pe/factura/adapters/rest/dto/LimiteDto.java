package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.Limite;

/**
 * Un tope de un plan. «Sin límite» se dice con {@code ilimitado: true} y sin {@code maximo}: un campo omitido por error nunca se vuelve «ilimitado» en silencio, y
 * dar las dos cosas a la vez es ambiguo, así que se rechaza.
 */
public record LimiteDto(
        @Schema(example = "300", nullable = true, description = "El tope; mayor que cero. Ausente si el límite es ilimitado") Integer maximo,
        @Schema(example = "false", description = "`true` si no tiene tope. En ese caso `maximo` debe faltar") boolean ilimitado) {

    public static LimiteDto de(Limite l) { return new LimiteDto(l.maximo(), l.ilimitado()); }

    /** {@code LIMITE_INVALIDO} si falta el tope sin marcar ilimitado, o si trae las dos cosas. */
    public static Limite aDominio(LimiteDto dto, String campo) {
        if (dto == null) throw new DomainException("LIMITE_INVALIDO", "Falta el límite «" + campo + "»");
        if (dto.ilimitado()) {
            if (dto.maximo() != null) throw new DomainException("LIMITE_INVALIDO", "El límite «" + campo + "» no puede ser ilimitado y tener un máximo a la vez");
            return Limite.sinLimite();
        }
        if (dto.maximo() == null) throw new DomainException("LIMITE_INVALIDO", "Falta el máximo del límite «" + campo + "» (o márcalo como ilimitado)");
        return Limite.de(dto.maximo());
    }
}
