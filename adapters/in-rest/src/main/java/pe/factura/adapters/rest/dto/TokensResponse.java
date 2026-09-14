package pe.factura.adapters.rest.dto;

import pe.factura.application.port.in.AutenticarUsuarioUseCase.Tokens;

public record TokensResponse(String access, String refresh, UsuarioResponse usuario) {
    public static TokensResponse de(Tokens t) {
        return new TokensResponse(t.access(), t.refresh(), UsuarioResponse.de(t.usuario()));
    }
}
