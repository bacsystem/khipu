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
        @Schema(example = "false", description = "`true` si no aceptó la versión vigente de los términos del servicio y la política de privacidad (#342): un usuario anterior a ellos, "
                + "o uno que aceptó una versión que ya cambió. El portal no deja usar el panel hasta que acepte (`POST /v1/auth/terminos`). Ausente donde no aplica "
                + "(la respuesta de abrir una sesión de soporte).")
        Boolean terminosPendientes,
        @Schema(example = "2026-10-04T12:15:00Z", description = "Solo en una sesión de soporte (#184: un administrador mirando el portal como este usuario): hasta cuándo vale. Ausente en una sesión normal. El portal lo usa para avisar que se está actuando como el cliente")
        Instant soporteHasta,
        @Schema(example = "30", minimum = "0", maximum = "480", description = "Minutos sin renovar la sesión tras los cuales el servidor la corta (#638): `0` si el corte está "
                + "desactivado, o de 15 a 480. El corte real llega hasta ese valor más 15 min, la vida del access token. El portal lo usa para avisar antes del cierre. "
                + "Solo en `GET /v1/auth/me` de una sesión normal: ausente en una sesión de soporte (no se renueva, vence a hora fija) y en el `usuario` del login, el refresh, "
                + "el registro y la sesión de soporte.")
        Integer sesionInactividadMinutos) {
    /** Sin {@code terminosPendientes}: para quien no es el propio usuario (el administrador que abre una sesión de soporte). */
    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.id(), u.cuentaId(), u.email(), u.rol().name(), u.correoVerificado(), null, null, null);
    }

    /** El usuario de la sesión, diciendo hasta cuándo vale si es de soporte. Del administrador solo se usa la expiración: nunca sale su id. */
    public static UsuarioResponse de(Usuario u, boolean terminosPendientes, TokenEmisor.Soporte soporte) {
        return new UsuarioResponse(u.id(), u.cuentaId(), u.email(), u.rol().name(), u.correoVerificado(), terminosPendientes,
                soporte == null ? null : soporte.expiraEn(), null);
    }

    /** Lo que devuelve {@code GET /v1/auth/me}: además, el corte por inactividad, salvo en una sesión de soporte, que no se renueva. */
    public static UsuarioResponse de(Usuario u, boolean terminosPendientes, TokenEmisor.Soporte soporte, int sesionInactividadMinutos) {
        return new UsuarioResponse(u.id(), u.cuentaId(), u.email(), u.rol().name(), u.correoVerificado(), terminosPendientes,
                soporte == null ? null : soporte.expiraEn(), soporte == null ? sesionInactividadMinutos : null);
    }
}
