package pe.factura.domain.webhook;

import java.time.Instant;
import java.util.UUID;

/**
 * Una entrega de un evento a un endpoint (#19), con el cuerpo exacto que se firma y se manda. {@code PENDIENTE}: falta enviarla o reintentarla; {@code EXITOSA}: el
 * endpoint respondió 2xx; {@code FALLIDA}: se agotaron los intentos (se puede reenviar a mano).
 */
public record EntregaWebhook(UUID id, UUID endpointId, UUID tenantId, String evento, UUID documentoId, String payload, Estado estado, int intentos, Instant siguienteIntento,
                             Integer ultimoHttp, String ultimoError, Instant ultimoIntentoEn, Instant entregadaEn, Instant creadoEn) {
    public enum Estado { PENDIENTE, EXITOSA, FALLIDA }
}
