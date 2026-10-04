package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.plan.Pago;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Un pago registrado a mano (#194). */
public record PagoResponse(
        UUID id,
        UUID cuentaId,
        @Schema(example = "2026-10-01") LocalDate periodoDesde,
        @Schema(example = "2026-10-31", description = "Último día cubierto, inclusive") LocalDate periodoHasta,
        @Schema(example = "29.00", description = "En soles") BigDecimal monto,
        @Schema(example = "YAPE") String medio,
        @Schema(example = "2026-10-14") LocalDate fechaDePago,
        @Schema(description = "Ausente si no se dio") String referencia,
        @Schema(description = "Ausente si no se dio") String nota,
        @Schema(description = "Cuándo se registró") Instant registradoEn,
        @Schema(example = "2026-11-01T05:00:00Z", description = "El nuevo vencimiento de la suscripción si este pago lo extendió; ausente si solo quedó anotado") Instant extendioHasta) {
    public static PagoResponse de(Pago p) {
        return new PagoResponse(p.id(), p.cuentaId(), p.periodoDesde(), p.periodoHasta(), p.monto(), p.medio().name(), p.fechaDePago(), p.referencia(), p.nota(), p.registradoEn(), p.extendioHasta());
    }
}
