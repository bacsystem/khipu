package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.plan.Plan;

import java.math.BigDecimal;
import java.util.UUID;

/** Un plan a grandes rasgos —nombre, precio y los límites que mandan hoy— tal como aparece dentro del plan de una cuenta (#191). */
public record PlanResumenResponse(
        UUID id,
        String nombre,
        @Schema(example = "29.00") BigDecimal precioMensual,
        @Schema(description = "Los límites que mandan hoy (un cambio de límites del plan que todavía no llegó no figura)") LimitesDto limites) {
    public static PlanResumenResponse de(Plan p) {
        return new PlanResumenResponse(p.id(), p.nombre(), p.precioMensual(), LimitesDto.de(p.limites()));
    }
}
