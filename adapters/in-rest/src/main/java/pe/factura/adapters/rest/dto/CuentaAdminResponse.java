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
        @Schema(description = "ACTIVA, SUSPENDIDA o BAJA. Una cuenta suspendida no entra al portal ni emite por API, y sigue en el listado para poder reactivarla. Una de BAJA (#201) es la de un cliente que se fue: solo sale si se pide con `bajas`, y manda sobre la suspensión") EstadoCuenta estado,
        @Schema(example = "2026-10-02T15:00:00Z", description = "Desde cuándo está suspendida; ausente si la cuenta no lo está") Instant suspendidaEn,
        @Schema(example = "2026-10-03T09:00:00Z", description = "Desde cuándo está dada de baja; ausente si está en servicio") Instant bajaEn) {
    public static CuentaAdminResponse de(CuentaResumen c) {
        return new CuentaAdminResponse(c.id(), c.nombre(), c.email(), c.telefono(), c.creadaEn(), c.empresas(), c.ultimoAcceso(), EstadoCuenta.de(c.suspendidaEn(), c.bajaEn()), c.suspendidaEn(), c.bajaEn());
    }
}
