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
import pe.factura.application.port.in.AceptarTerminosUseCase;
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
    private final AceptarTerminosUseCase terminos;
    private final String portalUrl;
    private final boolean registroAbierto;
    private final IpDelCliente ip;

    public AuthController(AutenticarUsuarioUseCase auth, AceptarTerminosUseCase terminos, @Value("${app.portal-url}") String portalUrl,
                           @Value("${app.registro-abierto:false}") boolean registroAbierto, IpDelCliente ip) {
        this.auth = auth;
        this.terminos = terminos;
        this.portalUrl = portalUrl;
        this.registroAbierto = registroAbierto;
        this.ip = ip;
    }

    /**
     * Cerrado por defecto (issue #174): antes de esto, el registro público solo estaba cerrado en el portal
     * (`/api/auth/registro` del BFF), así que cualquiera que llamara a este endpoint directo podía crear una cuenta
     * sin invitación. La beta cerrada no lo era. El código y el mensaje son los mismos que ya usa el portal para
     * que la experiencia no cambie según por dónde entre la petición.
     */
    @PostMapping("/registro")
    @Operation(summary = "Registrar una cuenta", description = """
            Crea la cuenta del portal y devuelve los tokens de sesión. El correo queda sin verificar y se le manda un enlace (24 h): hasta
            verificarlo puede entrar al portal, pero cualquier escritura con su sesión responde `403 CORREO_SIN_VERIFICAR`.
            `403 REGISTRO_CERRADO` si el autoservicio está cerrado (`app.registro-abierto`). Exige `acepta_terminos: true` (#342): sin eso,
            `400 TERMINOS_NO_ACEPTADOS` y no se crea nada; con eso queda guardada la versión vigente de los términos y la fecha.""")
    public ResponseEntity<ApiResponse<TokensResponse>> registrar(@Valid @RequestBody RegistroRequest body) {
        if (!registroAbierto) throw new DomainException("REGISTRO_CERRADO", "El registro está por invitación: escríbenos para pedir acceso.");
        var tokens = auth.registrar(body.nombre(), body.email(), body.password(), body.telefono(), Boolean.TRUE.equals(body.aceptaTerminos()), portalUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(TokensResponse.de(tokens)));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = """
            Devuelve `access_token` (corta duración) y `refresh_token` (rotativo: cada refresh invalida el anterior). Tras 5 contraseñas
            erróneas en 15 minutos para ese correo (o 20 fallos desde la misma IP), `429 DEMASIADOS_INTENTOS_LOGIN` durante 15 minutos,
            aun con la contraseña correcta.""")
    public ApiResponse<TokensResponse> login(HttpServletRequest req, @Valid @RequestBody LoginRequest body) {
        return ApiResponse.ok(TokensResponse.de(auth.login(body.email(), body.password(), ip.de(req))));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar la sesión", description = "Entrega un nuevo par de tokens a cambio del refresh vigente; el usado queda invalidado. Durante 30 segundos todavía se acepta, para una petición concurrente que mandó el mismo refresh; después, `401 SESION_INVALIDA`. Cerrar sesión lo invalida al instante.")
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
    @Operation(summary = "Usuario de la sesión", description = "Correo, rol y cuenta del usuario autenticado por JWT, y si le falta aceptar los términos vigentes.")
    public ApiResponse<UsuarioResponse> me(HttpServletRequest req) {
        var usuario = auth.me(UsuarioActual.id(req));
        return ApiResponse.ok(UsuarioResponse.de(usuario, terminos.pendiente(usuario.id()), SoporteActual.de(req).orElse(null)));
    }

    /**
     * Con la sesión del propio usuario (#342). Una sesión de soporte no llega acá: el filtro le niega toda escritura, y aceptar en nombre del
     * cliente es justo lo que no puede hacer quien lo está mirando.
     */
    @PostMapping("/terminos")
    @Operation(summary = "Aceptar los términos vigentes", description = """
            Registra que el usuario de la sesión acepta la versión vigente de los términos del servicio y la política de privacidad, con la
            fecha. Si ya la había aceptado responde igual, con la fecha de entonces. Se usa cuando la versión cambió (`terminos_pendientes`
            en `/me`, el login o el refresh); al registrarse, la aceptación va en el propio registro. Una sesión de soporte recibe
            `403 SOPORTE_SOLO_LECTURA`.""")
    public ApiResponse<AceptacionDeTerminosResponse> aceptarTerminos(HttpServletRequest req) {
        return ApiResponse.ok(AceptacionDeTerminosResponse.de(terminos.aceptar(UsuarioActual.id(req))));
    }

    /** Siempre 202, exista o no la cuenta: no revela si un correo está registrado. */
    @PostMapping("/recuperar")
    @Operation(summary = "Solicitar restablecimiento de contraseña", description = """
            Envía un enlace de un solo uso al correo si la cuenta existe; responde `202` siempre para no revelar cuentas. Como mucho
            3 correos por dirección por hora: pasado eso responde igual, pero no envía nada.""")
    public ResponseEntity<Void> recuperar(@Valid @RequestBody RecuperarRequest body) {
        auth.solicitarRecuperacion(body.email(), portalUrl);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/restablecer")
    @Operation(summary = "Restablecer la contraseña", description = "Fija una contraseña nueva con el token recibido por correo. También verifica el correo: el enlace llegó ahí.")
    public ResponseEntity<Void> restablecer(@Valid @RequestBody RestablecerRequest body) {
        auth.restablecer(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verificar")
    @Operation(summary = "Verificar el correo", description = "Con el token del enlace que llegó al correo (24 h, un solo uso). `422 TOKEN_INVALIDO` si venció, ya se usó o no existe.")
    public ResponseEntity<Void> verificar(@Valid @RequestBody VerificarRequest body) {
        auth.verificarCorreo(body.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verificacion")
    @Operation(summary = "Reenviar el enlace de verificación", description = """
            Al correo del usuario de la sesión. `409 CORREO_YA_VERIFICADO` si no hace falta. Hasta 5 enlaces cada 24 horas contando el del
            registro (el correo todavía no está verificado y podría no ser suyo): pasado eso, `429 DEMASIADOS_ENLACES`.""")
    public ResponseEntity<Void> reenviarVerificacion(HttpServletRequest req) {
        auth.reenviarVerificacion(UsuarioActual.id(req), portalUrl);
        return ResponseEntity.accepted().build();
    }
}
