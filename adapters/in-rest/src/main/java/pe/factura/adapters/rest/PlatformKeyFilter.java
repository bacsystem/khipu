package pe.factura.adapters.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class PlatformKeyFilter extends OncePerRequestFilter {
    private final String platformKey;
    public PlatformKeyFilter(String platformKey) { this.platformKey = platformKey == null ? "" : platformKey; }

    @Override protected boolean shouldNotFilter(HttpServletRequest req) { return !req.getRequestURI().startsWith("/v1/admin/"); }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (platformKey.isBlank()) { res.setStatus(404); return; }
        String dada = req.getHeader("X-Platform-Key");
        boolean ok = dada != null && MessageDigest.isEqual(dada.getBytes(StandardCharsets.UTF_8), platformKey.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"estado\":\"error\",\"codigo\":\"NO_AUTORIZADO\",\"mensaje\":\"Clave de plataforma inválida\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
