package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.GestionarPlanesUseCase.DatosDePlan;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;

/** Cuerpo para crear o editar un plan (#190). Las reglas de cada dato (nombre, precio, límites mayores que cero) las pone el dominio. */
public record PlanRequest(
        @Schema(example = "Estudio", maxLength = 40, description = "Único sin importar mayúsculas") String nombre,
        @Schema(example = "49.90", description = "En soles, cero o más, con hasta dos decimales") BigDecimal precioMensual,
        LimitesDto limites) {

    public DatosDePlan aDominio() {
        if (limites == null) throw new DomainException("LIMITE_INVALIDO", "Faltan los límites del plan");
        return new DatosDePlan(nombre, precioMensual, limites.aDominio());
    }
}
