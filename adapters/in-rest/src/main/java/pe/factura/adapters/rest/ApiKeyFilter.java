package pe.factura.adapters.rest;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.application.service.ApiKeyGenerator;
import pe.factura.domain.tenant.ApiKey;

import java.io.IOException;
import java.util.Optional;

@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {
    private final ApiKeyRepository apiKeys;
    private final String pepper;

    /**
     * Decide sobre la ruta normalizada (ver {@link RutaRequest}). Las rutas de administración las
     * protege {@link PlatformKeyFilter}; las públicas de autenticación no exigen ningún credencial;
     * y una petición ya autenticada por JWT ({@link JwtFilter}, que se ejecuta antes) no vuelve a
     * exigir API key.
     */
    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        String ruta = RutaRequest.rutaNormalizada(req);
        if (!RutaRequest.esApiV1(ruta) || RutaRequest.esAdmin(ruta) || RutaRequest.esPublica(ruta)) return true;
        return req.getAttribute(CuentaActual.ATRIBUTO) != null;
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String key = req.getHeader("X-Api-Key");
        Optional<ApiKey> k = key == null || key.isBlank() ? Optional.empty() : apiKeys.buscarPorHash(ApiKeyGenerator.hash(key.trim(), pepper));
        if (k.isEmpty() || !k.get().activa()) {
            res.setStatus(401);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"estado\":\"error\",\"codigo\":\"NO_AUTORIZADO\",\"mensaje\":\"API key ausente o inválida\"}");
            return;
        }
        req.setAttribute(TenantActual.ATRIBUTO, k.get().tenantId());
        chain.doFilter(req, res);
    }
}
