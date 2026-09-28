package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.*;
import pe.factura.application.port.in.AutenticarUsuarioUseCase;
import pe.factura.domain.DomainException;

/** Endpoints de autenticación del portal (JWT). Los de registro/login/refresh/recuperar/restablecer son públicos. */
@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Autenticación (portal)", description = """
        Flujo de sesión del portal web: registro, login con tokens JWT (acceso corto + refresh rotativo), recuperación de
        contraseña por correo. **Los integradores no usan estas rutas**: autentican cada petición con `X-Api-Key`.""")
public class AuthController {
    private final AutenticarUsuarioUseCase auth;
    private final String portalUrl;
    private final boolean registroAbierto;

    public AuthController(AutenticarUsuarioUseCase auth, @Value("${app.portal-url}") String portalUrl,
                           @Value("${app.registro-abierto:false}") boolean registroAbierto) {
        this.auth = auth;
        this.portalUrl = portalUrl;
        this.registroAbierto = registroAbierto;
    }

    /**
     * Cerrado por defecto (issue #174): antes de esto, el registro público solo estaba cerrado en el portal
     * (`/api/auth/registro` del BFF), así que cualquiera que llamara a este endpoint directo podía crear una cuenta
     * sin invitación. La beta cerrada no lo era. El código y el mensaje son los mismos que ya usa el portal para
     * que la experiencia no cambie según por dónde entre la petición.
     */
    @PostMapping("/registro")
    @Operation(summary = "Registrar una cuenta", description = "Crea la cuenta del portal y devuelve los tokens de sesión. `403 REGISTRO_CERRADO` si el autoservicio está cerrado (`app.registro-abierto`).")
    public ResponseEntity<ApiResponse<TokensResponse>> registrar(@Valid @RequestBody RegistroRequest body) {
        if (!registroAbierto) throw new DomainException("REGISTRO_CERRADO", "El registro está por invitación: escríbenos para pedir acceso.");
        var tokens = auth.registrar(body.nombre(), body.email(), body.password(), body.telefono());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(TokensResponse.de(tokens)));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Devuelve `access_token` (corta duración) y `refresh_token` (rotativo: cada refresh invalida el anterior).")
    public ApiResponse<TokensResponse> login(@Valid @RequestBody LoginRequest body) {
        return ApiResponse.ok(TokensResponse.de(auth.login(body.email(), body.password())));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar la sesión", description = "Entrega un nuevo par de tokens a cambio del refresh vigente; el usado queda invalidado (`401 SESION_INVALIDA` si se reutiliza).")
    public ApiResponse<TokensResponse> refrescar(@Valid @RequestBody RefreshRequest body) {
        return ApiResponse.ok(TokensResponse.de(auth.refrescar(body.refresh())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Invalida el refresh token.")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest body) {
        auth.logout(body.refresh());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario de la sesión", description = "Correo, rol y cuenta del usuario autenticado por JWT.")
    public ApiResponse<UsuarioResponse> me(HttpServletRequest req) {
        return ApiResponse.ok(UsuarioResponse.de(auth.me(UsuarioActual.id(req))));
    }

    /** Siempre 202, exista o no la cuenta: no revela si un correo está registrado. */
    @PostMapping("/recuperar")
    @Operation(summary = "Solicitar restablecimiento de contraseña", description = "Envía un enlace de un solo uso al correo si la cuenta existe; responde `202` siempre para no revelar cuentas.")
    public ResponseEntity<Void> recuperar(@Valid @RequestBody RecuperarRequest body) {
        auth.solicitarRecuperacion(body.email(), portalUrl);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/restablecer")
    @Operation(summary = "Restablecer la contraseña", description = "Fija una contraseña nueva con el token recibido por correo.")
    public ResponseEntity<Void> restablecer(@Valid @RequestBody RestablecerRequest body) {
        auth.restablecer(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
}
