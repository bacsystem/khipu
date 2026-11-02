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
    /**
     * Una entrega lista para enviar, con lo que hace falta para firmarla y mandarla. {@code bloqueo} es la marca con que se tomó: las escrituras del resultado solo valen si
     * la entrega sigue tomada con esa marca, así que una instancia que tardó más que el bloqueo no pisa a la que la volvió a tomar.
     */
    record Pendiente(EntregaWebhook entrega, String url, byte[] secretoCifrado, Instant bloqueo) {}

    /** Crea el endpoint si la empresa tiene menos de {@code maximo}; {@code false} si ya está en el tope. Serializa las altas de una misma empresa. */
    boolean crearSiHayCupo(EndpointWebhook endpoint, byte[] secretoCifrado, int maximo);

    Optional<EndpointWebhook> buscar(UUID tenantId, UUID id);

    /** Los endpoints de la empresa, del más antiguo al más nuevo. */
    List<EndpointWebhook> listar(UUID tenantId);

    /** Cambia solo la URL, la descripción y los eventos: el estado de activación lo escribe {@link #fijarActivo}, para no pisar una pausa que ocurra en el medio. */
    void actualizarDatos(UUID tenantId, UUID id, String url, String descripcion, List<String> eventos);

    /**
     * Enciende o apaga el endpoint. Al encenderlo quita el motivo de pausa, pone en cero los fallos seguidos y deja las entregas pendientes listas para salir ya; al apagarlo a
     * mano también quita el motivo (lo apagó la persona, no khipu).
     */
    void fijarActivo(UUID tenantId, UUID id, boolean activo);

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
     * Toma las entregas pendientes a las que ya les toca, de endpoints activos, y las bloquea por {@code bloqueo} para que otra instancia no las tome. Como mucho
     * {@code maximoPorEndpoint} de cada endpoint (un servidor lento no acapara el lote) y {@code limite} en total. Debe llamarse dentro de una transacción.
     */
    List<Pendiente> tomarVencidas(int limite, int maximoPorEndpoint, Duration bloqueo);

    /** Si el endpoint sigue encendido (se vuelve a mirar antes de cada envío de un lote: pudo apagarse o pausarse mientras tanto). */
    boolean estaActivo(UUID endpointId);

    /** Marca la entrega EXITOSA y pone en cero los fallos seguidos de su endpoint. {@code false} si la entrega ya no estaba tomada con esa marca. */
    boolean registrarExito(Pendiente tomada, int http, Instant ahora);

    /**
     * Anota un intento fallido. Con {@code siguiente} la entrega queda PENDIENTE para esa hora; sin él (intentos agotados) queda FALLIDA. Suma uno a los fallos seguidos del
     * endpoint y devuelve el nuevo total, o {@code -1} si la entrega ya no estaba tomada con esa marca (no se anota nada).
     */
    int registrarFallo(Pendiente tomada, Integer http, String error, Instant ahora, Instant siguiente);

    /** Suelta una entrega tomada que no se llegó a enviar (el endpoint se apagó en el medio). */
    void liberar(Pendiente tomada);

    /** Apaga el endpoint por entregas fallidas seguidas, con el motivo. {@code false} si ya estaba apagado: no se pisa el motivo ni se avisa dos veces. */
    boolean pausar(UUID endpointId, String motivo);

    /**
     * Borra las entregas cerradas (EXITOSA o FALLIDA) creadas antes de {@code antesDe}, y también las pendientes de esa antigüedad de un endpoint apagado: un endpoint que
     * nunca se reactiva no puede acumular entregas para siempre.
     */
    long limpiar(Instant antesDe);
}
