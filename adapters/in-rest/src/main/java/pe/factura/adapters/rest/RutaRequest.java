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
            "/v1/auth/registro", "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/recuperar", "/v1/auth/restablecer");

    static String rutaNormalizada(HttpServletRequest req) {
        String ruta = UrlPathHelper.defaultInstance.getPathWithinApplication(req);
        return StringUtils.cleanPath(ruta);
    }

    static boolean esAdmin(String ruta) { return ruta.equals("/v1/admin") || ruta.startsWith("/v1/admin/"); }

    /** Dentro de /v1/admin/**, el login del backoffice: el administrador aún no tiene JWT ni X-Platform-Key. */
    static boolean esAdminAuthPublica(String ruta) { return ruta.equals("/v1/admin/auth/login"); }

    static boolean esApiV1(String ruta) { return ruta.equals("/v1") || ruta.startsWith("/v1/"); }

    /** Sin credenciales: rutas de autenticación (el cliente aún no tiene ninguna) y los catálogos SUNAT (información pública de referencia). */
    static boolean esPublica(String ruta) { return AUTH_PUBLICA.contains(ruta) || ruta.equals("/v1/catalogos") || ruta.startsWith("/v1/catalogos/"); }
}
