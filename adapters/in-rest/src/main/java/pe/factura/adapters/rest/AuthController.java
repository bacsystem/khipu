package pe.factura.adapters.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.*;
import pe.factura.application.port.in.AutenticarUsuarioUseCase;

/** Endpoints de autenticación del portal (JWT). Los de registro/login/refresh/recuperar/restablecer son públicos. */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {
    private final AutenticarUsuarioUseCase auth;
    private final String portalUrl;

    public AuthController(AutenticarUsuarioUseCase auth, @Value("${app.portal-url}") String portalUrl) {
        this.auth = auth;
        this.portalUrl = portalUrl;
    }

    @PostMapping("/registro")
    public ResponseEntity<ApiResponse<TokensResponse>> registrar(@Valid @RequestBody RegistroRequest body) {
        var tokens = auth.registrar(body.nombre(), body.email(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(TokensResponse.de(tokens)));
    }

    @PostMapping("/login")
    public ApiResponse<TokensResponse> login(@Valid @RequestBody LoginRequest body) {
        return ApiResponse.ok(TokensResponse.de(auth.login(body.email(), body.password())));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokensResponse> refrescar(@Valid @RequestBody RefreshRequest body) {
        return ApiResponse.ok(TokensResponse.de(auth.refrescar(body.refresh())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest body) {
        auth.logout(body.refresh());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ApiResponse<UsuarioResponse> me(HttpServletRequest req) {
        return ApiResponse.ok(UsuarioResponse.de(auth.me(UsuarioActual.id(req))));
    }

    /** Siempre 202, exista o no la cuenta: no revela si un correo está registrado. */
    @PostMapping("/recuperar")
    public ResponseEntity<Void> recuperar(@Valid @RequestBody RecuperarRequest body) {
        auth.solicitarRecuperacion(body.email(), portalUrl);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/restablecer")
    public ResponseEntity<Void> restablecer(@Valid @RequestBody RestablecerRequest body) {
        auth.restablecer(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
}
