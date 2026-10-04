package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import pe.factura.application.port.out.TokenEmisor;

import java.util.Optional;

/** Si la petición viene de una sesión de soporte (#184: un administrador mirando el portal como un cliente), qué administrador la abrió y hasta cuándo vale. */
public final class SoporteActual {
    public static final String ATRIBUTO = "SOPORTE";
    private SoporteActual() {}

    public static Optional<TokenEmisor.Soporte> de(HttpServletRequest req) {
        return Optional.ofNullable((TokenEmisor.Soporte) req.getAttribute(ATRIBUTO));
    }
}
