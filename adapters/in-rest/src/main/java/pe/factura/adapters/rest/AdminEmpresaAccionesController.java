package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.ApiKeyRevocadaResponse;
import pe.factura.adapters.rest.dto.CambiarEntornoRequest;
import pe.factura.adapters.rest.dto.CambioDeEntornoResponse;
import pe.factura.adapters.rest.dto.ResultadoDeConexionResponse;
import pe.factura.application.port.in.AccionesDeEmpresaUseCase;

import java.util.UUID;

/**
 * Acciones del administrador sobre una empresa (#187). Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador; un
 * JWT de cliente o una API key de tenant no pasan. Las tres quedan en la bitácora de auditoría, en la misma transacción que el cambio.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma", description = "Operaciones del operador de khipu con `X-Platform-Key` o de un administrador ya autenticado. No están disponibles para empresas ni integradores.")
@RequiredArgsConstructor
public class AdminEmpresaAccionesController {
    private final AccionesDeEmpresaUseCase acciones;

    @PostMapping("/empresas/{id}/entorno")
    @Operation(summary = "Cambiar el entorno de una empresa", description = """
            Cambia entre `BETA` y `PRODUCCION`. **Decide contra qué URLs de SUNAT emite la empresa** y con qué credenciales SOL y certificado se espera que
            lo haga: al pasar a producción hacen falta los de producción. **No toca ningún comprobante ya emitido**. Se rechaza con `409
            EMPRESA_CON_ENVIOS_PENDIENTES` si la empresa tiene tareas en el outbox, porque lo emitido en un entorno no debe enviarse al otro, y con `409
            ENTORNO_SIN_CAMBIOS` si ya estaba en ese entorno. Un entorno que no existe responde `400`; `404 NO_ENCONTRADO` si la empresa no existe.
            Queda en la bitácora (de cuál a cuál), también en la de su cuenta.""")
    public ApiResponse<CambioDeEntornoResponse> cambiarEntorno(@Parameter(description = "Id de la empresa") @PathVariable UUID id,
                                                               @RequestBody(required = false) CambiarEntornoRequest body, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(CambioDeEntornoResponse.de(acciones.cambiarEntorno(actor, id, body == null ? null : body.entorno())));
    }

    @PostMapping("/empresas/{id}/api-keys/{apiKeyId}/revocar")
    @Operation(summary = "Revocar una API key de una empresa", description = """
            Revoca esa key y solo esa: deja de autenticar de inmediato. Las demás keys de la empresa no se tocan. Queda en la bitácora con su prefijo, nunca la
            clave. `409 API_KEY_YA_REVOCADA` si ya lo estaba; `404 NO_ENCONTRADO` si la empresa o la key no existen, o la key es de otra empresa.""")
    public ApiResponse<ApiKeyRevocadaResponse> revocarApiKey(@Parameter(description = "Id de la empresa") @PathVariable UUID id,
                                                             @Parameter(description = "Id de la API key, dentro de esa empresa") @PathVariable UUID apiKeyId, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(ApiKeyRevocadaResponse.de(acciones.revocarApiKey(actor, id, apiKeyId)));
    }

    @PostMapping("/empresas/{id}/prueba-de-conexion")
    @Operation(summary = "Probar la conexión de una empresa con SUNAT", description = """
            Consulta a SUNAT, con las credenciales SOL y el entorno de la empresa, el estado de un ticket que no existe: es de solo lectura, no envía ni cambia
            nada. Responde cómo contestó SUNAT: `CONECTADO` (contestó con normalidad), `RECHAZADO` (contestó con un error definitivo; con un ticket que no
            existe un error de ticket es esperable) o `SIN_RESPUESTA` (red, tiempo de espera, error del servicio o de autenticación HTTP), con el código y el
            mensaje de SUNAT tal cual. No dice por sí sola si las credenciales son válidas: la lee quien la ejecuta. Queda en la bitácora con el resultado,
            nunca las credenciales. `409 SOL_NO_CARGADAS` si la empresa no tiene credenciales SOL; `404 NO_ENCONTRADO` si no existe.""")
    public ApiResponse<ResultadoDeConexionResponse> probarConexion(@Parameter(description = "Id de la empresa") @PathVariable UUID id, HttpServletRequest req) {
        var actor = AdministradorActual.actor(req);
        return ApiResponse.ok(ResultadoDeConexionResponse.de(acciones.probarConexion(actor, id)));
    }
}
