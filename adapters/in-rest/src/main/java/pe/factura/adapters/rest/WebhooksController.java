package pe.factura.adapters.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.factura.adapters.rest.dto.WebhookDtos;
import pe.factura.application.port.in.GestionarWebhooksUseCase;
import pe.factura.domain.webhook.EntregaWebhook;
import pe.factura.domain.webhook.EventoWebhook;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/webhooks")
@Tag(name = "Webhooks", description = """
        khipu avisa a su sistema, con un `POST` firmado, cada vez que un comprobante cambia de estado (aceptado, rechazado, error de envío…), para que no tenga que consultar
        `GET /v1/facturas/{id}`. Registre hasta 5 endpoints https por empresa. Cada entrega lleva `X-Khipu-Event`, `X-Khipu-Delivery` y `X-Khipu-Signature` (HMAC-SHA256 del
        cuerpo con el secreto del endpoint); responda 2xx para confirmarla. Una entrega que no se confirma se reintenta con espera creciente hasta 8 veces; tras 10 intentos
        fallidos seguidos khipu apaga el endpoint y avisa por correo. Crear, cambiar o borrar endpoints exige la sesión del portal (una API key filtrada no debe poder
        redirigir los eventos); consultarlos y ver el log de entregas funciona también con API key.""")
@RequiredArgsConstructor
public class WebhooksController {
    private final GestionarWebhooksUseCase webhooks;

    @GetMapping("/eventos")
    @Operation(summary = "Eventos disponibles", description = "Los eventos a los que puede suscribirse un endpoint.")
    public ApiResponse<List<String>> eventos() { return ApiResponse.ok(EventoWebhook.todos()); }

    @PostMapping
    @Operation(summary = "Registrar un webhook", description = """
            Crea un endpoint. **El secreto de firma se muestra una sola vez** en esta respuesta. La URL debe ser https y apuntar a un servidor público: una dirección interna
            responde `422 WEBHOOK_INVALIDO`. Con 5 endpoints ya creados responde `409 WEBHOOK_LIMITE`. Requiere sesión del portal.""")
    public ResponseEntity<ApiResponse<WebhookDtos.Creado>> crear(HttpServletRequest req, @Valid @RequestBody WebhookDtos.Crear body) {
        CuentaActual.exigirSesion(req);
        GestionarWebhooksUseCase.Creado c = webhooks.crear(TenantActual.id(req), body.url(), body.descripcion(), body.eventos());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(new WebhookDtos.Creado(WebhookDtos.Webhook.de(c.endpoint()), c.secreto())));
    }

    @GetMapping
    @Operation(summary = "Listar webhooks", description = "Los endpoints de la empresa, sin el secreto.")
    public ApiResponse<List<WebhookDtos.Webhook>> listar(HttpServletRequest req) {
        return ApiResponse.ok(webhooks.listar(TenantActual.id(req)).stream().map(WebhookDtos.Webhook::de).toList());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cambiar un webhook", description = """
            Cambia la URL, la descripción, los eventos o el estado. Con `activo: true` reactiva un endpoint apagado o pausado por khipu y pone en cero sus fallos; las
            entregas que quedaron pendientes se mandan enseguida. Requiere sesión del portal.""")
    public ApiResponse<WebhookDtos.Webhook> actualizar(HttpServletRequest req, @PathVariable UUID id, @Valid @RequestBody WebhookDtos.Actualizar body) {
        CuentaActual.exigirSesion(req);
        return ApiResponse.ok(WebhookDtos.Webhook.de(webhooks.actualizar(TenantActual.id(req), id, body.url(), body.descripcion(), body.eventos(), body.activo())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un webhook", description = "Borra el endpoint y su log de entregas. `404 NO_ENCONTRADO` si no es de la empresa. Requiere sesión del portal.")
    public ResponseEntity<Void> eliminar(HttpServletRequest req, @PathVariable UUID id) {
        CuentaActual.exigirSesion(req);
        webhooks.eliminar(TenantActual.id(req), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/secreto")
    @Operation(summary = "Rotar el secreto de firma", description = "Genera un secreto nuevo; el anterior deja de valer al instante. **Se muestra una sola vez.** Requiere sesión del portal.")
    public ApiResponse<WebhookDtos.Secreto> rotarSecreto(HttpServletRequest req, @PathVariable UUID id) {
        CuentaActual.exigirSesion(req);
        return ApiResponse.ok(new WebhookDtos.Secreto(webhooks.rotarSecreto(TenantActual.id(req), id)));
    }

    @PostMapping("/{id}/prueba")
    @Operation(summary = "Enviar un evento de prueba", description = "Encola un evento `webhook.prueba` firmado. Devuelve el id de la entrega, que se ve en el log. `409 WEBHOOK_PAUSADO` si el endpoint está apagado. Requiere sesión del portal.")
    public ResponseEntity<ApiResponse<WebhookDtos.Prueba>> probar(HttpServletRequest req, @PathVariable UUID id) {
        CuentaActual.exigirSesion(req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(new WebhookDtos.Prueba(webhooks.probar(TenantActual.id(req), id))));
    }

    @GetMapping("/{id}/entregas")
    @Operation(summary = "Log de entregas", description = "Las entregas del endpoint, de la más reciente a la más antigua, con su estado, intentos y el último código HTTP o error. El total va en `X-Total-Count`. Se guardan 30 días.")
    public ResponseEntity<ApiResponse<List<WebhookDtos.Entrega>>> entregas(HttpServletRequest req, @PathVariable UUID id,
                                                                            @Parameter(description = "PENDIENTE, EXITOSA o FALLIDA") @RequestParam(required = false) EntregaWebhook.Estado estado,
                                                                            @Parameter(description = "Página, desde 1") @RequestParam(defaultValue = "1") int pagina,
                                                                            @Parameter(description = "Resultados por página, 1–100") @RequestParam(name = "por_pagina", defaultValue = "20") int porPagina) {
        GestionarWebhooksUseCase.PaginaEntregas p = webhooks.entregas(TenantActual.id(req), id, estado, pagina, porPagina);
        return ResponseEntity.ok().header(FacturaController.TOTAL_HEADER, String.valueOf(p.total())).body(ApiResponse.ok(p.entregas().stream().map(WebhookDtos.Entrega::de).toList()));
    }

    @PostMapping("/entregas/{entregaId}/reenviar")
    @Operation(summary = "Reenviar una entrega", description = "Vuelve a mandar una entrega ya cerrada (exitosa o fallida) con el mismo cuerpo. `409 ENTREGA_NO_REENVIABLE` si sigue pendiente y `409 WEBHOOK_PAUSADO` si el endpoint está apagado. Requiere sesión del portal.")
    public ResponseEntity<Void> reenviar(HttpServletRequest req, @PathVariable UUID entregaId) {
        CuentaActual.exigirSesion(req);
        webhooks.reenviar(TenantActual.id(req), entregaId);
        return ResponseEntity.accepted().build();
    }
}
