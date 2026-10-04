package pe.factura.adapters.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.factura.application.port.out.SuspensionRepository;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.TokenEmisor;
import pe.factura.application.port.out.UsuarioRepository;
import pe.factura.domain.cuenta.Usuario;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
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
    private final UsuarioRepository usuarios;
    private final SuspensionRepository suspensiones;
    /** Lo que no escribe: con el correo sin verificar se puede mirar (#22). */
    private static final Set<String> LECTURAS = Set.of("GET", "HEAD", "OPTIONS");

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

        String ruta = RutaRequest.rutaNormalizada(req);

        // Una cuenta suspendida (#182) no entra: ni a mirar ni a escribir, y tampoco con una sesión que ya estaba abierta, porque se mira la
        // cuenta en la base y no el token. Después de validar el token: quien presenta uno inválido no se entera del estado de ninguna cuenta.
        // Lo de la propia sesión (/v1/auth/**: quién soy, cerrar sesión) sigue funcionando, para que el portal pueda decirle «tu cuenta está
        // suspendida» en vez de mostrarle un error suelto.
        if (!ruta.startsWith("/v1/auth/") && suspensiones.cuentaSuspendida(claims.get().cuentaId())) {
            escribirError(res, 403, "CUENTA_SUSPENDIDA", "Tu cuenta está suspendida. Contacta a soporte para reactivarla");
            return;
        }

        // Sin verificar el correo (#22) se puede entrar y mirar, pero no escribir: ni crear empresas ni emitir. Lo de la propia sesión
        // (/v1/auth/**: reenviar el enlace, cerrar sesión) sí. Se mira el usuario en la base, no el token: verificado en otra pestaña,
        // la siguiente escritura ya pasa.
        if (!LECTURAS.contains(req.getMethod()) && !ruta.startsWith("/v1/auth/")) {
            Optional<Usuario> usuario = usuarios.buscar(claims.get().usuarioId()).filter(Usuario::activo);
            if (usuario.isEmpty()) {
                escribirError(res, 401, "NO_AUTORIZADO", "Token inválido o expirado");
                return;
            }
            if (!usuario.get().correoVerificado()) {
                escribirError(res, 403, "CORREO_SIN_VERIFICAR", "Verifica tu correo para continuar: te enviamos un enlace");
                return;
            }
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
