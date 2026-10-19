package pe.factura.adapters.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import pe.factura.domain.webhook.EndpointWebhook;
import pe.factura.domain.webhook.EntregaWebhook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedidos y respuestas de los webhooks (#19). */
public final class WebhookDtos {
    private WebhookDtos() {}

    public record Crear(
            @NotBlank @Size(max = 500) @Schema(example = "https://mi-sistema.pe/khipu/webhook", description = "URL https que recibe los eventos") String url,
            @Size(max = 100) @Schema(example = "ERP producción") String descripcion,
            @NotEmpty @Schema(example = "[\"comprobante.aceptado\", \"comprobante.rechazado\"]", description = "Eventos a los que se suscribe, o `[\"*\"]` para todos (`GET /v1/webhooks/eventos`)") List<String> eventos) {}

    public record Actualizar(
            @Size(max = 500) @Schema(description = "Nueva URL https; omitida, se deja la actual") String url,
            @Size(max = 100) @Schema(description = "Nueva descripción; vacía la borra") String descripcion,
            @Schema(description = "Nuevos eventos; omitidos, se dejan los actuales") List<String> eventos,
            @Schema(description = "`false` lo apaga; `true` lo reactiva (también uno que khipu pausó) con los fallos en cero") Boolean activo) {}

    public record Webhook(
            UUID id,
            @Schema(example = "https://mi-sistema.pe/khipu/webhook") String url,
            @Schema(example = "ERP producción") String descripcion,
            List<String> eventos,
            @Schema(description = "Falso si lo apagó usted o khipu lo pausó por entregas fallidas seguidas") boolean activo,
            @Schema(description = "Por qué khipu lo pausó; nulo si está activo o lo apagó usted") String pausadoMotivo,
            @Schema(description = "Intentos fallidos seguidos; a los 10 khipu lo pausa") int fallosConsecutivos,
            Instant creadoEn) {
        public static Webhook de(EndpointWebhook e) {
            return new Webhook(e.id(), e.url(), e.descripcion(), e.eventos(), e.activo(), e.pausadoMotivo(), e.fallosConsecutivos(), e.creadoEn());
        }
    }

    /** La respuesta al crear: el endpoint y su secreto de firma, que no se vuelve a mostrar. */
    public record Creado(Webhook webhook, @Schema(example = "whsec_Zm9vYmFy…", description = "Secreto para verificar la firma `X-Khipu-Signature`. Se muestra una sola vez") String secreto) {}

    public record Secreto(@Schema(description = "El nuevo secreto; el anterior dejó de valer. Se muestra una sola vez") String secreto) {}

    public record Prueba(@Schema(description = "Id de la entrega de prueba; aparece en el log de entregas") UUID entregaId) {}

    public record Entrega(
            UUID id,
            @Schema(example = "comprobante.aceptado") String evento,
            @Schema(description = "El comprobante del evento; nulo en la entrega de prueba") UUID comprobanteId,
            @Schema(description = "PENDIENTE, EXITOSA o FALLIDA") String estado,
            int intentos,
            @Schema(description = "Cuándo se reintenta; solo si está pendiente") Instant siguienteIntento,
            @Schema(description = "Código HTTP del último intento; nulo si no hubo respuesta") Integer ultimoHttp,
            String ultimoError,
            Instant ultimoIntentoEn,
            Instant entregadaEn,
            Instant creadaEn) {
        public static Entrega de(EntregaWebhook e) {
            return new Entrega(e.id(), e.evento(), e.documentoId(), e.estado().name(), e.intentos(), e.estado() == EntregaWebhook.Estado.PENDIENTE ? e.siguienteIntento() : null,
                    e.ultimoHttp(), e.ultimoError(), e.ultimoIntentoEn(), e.entregadaEn(), e.creadoEn());
        }
    }
}
