package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import pe.factura.domain.DomainException;

import java.util.UUID;

/** Usuario autenticado por JWT (portal). */
public final class UsuarioActual {
    public static final String ATRIBUTO = "USUARIO_ID";
    private UsuarioActual() {}

    public static UUID id(HttpServletRequest req) {
        Object v = req.getAttribute(ATRIBUTO);
        if (v == null) throw new DomainException("NO_AUTORIZADO", "Petición sin usuario autenticado por JWT");
        return (UUID) v;
    }
}
