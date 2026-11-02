package pe.factura.application.port.in;

import pe.factura.domain.webhook.EndpointWebhook;
import pe.factura.domain.webhook.EntregaWebhook;

import java.util.List;
import java.util.UUID;

/**
 * Los webhooks de una empresa (#19): registrar a dónde y por qué eventos khipu avisa, consultar el log de entregas, probar el endpoint y reenviar lo que falló. El secreto
 * de firma se devuelve **una sola vez**, al crear el endpoint o al rotarlo.
 */
public interface GestionarWebhooksUseCase {
    /** Lo máximo de endpoints por empresa. */
    int MAXIMO_POR_EMPRESA = 5;

    record Creado(EndpointWebhook endpoint, String secreto) {}

    record PaginaEntregas(List<EntregaWebhook> entregas, long total) {}

    Creado crear(UUID tenantId, String url, String descripcion, List<String> eventos);

    List<EndpointWebhook> listar(UUID tenantId);

    /** Cada argumento nulo se deja como está. Activar un endpoint pausado lo reactiva con los fallos en cero. */
    EndpointWebhook actualizar(UUID tenantId, UUID id, String url, String descripcion, List<String> eventos, Boolean activo);

    /** Cambia el secreto; el anterior deja de valer ya. Devuelve el nuevo, que no se vuelve a mostrar. */
    String rotarSecreto(UUID tenantId, UUID id);

    void eliminar(UUID tenantId, UUID id);

    /** {@code estado} nulo: todas. Más recientes primero; páginas desde 1. */
    PaginaEntregas entregas(UUID tenantId, UUID endpointId, EntregaWebhook.Estado estado, int pagina, int porPagina);

    /** Encola un evento {@code webhook.prueba} al endpoint y devuelve el id de la entrega, que se ve en el log. */
    UUID probar(UUID tenantId, UUID id);

    /** Vuelve a mandar una entrega ya cerrada (exitosa o fallida). */
    void reenviar(UUID tenantId, UUID entregaId);
}
