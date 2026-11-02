package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AutenticarAdministradorUseCase.SesionNueva;

import java.util.List;

/** La sesión que abre la primera configuración del segundo factor, con los códigos de recuperación: solo viajan en esta respuesta. */
public record AdminSesionNuevaResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ...") String accessToken,
        @Schema(example = "1800") long expiraEn,
        AdministradorResponse administrador,
        @Schema(description = "Diez códigos de un solo uso para entrar sin la app. No se pueden volver a consultar.", example = "[\"K7M2P-QX9RT\"]")
        List<String> codigosRecuperacion) {
    public static AdminSesionNuevaResponse de(SesionNueva n) {
        return new AdminSesionNuevaResponse(n.sesion().accessToken(), n.sesion().expiraEnSegundos(),
                AdministradorResponse.de(n.sesion().administrador()), n.codigosRecuperacion());
    }
}
