package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.AdminLoginRequest;
import pe.factura.adapters.rest.dto.AdminSesionResponse;
import pe.factura.adapters.rest.dto.AdministradorResponse;
import pe.factura.application.port.in.AutenticarAdministradorUseCase;

/**
 * Sesión del backoffice de administración (#175/#176). Ruta bajo {@code /v1/admin} pero, a diferencia
 * del resto de ese prefijo, {@code /login} es pública: el administrador todavía no tiene ni
 * X-Platform-Key ni JWT en ese momento (ver {@link RutaRequest#esAdminAuthPublica}).
 */
@RestController
@RequestMapping("/v1/admin/auth")
@Tag(name = "Autenticación (backoffice)", description = "Sesión del panel de administración de la plataforma. No es la misma sesión que la del portal de clientes.")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AutenticarAdministradorUseCase auth;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión de administrador", description = "Devuelve un token de acceso de vida corta (30 min). Sin refresh: al expirar, hay que volver a iniciar sesión.")
    public ApiResponse<AdminSesionResponse> login(@Valid @RequestBody AdminLoginRequest body) {
        return ApiResponse.ok(AdminSesionResponse.de(auth.login(body.email(), body.password())));
    }

    @GetMapping("/me")
    @Operation(summary = "Administrador de la sesión", description = "Identidad del administrador autenticado por el JWT del backoffice.")
    public ApiResponse<AdministradorResponse> me(HttpServletRequest req) {
        return ApiResponse.ok(AdministradorResponse.de(auth.me(AdministradorActual.id(req))));
    }
}
