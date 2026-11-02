package pe.factura.adapters.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.factura.application.port.out.ApiKeyRepository;
import pe.factura.application.port.out.PepperDeApiKeysRepository;
import pe.factura.application.port.out.SuspensionRepository;
import pe.factura.application.service.ApiKeyGenerator;
import pe.factura.domain.tenant.ApiKey;

import java.io.IOException;
import java.util.Optional;

public class ApiKeyFilter extends OncePerRequestFilter {
    private final ApiKeyRepository apiKeys;
    private final String pepper;
    /** S2: el pepper de antes de rotar; {@code null} si no se está rotando. */
    private final String pepperAnterior;
    private final PepperDeApiKeysRepository rotacion;
    private final SuspensionRepository suspensiones;

    public ApiKeyFilter(ApiKeyRepository apiKeys, String pepper, SuspensionRepository suspensiones) {
        this(apiKeys, pepper, null, null, suspensiones);
    }

    public ApiKeyFilter(ApiKeyRepository apiKeys, String pepper, String pepperAnterior, PepperDeApiKeysRepository rotacion, SuspensionRepository suspensiones) {
        this.apiKeys = apiKeys;
        this.pepper = pepper;
        this.pepperAnterior = pepperAnterior == null || pepperAnterior.isBlank() ? null : pepperAnterior;
        this.rotacion = rotacion;
        this.suspensiones = suspensiones;
    }

    /**
     * Decide sobre la ruta normalizada (ver {@link RutaRequest}). Las rutas de administración las
     * protege {@link AdminAuthFilter}; las públicas de autenticación no exigen ningún credencial;
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
        Optional<ApiKey> k = key == null || key.isBlank() ? Optional.empty() : buscar(key.trim());
        if (k.isEmpty() || !k.get().activa()) {
            res.setStatus(401);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"estado\":\"error\",\"codigo\":\"NO_AUTORIZADO\",\"mensaje\":\"API key ausente o inválida\"}");
            return;
        }
        // Después de validar la key: quien no tiene una válida no se entera del estado de ninguna cuenta (#182). La empresa de una cuenta
        // suspendida no emite ni consulta; las ya emitidas siguen enviándose a SUNAT por el outbox, que no pasa por aquí.
        if (suspensiones.empresaSuspendida(k.get().tenantId())) {
            res.setStatus(403);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"estado\":\"error\",\"codigo\":\"CUENTA_SUSPENDIDA\",\"mensaje\":\"La cuenta de esta empresa está suspendida. Contacta a soporte para reactivarla\"}");
            return;
        }
        req.setAttribute(TenantActual.ATRIBUTO, k.get().tenantId());
        chain.doFilter(req, res);
    }

    /**
     * Con el pepper vigente; si no aparece y se está rotando (S2), con el anterior, y entonces se guarda de una vez con el vigente: cada key que se usa
     * durante la rotación deja de depender del pepper anterior. Una revocada se encuentra igual, pero no se re-hashea ni autentica.
     */
    private Optional<ApiKey> buscar(String key) {
        Optional<ApiKey> k = apiKeys.buscarPorHash(ApiKeyGenerator.hash(key, pepper));
        if (k.isPresent() || pepperAnterior == null) return k;
        String hashAnterior = ApiKeyGenerator.hash(key, pepperAnterior);
        Optional<ApiKey> vieja = apiKeys.buscarPorHash(hashAnterior);
        vieja.filter(ApiKey::activa).ifPresent(v -> rotacion.rehashear(v.id(), hashAnterior, ApiKeyGenerator.hash(key, pepper)));
        return vieja;
    }
}
