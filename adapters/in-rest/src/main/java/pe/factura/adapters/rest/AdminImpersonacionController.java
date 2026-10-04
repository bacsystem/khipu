package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ImpersonacionResponse;
import pe.factura.application.port.in.ImpersonarUsuarioUseCase;

import java.util.UUID;

/**
 * Impersonar a un usuario de un cliente desde el backoffice (#184): la función más sensible, y la más acotada. Autenticado por {@link AdminAuthFilter}, pero **solo
 * la sesión de un administrador** sirve: la clave de plataforma no identifica a nadie y la bitácora no podría decir quién fue.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminImpersonacionController {
    private final ImpersonarUsuarioUseCase impersonar;

    @PostMapping("/cuentas/{cuentaId}/usuarios/{usuarioId}/impersonar")
    @Operation(summary = "Entrar al portal como un usuario del cliente", description = """
            Abre una **sesión de soporte**: un JWT del usuario elegido, de **solo lectura** (cualquier escritura, incluida cambiar la contraseña, las credenciales SOL,
            las API keys o emitir, responde `403 SOPORTE_SOLO_LECTURA`), que vence a los 15 minutos y **no se puede renovar** (no hay refresh). Queda en la bitácora
            quién impersonó a quién, cuándo y por cuánto tiempo, en la misma transacción: si no se puede dejar constancia, el token no sale. El cliente ve el acceso en
            su propio historial (sin el nombre del administrador). Solo sirve la sesión de un administrador: con la clave de plataforma, `403 REQUIERE_ADMINISTRADOR`.
            `404 NO_ENCONTRADO` si el usuario no existe en esa cuenta; `409 USUARIO_INACTIVO` si está desactivado. La respuesta no se guarda en ninguna caché.""")
    public ResponseEntity<ApiResponse<ImpersonacionResponse>> impersonar(@Parameter(description = "Id de la cuenta") @PathVariable UUID cuentaId,
                                                                         @Parameter(description = "Id del usuario, dentro de esa cuenta") @PathVariable UUID usuarioId, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(ImpersonacionResponse.de(impersonar.impersonar(actor, cuentaId, usuarioId))));
    }
}
