package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AutenticarAdministradorUseCase.Sesion;

public record AdminSesionResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ...") String accessToken,
        AdministradorResponse administrador) {
    public static AdminSesionResponse de(Sesion s) {
        return new AdminSesionResponse(s.accessToken(), AdministradorResponse.de(s.administrador()));
    }
}
