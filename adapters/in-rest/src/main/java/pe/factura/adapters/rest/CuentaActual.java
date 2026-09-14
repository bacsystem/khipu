package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import pe.factura.domain.DomainException;

import java.util.UUID;

/** Cuenta autenticada por JWT (portal). Ausente en peticiones autenticadas por API key. */
public final class CuentaActual {
    public static final String ATRIBUTO = "CUENTA_ID";
    private CuentaActual() {}

    public static UUID id(HttpServletRequest req) {
        Object v = req.getAttribute(ATRIBUTO);
        if (v == null) throw new DomainException("NO_AUTORIZADO", "Petición sin cuenta autenticada por JWT");
        return (UUID) v;
    }
}
