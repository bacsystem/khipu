package pe.factura.application.port.out;

import pe.factura.domain.webhook.EndpointWebhook;
import pe.factura.domain.webhook.EntregaWebhook;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Los endpoints de webhooks de cada empresa y sus entregas (#19). Las entregas de los cambios de estado de un comprobante se encolan en la misma transacción que el cambio
 * (en {@code ComprobanteRepository.guardar}), no por este puerto: este es para gestionarlas y enviarlas.
 */
public interface WebhookRepository {
    /** Una entrega lista para enviar, con lo que hace falta para firmarla y mandarla. */
    record Pendiente(EntregaWebhook entrega, String url, byte[] secretoCifrado) {}

    void crear(EndpointWebhook endpoint, byte[] secretoCifrado);

    Optional<EndpointWebhook> buscar(UUID tenantId, UUID id);

    /** Los endpoints de la empresa, del más antiguo al más nuevo. */
    List<EndpointWebhook> listar(UUID tenantId);

    /** Cambia la URL, la descripción, los eventos y el estado de activación (con su motivo de pausa y su contador de fallos). */
    void actualizar(EndpointWebhook endpoint);

    void guardarSecreto(UUID tenantId, UUID id, byte[] secretoCifrado);

    /** El secreto cifrado del endpoint. */
    Optional<byte[]> secreto(UUID tenantId, UUID id);

    /** Borra el endpoint y sus entregas; {@code false} si no existía en esa empresa. */
    boolean eliminar(UUID tenantId, UUID id);

    /** Encola una entrega ya armada (la de prueba, o el reenvío de otra). Devuelve su id. */
    UUID encolar(UUID tenantId, UUID endpointId, String evento, UUID documentoId, String payload);

    List<EntregaWebhook> entregas(UUID tenantId, UUID endpointId, EntregaWebhook.Estado estado, int pagina, int porPagina);

    long contarEntregas(UUID tenantId, UUID endpointId, EntregaWebhook.Estado estado);

    Optional<EntregaWebhook> buscarEntrega(UUID tenantId, UUID id);

    /** Vuelve la entrega a PENDIENTE para enviarse ya, con los intentos en cero. */
    void reencolar(UUID tenantId, UUID id);

    /**
     * Toma las entregas pendientes a las que ya les toca, de endpoints activos, y las bloquea por {@code bloqueo} para que otra instancia no las tome. Debe llamarse dentro de una
     * transacción.
     */
    List<Pendiente> tomarVencidas(int limite, Duration bloqueo);

    /** Marca la entrega EXITOSA y pone en cero los fallos consecutivos de su endpoint. */
    void registrarExito(UUID entregaId, UUID endpointId, int http, Instant ahora);

    /**
     * Anota un intento fallido. Con {@code siguiente} la entrega queda PENDIENTE para esa hora; sin él (intentos agotados) queda FALLIDA. Suma uno a los fallos consecutivos del
     * endpoint y devuelve el nuevo total.
     */
    int registrarFallo(UUID entregaId, UUID endpointId, Integer http, String error, Instant ahora, Instant siguiente);

    /** Apaga el endpoint por entregas fallidas seguidas, con el motivo. */
    void pausar(UUID endpointId, String motivo);

    /** Borra las entregas ya cerradas (EXITOSA o FALLIDA) creadas antes de {@code antesDe}. */
    long limpiar(Instant antesDe);
}
