package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.PlanDeCuenta;

import java.time.Instant;
import java.util.UUID;

/** El plan de una cuenta hoy (#191): cuál, en qué estado de pago está y si hay una bajada esperando el ciclo siguiente. */
public record PlanDeCuentaResponse(
        UUID cuentaId,
        PlanResumenResponse plan,
        @Schema(example = "VIGENTE", allowableValues = {"VIGENTE", "EN_GRACIA", "VENCIDA"}, description = "`EN_GRACIA`: venció pero sigue sirviéndose los días de gracia; `VENCIDA`: se acabó la gracia") String estado,
        @Schema(example = "2026-09-01T10:00:00Z") Instant iniciaEn,
        @Schema(example = "2026-11-01T05:00:00Z", description = "Hasta cuándo está pagado (exclusivo); ausente en un plan sin vencimiento (el gratis)") Instant venceEn,
        @Schema(example = "5") int diasDeGracia,
        @Schema(example = "2026-11-06T05:00:00Z", description = "Hasta cuándo se sirve con este plan, gracia incluida; ausente si no vence") Instant hastaCuandoCubre,
        @Schema(description = "Una bajada de plan decidida que entra al inicio del ciclo siguiente; ausente si no hay") ProgramadoResponse programado) {

    public static PlanDeCuentaResponse de(PlanDeCuenta p) {
        return new PlanDeCuentaResponse(p.cuentaId(), PlanResumenResponse.de(p.plan()), p.estado().name(), p.suscripcion().iniciaEn(), p.suscripcion().venceEn(),
                p.suscripcion().diasDeGracia(), p.hastaCuandoCubre(),
                p.programado() == null ? null : new ProgramadoResponse(PlanResumenResponse.de(p.programado().plan()), p.programado().cambio().aplicaDesde(),
                        p.programado().cambio().venceEn(), p.programado().cambio().diasDeGracia()));
    }

    /** A qué plan pasa, desde cuándo y con qué vencimiento y gracia. */
    public record ProgramadoResponse(PlanResumenResponse plan, @Schema(example = "2026-10-01T05:00:00Z") Instant aplicaDesde, Instant venceEn, int diasDeGracia) {}
}
