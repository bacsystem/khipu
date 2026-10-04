package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.SuspenderCuentaUseCase.EstadoDeCuenta;

import java.time.Instant;
import java.util.UUID;

/** El estado en que quedó una cuenta después de suspenderla o reactivarla (#182). */
public record EstadoCuentaResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID cuentaId,
        EstadoCuenta estado,
        @Schema(example = "2026-10-02T15:00:00Z", description = "Desde cuándo está suspendida; ausente si la cuenta está activa") Instant suspendidaEn) {
    public static EstadoCuentaResponse de(EstadoDeCuenta e) {
        return new EstadoCuentaResponse(e.cuentaId(), EstadoCuenta.de(e.suspendidaEn()), e.suspendidaEn());
    }
}
