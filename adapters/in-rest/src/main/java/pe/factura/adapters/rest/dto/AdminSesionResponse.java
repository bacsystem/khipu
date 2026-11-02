package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AutenticarAdministradorUseCase.Sesion;

public record AdminSesionResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ...") String accessToken,
        @Schema(description = "Segundos de vida del token (`ADMIN_SESION_MINUTOS`). Sin refresh: al vencer se vuelve a iniciar sesión.", example = "1800") long expiraEn,
        AdministradorResponse administrador) {
    public static AdminSesionResponse de(Sesion s) {
        return new AdminSesionResponse(s.accessToken(), s.expiraEnSegundos(), AdministradorResponse.de(s.administrador()));
    }
}
