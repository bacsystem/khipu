package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.DestinatarioResponse;
import pe.factura.application.port.in.SoporteDeAccesoUseCase;

import java.util.UUID;

/**
 * Soporte de acceso de los usuarios de un cliente desde el backoffice (#183): mandarles el correo para restablecer su contraseña o reenviarles
 * el de verificación. Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador. Nunca se muestra ni se
 * fija una contraseña: el administrador dispara el correo y el usuario elige la suya con el enlace.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
public class AdminUsuarioController {
    private final SoporteDeAccesoUseCase soporte;
    private final String portalUrl;

    public AdminUsuarioController(SoporteDeAccesoUseCase soporte, @Value("${app.portal-url}") String portalUrl) {
        this.soporte = soporte;
        this.portalUrl = portalUrl;
    }

    @PostMapping("/cuentas/{cuentaId}/usuarios/{usuarioId}/restablecimiento")
    @Operation(summary = "Mandar a un usuario el correo para restablecer su contraseña", description = """
            Crea un enlace de un solo uso (vale 1 hora) y se lo manda al correo del usuario; es él quien elige su contraseña, el administrador
            nunca la ve ni la fija, y **ni el enlace ni su token salen en la respuesta**. Vale también con el correo ya verificado. Queda en la
            bitácora de auditoría. `404 NO_ENCONTRADO` si el usuario no existe **en esa cuenta**; `409 USUARIO_INACTIVO` si está desactivado;
            `503 CORREO_NO_CONFIGURADO` si el servidor no entrega correos (no se crea nada); `502 CORREO_NO_ENVIADO` si el servidor de correo lo
            rechaza (el intento queda en la bitácora).""")
    public ApiResponse<DestinatarioResponse> restablecer(
            @Parameter(description = "Id de la cuenta") @PathVariable UUID cuentaId,
            @Parameter(description = "Id del usuario, dentro de esa cuenta") @PathVariable UUID usuarioId, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(DestinatarioResponse.de(soporte.enviarRestablecimiento(actor, cuentaId, usuarioId, portalUrl)));
    }

    @PostMapping("/cuentas/{cuentaId}/usuarios/{usuarioId}/verificacion")
    @Operation(summary = "Reenviar a un usuario el correo para verificar su dirección", description = """
            Crea un enlace nuevo de un solo uso (vale 24 horas) y se lo manda al usuario. Queda en la bitácora de auditoría. `409 CORREO_YA_VERIFICADO` si
            ya verificó su correo: no hay nada que reenviar. Los demás errores, como al mandar el correo de restablecimiento.""")
    public ApiResponse<DestinatarioResponse> reenviarVerificacion(
            @Parameter(description = "Id de la cuenta") @PathVariable UUID cuentaId,
            @Parameter(description = "Id del usuario, dentro de esa cuenta") @PathVariable UUID usuarioId, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(DestinatarioResponse.de(soporte.reenviarVerificacion(actor, cuentaId, usuarioId, portalUrl)));
    }
}
