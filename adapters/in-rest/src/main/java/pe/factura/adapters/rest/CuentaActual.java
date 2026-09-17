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

    /**
     * Para operaciones reservadas al portal (gestión de API keys): una petición autenticada por
     * API key tiene tenant pero no cuenta, y no debe poder crear, listar ni revocar keys.
     */
    public static void exigirSesion(HttpServletRequest req) {
        if (req.getAttribute(ATRIBUTO) == null) throw new DomainException("REQUIERE_SESION", "Esta operación solo está disponible desde el portal (sesión de cuenta), no con API key");
    }
}
