package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ListarCuentasAdminUseCase.CuentaResumen;

import java.time.Instant;
import java.util.UUID;

public record CuentaAdminResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID id,
        @Schema(example = "Mi negocio") String nombre,
        @Schema(example = "ana@negocio.pe") String email,
        @Schema(example = "987654321", description = "Celular de contacto; ausente en cuentas anteriores a ese campo") String telefono,
        @Schema(example = "2026-09-01T10:00:00Z", description = "Fecha de alta") Instant creadaEn,
        @Schema(example = "2", description = "Empresas de la cuenta") int empresas,
        @Schema(example = "2026-10-01T09:00:00Z", description = "Última sesión de sus usuarios en el portal (inicio de sesión o refresco de token); el uso por API key no cuenta. Ausente si nunca inició sesión") Instant ultimoAcceso,
        @Schema(description = "ACTIVA o SUSPENDIDA. Una cuenta suspendida no entra al portal ni emite por API, y sigue en el listado para poder reactivarla") EstadoCuenta estado,
        @Schema(example = "2026-10-02T15:00:00Z", description = "Desde cuándo está suspendida; ausente si la cuenta está activa") Instant suspendidaEn) {
    public static CuentaAdminResponse de(CuentaResumen c) {
        return new CuentaAdminResponse(c.id(), c.nombre(), c.email(), c.telefono(), c.creadaEn(), c.empresas(), c.ultimoAcceso(), EstadoCuenta.de(c.suspendidaEn()), c.suspendidaEn());
    }
}
