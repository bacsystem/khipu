package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.GestionarPlanesUseCase.PlanConUso;
import pe.factura.domain.plan.CambioDeLimites;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Un plan tal como manda hoy (#190), con cuántas cuentas lo tienen y el cambio de límites que espera al ciclo siguiente, si lo hay. */
public record PlanResponse(
        UUID id,
        String nombre,
        @Schema(example = "29.00") BigDecimal precioMensual,
        @Schema(description = "Los límites que mandan en el ciclo en curso") LimitesDto limites,
        @Schema(nullable = true, description = "Un cambio de límites ya decidido que entra al inicio del ciclo siguiente; ausente si no hay") CambioProgramadoResponse limitesProgramados,
        @Schema(example = "ACTIVO", allowableValues = {"ACTIVO", "INACTIVO"}, description = "`INACTIVO` está fuera de la oferta pero las cuentas que ya lo tienen lo conservan") String estado,
        @Schema(description = "El plan con el que nacen las cuentas nuevas: no se puede desactivar ni borrar") boolean porDefecto,
        @Schema(example = "12", description = "Cuentas que lo tienen hoy como su suscripción vigente") long cuentas) {

    public static PlanResponse de(PlanConUso p) {
        CambioDeLimites c = p.plan().programado();
        return new PlanResponse(p.plan().id(), p.plan().nombre(), p.plan().precioMensual(), LimitesDto.de(p.plan().limites()),
                c == null ? null : new CambioProgramadoResponse(LimitesDto.de(c.limites()), c.aplicaDesde()),
                p.plan().estado().name(), p.plan().porDefecto(), p.cuentas());
    }

    /** Desde cuándo manda el cambio (la medianoche del día 1 en Lima) y con qué límites. */
    public record CambioProgramadoResponse(LimitesDto limites, @Schema(example = "2026-11-01T05:00:00Z") Instant aplicaDesde) {}
}
