package pe.factura.adapters.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.factura.application.port.out.AdministradorTokenEmisor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Autentica /v1/admin/**: por la clave de plataforma (X-Platform-Key, herramientas internas/ops) o por
 * el JWT de un administrador (Authorization: Bearer, sesión humana del backoffice) — cualquiera de las
 * dos vale, igual que las rutas de tenant aceptan X-Api-Key o JWT+X-Empresa. El login del backoffice
 * (/v1/admin/auth/login) es la única ruta admin sin credencial: ahí el administrador todavía no tiene
 * ninguna de las dos (ver {@link RutaRequest#esAdminAuthPublica}).
 */
public class AdminAuthFilter extends OncePerRequestFilter {
    private static final String PREFIJO_BEARER = "Bearer ";

    private final String platformKey;
    private final AdministradorTokenEmisor tokens;

    public AdminAuthFilter(String platformKey, AdministradorTokenEmisor tokens) {
        this.platformKey = platformKey == null ? "" : platformKey;
        this.tokens = tokens;
    }

    /** Decide sobre la ruta normalizada (ver {@link RutaRequest}), nunca sobre la URI cruda. */
    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        String ruta = RutaRequest.rutaNormalizada(req);
        return !RutaRequest.esAdmin(ruta) || RutaRequest.esAdminAuthPublica(ruta);
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String claveDada = req.getHeader("X-Platform-Key");
        if (claveDada != null) {
            // Clave de ops sin configurar: se oculta el prefijo entero (404) en vez de admitir que existe.
            if (platformKey.isBlank()) { res.setStatus(404); return; }
            if (MessageDigest.isEqual(claveDada.getBytes(StandardCharsets.UTF_8), platformKey.getBytes(StandardCharsets.UTF_8))) {
                chain.doFilter(req, res);
                return;
            }
            escribirError(res, "Clave de plataforma inválida");
            return;
        }

        String auth = req.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, PREFIJO_BEARER, 0, PREFIJO_BEARER.length())) {
            Optional<AdministradorTokenEmisor.Claims> claims = tokens.verificar(auth.substring(PREFIJO_BEARER.length()).trim());
            if (claims.isPresent()) {
                req.setAttribute(AdministradorActual.ATRIBUTO, claims.get().administradorId());
                chain.doFilter(req, res);
                return;
            }
        }
        escribirError(res, "Clave de plataforma o sesión de administrador inválida");
    }

    private static void escribirError(HttpServletResponse res, String mensaje) throws IOException {
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"estado\":\"error\",\"codigo\":\"NO_AUTORIZADO\",\"mensaje\":\"" + mensaje + "\"}");
    }
}
