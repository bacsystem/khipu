package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.ImpersonarUsuarioUseCase.Impersonacion;

import java.time.Instant;

/** La sesión de soporte que se abrió (#184). El token es una credencial y solo se entrega aquí, una vez; nunca lleva el hash de la contraseña del usuario. */
public record ImpersonacionResponse(
        @Schema(description = "JWT de una sesión de soporte: del usuario al que se mira, **solo lectura** y sin refresh") String accessToken,
        @Schema(example = "2026-10-04T12:15:00Z", description = "Hasta cuándo vale; no se puede renovar: para seguir mirando se impersona de nuevo, y cada vez queda su registro") Instant expiraEn,
        @Schema(description = "A quién se está mirando") UsuarioResponse usuario) {
    public static ImpersonacionResponse de(Impersonacion i) {
        return new ImpersonacionResponse(i.token(), i.expiraEn(), UsuarioResponse.de(i.usuario()));
    }
}
