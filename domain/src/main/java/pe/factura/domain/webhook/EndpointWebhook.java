package pe.factura.domain.webhook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un destino de webhooks de una empresa (#19). {@code activo} es falso cuando el integrador lo apagó o khipu lo pausó por entregas fallidas seguidas
 * ({@code pausadoMotivo} dice por qué; nulo si lo apagó el integrador). El secreto de firma no está aquí: solo se guarda cifrado y se muestra una vez al crearlo.
 */
public record EndpointWebhook(UUID id, UUID tenantId, String url, String descripcion, List<String> eventos, boolean activo, String pausadoMotivo, int fallosConsecutivos, Instant creadoEn) {
    public boolean suscritoA(String evento) { return eventos.contains(evento); }
}
