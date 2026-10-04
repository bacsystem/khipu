package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.Previsualizacion;

import java.time.Instant;
import java.util.UUID;

/** Qué pasaría con un cambio de plan, para mostrarlo antes de confirmar (#191): cuándo entra y qué pasa con el consumo del mes en curso. */
public record PrevisualizacionDePlanResponse(
        UUID cuentaId,
        PlanResumenResponse planActual,
        PlanResumenResponse planNuevo,
        @Schema(example = "BAJADA", allowableValues = {"SUBIDA", "BAJADA", "RENOVACION"}, description = "Lo decide el precio: más caro es subida, más barato bajada, el mismo plan una renovación") String direccion,
        @Schema(example = "CICLO_SIGUIENTE", allowableValues = {"INMEDIATO", "CICLO_SIGUIENTE"}, description = "Subir y renovar entran ya; bajar, al inicio del ciclo siguiente") String efecto,
        @Schema(example = "2026-10-01T05:00:00Z", description = "Desde cuándo manda el plan nuevo") Instant aplicaDesde,
        @Schema(example = "2026-09", description = "El mes en curso (calendario de America/Lima) del que se habla") String mes,
        @Schema(example = "312", description = "Documentos que la cuenta ha consumido este mes: solo los comprobantes aceptados por SUNAT") long consumoDelMes,
        @Schema(description = "El tope de documentos al mes del plan nuevo") LimiteDto limiteDeDocumentos,
        @Schema(description = "El consumo de este mes ya pasa el tope del plan nuevo: una advertencia (un cambio inmediato dejaría a la cuenta por encima de su límite; una bajada no toca este mes)") boolean superaElLimite) {

    public static PrevisualizacionDePlanResponse de(Previsualizacion v) {
        return new PrevisualizacionDePlanResponse(v.cuentaId(), PlanResumenResponse.de(v.planActual()), PlanResumenResponse.de(v.planNuevo()), v.direccion().name(), v.efecto().name(),
                v.aplicaDesde(), v.mes().toString(), v.consumoDelMes(), LimiteDto.de(v.limiteDeDocumentos()), v.superaElLimite());
    }
}
