package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.GestionarWebhooksUseCase;
import pe.factura.application.port.out.DestinoDeWebhook;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.port.out.WebhookRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.webhook.EndpointWebhook;
import pe.factura.domain.webhook.EntregaWebhook;
import pe.factura.domain.webhook.EventoWebhook;
import pe.factura.domain.webhook.UrlDeWebhook;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class GestionarWebhooksService implements GestionarWebhooksUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DESCRIPCION_MAXIMA = 100;

    private final WebhookRepository webhooks;
    private final DestinoDeWebhook destino;
    private final SecretCipher cifrador;
    private final UnitOfWork uow;
    private final Clock clock;
    /** Acepta {@code http://} (solo para desarrollo y pruebas; en producción las URL son https). */
    private final boolean permitirHttp;

    @Override public Creado crear(UUID tenantId, String url, String descripcion, List<String> eventos) {
        String limpia = UrlDeWebhook.validar(url, permitirHttp);
        List<String> suscritos = EventoWebhook.normalizar(eventos);
        String texto = descripcionValida(descripcion);
        destino.exigirDestinoPublico(limpia);
        String secreto = generarSecreto();
        EndpointWebhook e = new EndpointWebhook(UUID.randomUUID(), tenantId, limpia, texto, suscritos, true, null, 0, clock.instant());
        uow.ejecutar(() -> {
            if (webhooks.listar(tenantId).size() >= MAXIMO_POR_EMPRESA)
                throw new DomainException("WEBHOOK_LIMITE", "Una empresa puede tener hasta " + MAXIMO_POR_EMPRESA + " webhooks; elimine uno para crear otro");
            webhooks.crear(e, cifrador.cifrar(secreto.getBytes(StandardCharsets.UTF_8)));
        });
        return new Creado(e, secreto);
    }

    @Override public List<EndpointWebhook> listar(UUID tenantId) { return webhooks.listar(tenantId); }

    @Override public EndpointWebhook actualizar(UUID tenantId, UUID id, String url, String descripcion, List<String> eventos, Boolean activo) {
        EndpointWebhook actual = obtener(tenantId, id);
        String nuevaUrl = url == null ? actual.url() : UrlDeWebhook.validar(url, permitirHttp);
        if (url != null && !nuevaUrl.equals(actual.url())) destino.exigirDestinoPublico(nuevaUrl);
        boolean reactiva = Boolean.TRUE.equals(activo) && !actual.activo();
        // Activarlo (también desde una pausa por fallos) arranca limpio; apagarlo a mano borra el motivo de pausa: lo apagó el integrador, no khipu.
        boolean nuevoActivo = activo == null ? actual.activo() : activo;
        EndpointWebhook nuevo = new EndpointWebhook(actual.id(), tenantId, nuevaUrl, descripcion == null ? actual.descripcion() : descripcionValida(descripcion),
                eventos == null ? actual.eventos() : EventoWebhook.normalizar(eventos), nuevoActivo,
                activo == null ? actual.pausadoMotivo() : null, reactiva ? 0 : actual.fallosConsecutivos(), actual.creadoEn());
        uow.ejecutar(() -> webhooks.actualizar(nuevo));
        return nuevo;
    }

    @Override public String rotarSecreto(UUID tenantId, UUID id) {
        obtener(tenantId, id);
        String secreto = generarSecreto();
        uow.ejecutar(() -> webhooks.guardarSecreto(tenantId, id, cifrador.cifrar(secreto.getBytes(StandardCharsets.UTF_8))));
        return secreto;
    }

    @Override public void eliminar(UUID tenantId, UUID id) {
        if (!uow.ejecutar(() -> webhooks.eliminar(tenantId, id))) throw noEncontrado();
    }

    @Override public PaginaEntregas entregas(UUID tenantId, UUID endpointId, EntregaWebhook.Estado estado, int pagina, int porPagina) {
        obtener(tenantId, endpointId);
        if (pagina < 1 || porPagina < 1 || porPagina > 100) throw new DomainException("PAGINACION_INVALIDA", "pagina debe ser 1 o más y por_pagina de 1 a 100");
        return new PaginaEntregas(webhooks.entregas(tenantId, endpointId, estado, pagina, porPagina), webhooks.contarEntregas(tenantId, endpointId, estado));
    }

    @Override public UUID probar(UUID tenantId, UUID id) {
        exigirActivo(obtener(tenantId, id));
        String payload = "{\"evento\":\"" + EventoWebhook.PRUEBA + "\",\"creado_en\":\"" + clock.instant() + "\",\"datos\":{\"mensaje\":\"Entrega de prueba de khipu\"}}";
        return uow.ejecutar(() -> webhooks.encolar(tenantId, id, EventoWebhook.PRUEBA, null, payload));
    }

    @Override public void reenviar(UUID tenantId, UUID entregaId) {
        EntregaWebhook e = webhooks.buscarEntrega(tenantId, entregaId).orElseThrow(GestionarWebhooksService::noEncontrada);
        if (e.estado() == EntregaWebhook.Estado.PENDIENTE) throw new DomainException("ENTREGA_NO_REENVIABLE", "La entrega todavía está pendiente; khipu la sigue intentando");
        exigirActivo(obtener(tenantId, e.endpointId()));
        uow.ejecutar(() -> webhooks.reencolar(tenantId, entregaId));
    }

    private EndpointWebhook obtener(UUID tenantId, UUID id) { return webhooks.buscar(tenantId, id).orElseThrow(GestionarWebhooksService::noEncontrado); }

    private static void exigirActivo(EndpointWebhook e) {
        if (!e.activo()) throw new DomainException("WEBHOOK_PAUSADO", "El webhook está apagado" + (e.pausadoMotivo() == null ? "" : " (" + e.pausadoMotivo() + ")") + "; actívelo antes de enviar");
    }

    private static String descripcionValida(String d) {
        if (d == null || d.isBlank()) return null;
        String limpia = d.strip();
        if (limpia.length() > DESCRIPCION_MAXIMA) throw new DomainException("WEBHOOK_INVALIDO", "La descripción no puede pasar de " + DESCRIPCION_MAXIMA + " caracteres");
        return limpia;
    }

    static String generarSecreto() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static DomainException noEncontrado() { return new DomainException("NO_ENCONTRADO", "Webhook no encontrado"); }

    private static DomainException noEncontrada() { return new DomainException("NO_ENCONTRADO", "Entrega no encontrada"); }
}
