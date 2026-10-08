package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UrlPathHelper;

/**
 * Ruta de la petición tal como la verá el enrutador, no tal como llegó por la red.
 * <p>
 * Tomcat/Spring decodifican {@code %xx}, descartan los parámetros de segmento ({@code ;x}),
 * colapsan {@code //} y resuelven {@code ..} antes de elegir el controlador. Un filtro que
 * decida con {@code getRequestURI()} (la URI cruda) puede dejar pasar
 * {@code /v1;x/admin/tenants} o {@code /v1/%61dmin/tenants} hacia rutas protegidas.
 * Ambos filtros de autenticación deben decidir sobre esta ruta normalizada.
 */
final class RutaRequest {
    private RutaRequest() {}

    /** Rutas de autenticación que no exigen ni API key ni JWT (el cliente aún no tiene ninguno). */
    private static final java.util.Set<String> AUTH_PUBLICA = java.util.Set.of(
            "/v1/auth/registro", "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/recuperar", "/v1/auth/restablecer", "/v1/auth/verificar");

    static String rutaNormalizada(HttpServletRequest req) {
        String ruta = UrlPathHelper.defaultInstance.getPathWithinApplication(req);
        return StringUtils.cleanPath(ruta);
    }

    static boolean esAdmin(String ruta) { return ruta.equals("/v1/admin") || ruta.startsWith("/v1/admin/"); }

    /**
     * Dentro de /v1/admin/**, el login del backoffice y sus pasos del segundo factor (#177): el administrador aún no tiene JWT ni
     * X-Platform-Key. Los pasos del segundo factor exigen el desafío que solo da la contraseña.
     */
    private static final java.util.Set<String> ADMIN_AUTH_PUBLICA = java.util.Set.of(
            "/v1/admin/auth/login", "/v1/admin/auth/segundo-factor/configurar", "/v1/admin/auth/segundo-factor/confirmar",
            "/v1/admin/auth/segundo-factor/verificar");

    static boolean esAdminAuthPublica(String ruta) { return ADMIN_AUTH_PUBLICA.contains(ruta); }

    static boolean esApiV1(String ruta) { return ruta.equals("/v1") || ruta.startsWith("/v1/"); }

    /**
     * Sin credenciales: rutas de autenticación (el cliente aún no tiene ninguna), los catálogos SUNAT (información pública de referencia), el aviso de mantenimiento (#199), que el
     * portal muestra también antes de iniciar sesión, y los planes publicados (H20), que lee la página de precios.
     */
    static boolean esPublica(String ruta) {
        return AUTH_PUBLICA.contains(ruta) || ruta.equals("/v1/catalogos") || ruta.startsWith("/v1/catalogos/") || ruta.equals("/v1/banner") || ruta.equals("/v1/planes");
    }
}
