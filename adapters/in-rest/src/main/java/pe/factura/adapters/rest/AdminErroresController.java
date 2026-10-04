package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.factura.adapters.rest.dto.DescarteResponse;
import pe.factura.adapters.rest.dto.DescartarComprobanteRequest;
import pe.factura.adapters.rest.dto.ErrorDeEmisionResponse;
import pe.factura.adapters.rest.dto.ReintentoResponse;
import pe.factura.application.port.in.ConsultarColaDeErroresUseCase;
import pe.factura.application.port.in.ResolverErroresUseCase;
import pe.factura.domain.documento.ClaseDeError;

import java.util.List;
import java.util.UUID;

/**
 * La cola global de errores del backoffice (#196): los comprobantes con problema de todas las empresas en una sola lista, y las dos acciones que un administrador puede hacer
 * sobre uno en error de envío. Autenticado por {@link AdminAuthFilter}: la clave de plataforma o el JWT de un administrador.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Administración de la plataforma")
@RequiredArgsConstructor
public class AdminErroresController {
    private final ConsultarColaDeErroresUseCase cola;
    private final ResolverErroresUseCase resolver;

    @GetMapping("/errores")
    @Operation(summary = "La cola de errores de todas las empresas", description = """
            Los comprobantes con problema de **todas** las empresas, de la emisión más antigua a la más reciente (la más urgente primero), paginados; el total va en la cabecera
            `X-Total-Count`. Tres clases: `ERROR_DE_ENVIO` (SUNAT no contestó o falló: se reintenta solo, y un administrador puede reintentar a mano o descartar),
            `ERROR_DE_FORMATO` (SUNAT lo rechazó con un fault 1000–1999: terminal, no se reintenta) y `FUERA_DE_PLAZO` (terminal: hay que emitir de nuevo). Cada fila trae el fault
            de SUNAT, los intentos y, si hay un reintento programado, cuándo. `q` busca el cliente por RUC (prefijo), razón social, o nombre o correo de su cuenta, sin distinguir
            mayúsculas ni tildes. Solo lectura.""")
    public ResponseEntity<ApiResponse<List<ErrorDeEmisionResponse>>> listar(
            @Parameter(description = "Solo esta clase de error") @RequestParam(required = false) ClaseDeError clase,
            @Parameter(description = "Solo esta empresa") @RequestParam(name = "empresa_id", required = false) UUID empresaId,
            @Parameter(description = "Cliente: RUC, razón social, o nombre o correo de la cuenta", example = "andina") @RequestParam(required = false) String q,
            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        var p = cola.listar(new ConsultarColaDeErroresUseCase.Filtro(clase, empresaId, q), Math.max(1, pagina), Math.min(100, Math.max(1, porPagina)));
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(p.total())).body(ApiResponse.ok(p.errores().stream().map(ErrorDeEmisionResponse::de).toList()));
    }

    @PostMapping("/comprobantes/{id}/reintento")
    @Operation(summary = "Reintentar el envío de un comprobante", description = """
            Reintenta ahora el envío a SUNAT de un comprobante en error de envío, con las mismas reglas que el envío manual de la empresa. Que SUNAT vuelva a fallar **no** es un error:
            responde `200` con el estado en que quedó (`ERROR_ENVIO` y el fault, o el resultado de SUNAT). `409 ESTADO_NO_ENVIABLE` si ya no está por enviar, `409 FUERA_DE_PLAZO`
            si se pasó el plazo (y queda en ese estado), `404 NO_ENCONTRADO` si no existe. Queda en la bitácora, también cuando no se pudo reintentar.""")
    public ApiResponse<ReintentoResponse> reintentar(@Parameter(description = "Id del comprobante") @PathVariable UUID id, HttpServletRequest req) {
        return ApiResponse.ok(ReintentoResponse.de(resolver.reintentar(AdministradorActual.actor(req), id)));
    }

    @PostMapping("/comprobantes/{id}/descarte")
    @Operation(summary = "Descartar un comprobante en error de envío", description = """
            Deja de intentar un envío que falla: el comprobante pasa a `DESCARTADO` (terminal; hay que emitir de nuevo) y se saca del outbox. Solo desde error de envío
            (`409 ESTADO_NO_DESCARTABLE`; si cambió de estado en el medio, `409 ESTADO_CONFLICTO`). El `motivo` es obligatorio (`422 MOTIVO_REQUERIDO`) y de hasta 200 caracteres
            (`422 MOTIVO_LARGO`). `404 NO_ENCONTRADO` si no existe. Queda en la bitácora, en la misma transacción, con el motivo.""")
    public ApiResponse<DescarteResponse> descartar(@Parameter(description = "Id del comprobante") @PathVariable UUID id, @RequestBody(required = false) DescartarComprobanteRequest body,
                                                   HttpServletRequest req) {
        return ApiResponse.ok(DescarteResponse.de(resolver.descartar(AdministradorActual.actor(req), id, body == null ? null : body.motivo())));
    }
}
