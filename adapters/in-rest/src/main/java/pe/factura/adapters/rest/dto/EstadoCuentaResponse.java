package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.SuspenderCuentaUseCase.EstadoDeCuenta;

import java.time.Instant;
import java.util.UUID;

/**
 * El estado en que quedó una cuenta después de suspenderla o reactivarla (#182). El estado se calcula igual que en el listado y el detalle: una
 * cuenta de baja (#201) es {@code BAJA} aunque se la suspenda o reactive.
 */
public record EstadoCuentaResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID cuentaId,
        EstadoCuenta estado,
        @Schema(example = "2026-10-02T15:00:00Z", description = "Desde cuándo está suspendida; ausente si la cuenta no lo está") Instant suspendidaEn,
        @Schema(example = "2026-10-03T09:00:00Z", description = "Desde cuándo está dada de baja (#201); ausente si está en servicio") Instant bajaEn) {
    public static EstadoCuentaResponse de(EstadoDeCuenta e) {
        return new EstadoCuentaResponse(e.cuentaId(), EstadoCuenta.de(e.suspendidaEn(), e.bajaEn()), e.suspendidaEn(), e.bajaEn());
    }
}
