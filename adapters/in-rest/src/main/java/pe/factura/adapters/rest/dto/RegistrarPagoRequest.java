package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.domain.plan.MedioDePago;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cuerpo para registrar a mano el pago de una cuenta (#194). */
public record RegistrarPagoRequest(
        @Schema(example = "2026-10-01", description = "Primer día que cubre el pago") LocalDate periodoDesde,
        @Schema(example = "2026-10-31", description = "Último día que cubre el pago (inclusive); como mucho un año después del primero") LocalDate periodoHasta,
        @Schema(example = "29.00", description = "En soles, mayor que cero, con hasta dos decimales") BigDecimal monto,
        @Schema(example = "YAPE", description = "`TRANSFERENCIA`, `DEPOSITO`, `YAPE`, `PLIN`, `TARJETA`, `EFECTIVO` u `OTRO`") MedioDePago medio,
        @Schema(example = "2026-10-14", description = "El día que el cliente pagó; no puede ser futuro") LocalDate fechaDePago,
        @Schema(example = "OP-123456", maxLength = 100, description = "Número de operación o similar. Opcional; si se da, esa cuenta no puede repetir medio y referencia") String referencia,
        @Schema(maxLength = 200, description = "Nota libre, opcional") String nota,
        @Schema(description = "Si es `true`, el vencimiento de la suscripción pasa a ser la medianoche (Lima) del día siguiente a `periodo_hasta`. Por defecto `false`") Boolean extenderVencimiento) {}
