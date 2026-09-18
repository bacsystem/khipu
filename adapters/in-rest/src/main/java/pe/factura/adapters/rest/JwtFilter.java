package pe.factura.adapters.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.TokenEmisor;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Autentica peticiones del portal por JWT (cabecera {@code Authorization: Bearer <token>}).
 * <p>
 * Si la petición ya trae {@code X-Api-Key}, este filtro se aparta y deja que {@link ApiKeyFilter}
 * decida (los integradores no cambian). Si el JWT es válido, expone la cuenta autenticada
 * ({@link CuentaActual}) y, cuando la cabecera {@code X-Empresa} identifica una empresa de esa
 * cuenta, también el tenant activo ({@link TenantActual}) para que los controladores existentes
 * (pensados para API key) funcionen sin cambios.
 */
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private static final String PREFIJO_BEARER = "Bearer ";

    private final TokenEmisor tokenEmisor;
    private final TenantRepository tenants;

    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        String ruta = RutaRequest.rutaNormalizada(req);
        if (!RutaRequest.esApiV1(ruta) || RutaRequest.esAdmin(ruta) || RutaRequest.esPublica(ruta)) return true;
        String apiKey = req.getHeader("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) return true;
        String auth = req.getHeader("Authorization");
        return auth == null || !auth.regionMatches(true, 0, PREFIJO_BEARER, 0, PREFIJO_BEARER.length());
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String jwt = req.getHeader("Authorization").substring(PREFIJO_BEARER.length()).trim();
        Optional<TokenEmisor.Claims> claims = tokenEmisor.verificar(jwt);
        if (claims.isEmpty()) {
            escribirError(res, 401, "NO_AUTORIZADO", "Token inválido o expirado");
            return;
        }

        req.setAttribute(CuentaActual.ATRIBUTO, claims.get().cuentaId());
        req.setAttribute(UsuarioActual.ATRIBUTO, claims.get().usuarioId());

        String empresaHeader = req.getHeader("X-Empresa");
        if (empresaHeader != null && !empresaHeader.isBlank()) {
            UUID tenantId;
            try {
                tenantId = UUID.fromString(empresaHeader.trim());
            } catch (IllegalArgumentException e) {
                escribirError(res, 400, "EMPRESA_INVALIDA", "X-Empresa no es un identificador válido");
                return;
            }
            Optional<UUID> cuentaDeLaEmpresa = tenants.cuentaDe(tenantId);
            if (cuentaDeLaEmpresa.isEmpty() || !cuentaDeLaEmpresa.get().equals(claims.get().cuentaId())) {
                escribirError(res, 403, "EMPRESA_AJENA", "La empresa no pertenece a tu cuenta");
                return;
            }
            req.setAttribute(TenantActual.ATRIBUTO, tenantId);
        }
        chain.doFilter(req, res);
    }

    private static void escribirError(HttpServletResponse res, int status, String codigo, String mensaje) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"estado\":\"error\",\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje + "\"}");
    }
}
