package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AutenticarAdministradorUseCase.Desafio;
import pe.factura.application.port.in.AutenticarAdministradorUseCase.Paso;

public record AdminDesafioResponse(
        @Schema(description = "Token de 5 minutos para completar el login. No autentica ninguna otra ruta.", example = "eyJhbGciOiJIUzI1NiJ9...") String desafio,
        @Schema(description = "`CONFIGURAR_SEGUNDO_FACTOR` la primera vez; después, `VERIFICAR_SEGUNDO_FACTOR`.") Paso paso) {
    public static AdminDesafioResponse de(Desafio d) { return new AdminDesafioResponse(d.token(), d.paso()); }
}
