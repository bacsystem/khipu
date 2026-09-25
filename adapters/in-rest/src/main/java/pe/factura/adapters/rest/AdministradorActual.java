package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import pe.factura.domain.DomainException;

import java.util.UUID;

/** Administrador de la plataforma autenticado por su propio JWT (ver AdminAuthFilter). */
public final class AdministradorActual {
    public static final String ATRIBUTO = "ADMINISTRADOR_ID";
    private AdministradorActual() {}

    public static UUID id(HttpServletRequest req) {
        Object v = req.getAttribute(ATRIBUTO);
        if (v == null) throw new DomainException("NO_AUTORIZADO", "Petición sin administrador autenticado por JWT");
        return (UUID) v;
    }
}
