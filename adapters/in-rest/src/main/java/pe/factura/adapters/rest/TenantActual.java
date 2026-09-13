package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class TenantActual {
    public static final String ATRIBUTO = "TENANT_ID";
    private TenantActual() {}
    public static UUID id(HttpServletRequest req) {
        Object v = req.getAttribute(ATRIBUTO);
        if (v == null) throw new IllegalStateException("Petición sin tenant autenticado");
        return (UUID) v;
    }
}
