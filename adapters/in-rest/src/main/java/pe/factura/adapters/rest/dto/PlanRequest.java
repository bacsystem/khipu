package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.GestionarPlanesUseCase.DatosDePlan;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;

/** Cuerpo para crear o editar un plan (#190). Las reglas de cada dato (nombre, precio, límites mayores que cero) las pone el dominio. */
public record PlanRequest(
        @Schema(example = "Estudio", maxLength = 40, description = "Único sin importar mayúsculas") String nombre,
        @Schema(example = "49.90", description = "En soles, cero o más, con hasta dos decimales") BigDecimal precioMensual,
        LimitesDto limites,
        @Schema(example = "true", nullable = true, description = "Si sale en la página de precios. Ausente: al crear, sí; al editar, como estaba. Un plan a medida para un "
                + "cliente va en `false`: se asigna y se cobra igual, pero no se publica") Boolean visibleEnPublicidad) {

    public DatosDePlan aDominio() {
        if (limites == null) throw new DomainException("LIMITE_INVALIDO", "Faltan los límites del plan");
        return new DatosDePlan(nombre, precioMensual, limites.aDominio(), visibleEnPublicidad);
    }
}
