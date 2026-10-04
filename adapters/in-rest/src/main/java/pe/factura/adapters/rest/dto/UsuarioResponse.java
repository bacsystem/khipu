package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.domain.cuenta.Usuario;

import java.time.Instant;
import java.util.UUID;

public record UsuarioResponse(
        @Schema(example = "9c1f3a2b-4d5e-4a6b-8c7d-1e2f3a4b5c6d") UUID id,
        @Schema(example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d") UUID cuentaId,
        @Schema(example = "facturacion@comercialandina.pe") String email,
        @Schema(example = "ADMIN") String rol,
        @Schema(description = "`false` hasta abrir el enlace de verificación (#22): sin verificar no puede crear empresas ni emitir desde el portal.", example = "true")
        boolean correoVerificado,
        @Schema(example = "2026-10-04T12:15:00Z", description = "Solo en una sesión de soporte (#184: un administrador mirando el portal como este usuario): hasta cuándo vale. Ausente en una sesión normal. El portal lo usa para avisar que se está actuando como el cliente")
        Instant soporteHasta) {
    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.id(), u.cuentaId(), u.email(), u.rol().name(), u.correoVerificado(), null);
    }

    /** El usuario de la sesión, diciendo hasta cuándo vale si es de soporte. Del administrador solo se usa la expiración: nunca sale su id. */
    public static UsuarioResponse de(Usuario u, TokenEmisor.Soporte soporte) {
        return new UsuarioResponse(u.id(), u.cuentaId(), u.email(), u.rol().name(), u.correoVerificado(), soporte == null ? null : soporte.expiraEn());
    }
}
