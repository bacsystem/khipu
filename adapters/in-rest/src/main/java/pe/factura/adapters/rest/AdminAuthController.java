package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.*;
import pe.factura.application.port.in.AutenticarAdministradorUseCase;

/**
 * Sesión del backoffice de administración (#175/#176), en dos pasos desde #177: la contraseña da un desafío y el segundo factor da
 * la sesión. Rutas bajo {@code /v1/admin} pero, a diferencia del resto de ese prefijo, el login y los pasos del segundo factor son
 * públicos: el administrador todavía no tiene ni X-Platform-Key ni JWT (ver {@link RutaRequest#esAdminAuthPublica}). Los protege el
 * desafío, que solo se obtiene con la contraseña.
 */
@RestController
@RequestMapping("/v1/admin/auth")
@Tag(name = "Autenticación (backoffice)", description = "Sesión del panel de administración de la plataforma. No es la misma sesión que la del portal de clientes.")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AutenticarAdministradorUseCase auth;
    private final IpDelCliente ip;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión de administrador: paso 1, contraseña", description = """
            Con la contraseña correcta devuelve un `desafio` de 5 minutos y el `paso` siguiente: `CONFIGURAR_SEGUNDO_FACTOR` si el
            administrador todavía no tiene app de autenticación, `VERIFICAR_SEGUNDO_FACTOR` si ya la tiene. La contraseña sola nunca da
            una sesión. Tras 5 contraseñas erróneas en 15 minutos para ese correo (o 20 fallos desde la misma IP),
            `429 DEMASIADOS_INTENTOS_LOGIN` durante 15 minutos, aun con la contraseña correcta.""")
    public ApiResponse<AdminDesafioResponse> login(HttpServletRequest req, @Valid @RequestBody AdminLoginRequest body) {
        return ApiResponse.ok(AdminDesafioResponse.de(auth.login(body.email(), body.password(), ip.de(req))));
    }

    @PostMapping("/segundo-factor/configurar")
    @Operation(summary = "Paso 2a: generar el secreto de la app de autenticación", description = """
            Solo si el segundo factor no está configurado (`409 SEGUNDO_FACTOR_YA_CONFIGURADO` si lo está: la contraseña sola no puede
            cambiarlo). Devuelve el QR para escanear y el secreto en texto. Pedirlo otra vez reemplaza el anterior.""")
    public ApiResponse<AdminConfiguracionResponse> configurar(@Valid @RequestBody AdminDesafioRequest body) {
        return ApiResponse.ok(AdminConfiguracionResponse.de(auth.configurarSegundoFactor(body.desafio())));
    }

    @PostMapping("/segundo-factor/confirmar")
    @Operation(summary = "Paso 2b: confirmar con el primer código y entrar", description = """
            Con el primer código de la app confirma el segundo factor y abre la sesión. Devuelve **una sola vez** diez códigos de
            recuperación. `401 CODIGO_INVALIDO` si el código no es válido; tras 5 fallos, `429 DEMASIADOS_INTENTOS` durante 15 minutos.""")
    public ApiResponse<AdminSesionNuevaResponse> confirmar(HttpServletRequest req, @Valid @RequestBody AdminCodigoRequest body) {
        return ApiResponse.ok(AdminSesionNuevaResponse.de(auth.confirmarSegundoFactor(body.desafio(), body.codigo(), req.getRemoteAddr())));
    }

    @PostMapping("/segundo-factor/verificar")
    @Operation(summary = "Paso 2: verificar el segundo factor y entrar", description = """
            Acepta los 6 dígitos de la app o un código de recuperación (cada uno sirve una vez). Un código de la app tampoco se acepta
            dos veces. `401 CODIGO_INVALIDO` si no es válido; tras 5 fallos, `429 DEMASIADOS_INTENTOS` durante 15 minutos.""")
    public ApiResponse<AdminSesionResponse> verificar(HttpServletRequest req, @Valid @RequestBody AdminCodigoRequest body) {
        return ApiResponse.ok(AdminSesionResponse.de(auth.verificarSegundoFactor(body.desafio(), body.codigo(), req.getRemoteAddr())));
    }

    @GetMapping("/me")
    @Operation(summary = "Administrador de la sesión", description = "Identidad del administrador autenticado por el JWT del backoffice.")
    public ApiResponse<AdministradorResponse> me(HttpServletRequest req) {
        return ApiResponse.ok(AdministradorResponse.de(auth.me(AdministradorActual.id(req))));
    }
}
