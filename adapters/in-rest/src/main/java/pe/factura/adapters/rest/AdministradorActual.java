package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.ActorAdmin;

import java.util.UUID;

/** Quién llama a /v1/admin/**: un administrador con su propio JWT o la clave de plataforma (ver AdminAuthFilter). */
public final class AdministradorActual {
    public static final String ATRIBUTO = "ADMINISTRADOR_ID";
    /** Lo marca AdminAuthFilter cuando la petición entró con X-Platform-Key; sin él no se asume que lo hizo la clave. */
    public static final String ATRIBUTO_CLAVE_PLATAFORMA = "CLAVE_PLATAFORMA";
    private AdministradorActual() {}

    public static UUID id(HttpServletRequest req) {
        Object v = req.getAttribute(ATRIBUTO);
        if (v == null) throw new DomainException("NO_AUTORIZADO", "Petición sin administrador autenticado por JWT");
        return (UUID) v;
    }

    /**
     * El actor de la bitácora de auditoría. Falla cerrado: sin ninguna de las dos credenciales validadas por el filtro no
     * se fabrica una identidad — la acción no se ejecuta con un autor inventado. La IP es la de la conexión; detrás de un
     * proxy o del BFF del portal será la de ese salto hasta que se configure qué proxies son de confianza.
     */
    public static ActorAdmin actor(HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        if (req.getAttribute(ATRIBUTO) != null) return ActorAdmin.administrador(id(req), ip);
        if (Boolean.TRUE.equals(req.getAttribute(ATRIBUTO_CLAVE_PLATAFORMA))) return ActorAdmin.clavePlataforma(ip);
        throw new DomainException("NO_AUTORIZADO", "Petición sin administrador ni clave de plataforma autenticados");
    }
}
