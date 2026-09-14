package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.factura.application.port.in.AutenticarUsuarioUseCase.Tokens;

public record TokensResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ...") String access,
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ...") String refresh,
        UsuarioResponse usuario) {
    public static TokensResponse de(Tokens t) {
        return new TokensResponse(t.access(), t.refresh(), UsuarioResponse.de(t.usuario()));
    }
}
