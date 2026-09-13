package pe.factura.adapters.rest;

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

public class ApiKeyFilter extends OncePerRequestFilter {
    private final ApiKeyRepository apiKeys;
    private final String pepper;
    public ApiKeyFilter(ApiKeyRepository apiKeys, String pepper) { this.apiKeys = apiKeys; this.pepper = pepper; }

    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return !uri.startsWith("/v1/") || uri.startsWith("/v1/admin/");
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
