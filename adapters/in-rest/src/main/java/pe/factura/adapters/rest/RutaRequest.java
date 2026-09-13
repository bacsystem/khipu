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

    static String rutaNormalizada(HttpServletRequest req) {
        String ruta = UrlPathHelper.defaultInstance.getPathWithinApplication(req);
        return StringUtils.cleanPath(ruta);
    }

    static boolean esAdmin(String ruta) { return ruta.equals("/v1/admin") || ruta.startsWith("/v1/admin/"); }

    static boolean esApiV1(String ruta) { return ruta.equals("/v1") || ruta.startsWith("/v1/"); }
}
