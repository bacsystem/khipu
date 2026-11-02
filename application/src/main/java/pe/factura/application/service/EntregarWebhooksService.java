package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.in.EntregarWebhooksUseCase;
import pe.factura.application.port.out.AvisosRepository;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.DestinoDeWebhook;
import pe.factura.application.port.out.DestinoDeWebhook.Respuesta;
import pe.factura.application.port.out.SecretCipher;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.port.out.WebhookRepository;
import pe.factura.application.port.out.WebhookRepository.Pendiente;
import pe.factura.domain.plan.CicloMensual;
import pe.factura.domain.webhook.EntregaWebhook;
import pe.factura.domain.webhook.FirmaWebhook;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * Entrega los webhooks (#19). Cada entrega se firma con el secreto de su endpoint y se manda con tope de tiempo; un 2xx la cierra y cualquier otra cosa (5xx, 4xx, tiempo
 * agotado, red) la reprograma con espera creciente hasta {@link #MAXIMO_INTENTOS}, y después queda FALLIDA (se puede reenviar a mano). Un endpoint con
 * {@link #PAUSA_TRAS_FALLOS} intentos fallidos seguidos se apaga y se avisa por correo a la cuenta: dejar de intentarlo evita golpear un servidor caído y llenar el log.
 *
 * <p>Un servidor lento no frena a los demás: el lote toma como mucho {@link #POR_ENDPOINT} entregas de cada endpoint, los endpoints se atienden en paralelo (cada uno en
 * orden) y el bloqueo ({@link #BLOQUEO}) sobra para el peor caso de un endpoint (5 × 15 s). Aun así el resultado solo se anota si la entrega sigue tomada con la marca con
 * que se tomó, así que una instancia lenta nunca pisa a la que la volvió a tomar. Antes de cada envío se vuelve a mirar si el endpoint sigue encendido.
 */
@Slf4j
@RequiredArgsConstructor
public class EntregarWebhooksService implements EntregarWebhooksUseCase {
    static final int LOTE = 50;
    static final int POR_ENDPOINT = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(5);
    public static final int MAXIMO_INTENTOS = 8;
    public static final int PAUSA_TRAS_FALLOS = 10;
    /** La espera tras el intento n (1 a 7): 1 min, 5 min, 15 min, 1 h, 3 h, 6 h, 12 h; en total, unas 22 h hasta el octavo y último. */
    static final List<Duration> ESPERAS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(3),
            Duration.ofHours(6), Duration.ofHours(12));

    private final WebhookRepository webhooks;
    private final DestinoDeWebhook destino;
    private final SecretCipher cifrador;
    private final UnitOfWork uow;
    private final Clock clock;
    private final AvisosRepository avisos;
    private final CorreoSender correo;
    /** Donde se atienden los endpoints en paralelo. */
    private final ExecutorService pool;

    @Override public int entregar() {
        List<Pendiente> lote = uow.ejecutar(() -> webhooks.tomarVencidas(LOTE, POR_ENDPOINT, BLOQUEO));
        if (lote.isEmpty()) return 0;
        Map<UUID, List<Pendiente>> porEndpoint = new LinkedHashMap<>();
        for (Pendiente p : lote) porEndpoint.computeIfAbsent(p.entrega().endpointId(), k -> new ArrayList<>()).add(p);
        List<Callable<Void>> tareas = new ArrayList<>();
        for (List<Pendiente> deUnEndpoint : porEndpoint.values()) tareas.add(() -> {
            atender(deUnEndpoint);
            return null;
        });
        try {
            pool.invokeAll(tareas);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return lote.size();
    }

    /** Las entregas de un endpoint, una tras otra. Si deja de estar encendido (se pausó con una de ellas, o lo apagó una persona), el resto se suelta sin enviarse. */
    private void atender(List<Pendiente> deUnEndpoint) {
        boolean seguir = true;
        for (Pendiente p : deUnEndpoint) {
            try {
                if (seguir && !webhooks.estaActivo(p.entrega().endpointId())) seguir = false;
                if (!seguir) {
                    webhooks.liberar(p);
                    continue;
                }
                entregar(p);
            } catch (RuntimeException e) {
                log.error("No se pudo procesar la entrega de webhook {}", p.entrega().id(), e);
            }
        }
    }

    private void entregar(Pendiente p) {
        EntregaWebhook e = p.entrega();
        Respuesta r;
        long t = clock.instant().getEpochSecond();
        String secreto;
        try {
            secreto = new String(cifrador.descifrar(p.secretoCifrado()), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            // Sin poder leer el secreto (MASTER_KEY cambiada de golpe) no se puede firmar: cuenta como fallo, para que llegue a FALLIDA y a la pausa en vez de reintentarse para siempre.
            log.error("No se pudo descifrar el secreto del webhook {}", e.endpointId(), ex);
            fallar(p, new Respuesta(0, "No se pudo leer el secreto de firma; rote el secreto del webhook"));
            return;
        }
        Map<String, String> cabeceras = new LinkedHashMap<>();
        cabeceras.put("X-Khipu-Event", e.evento());
        cabeceras.put("X-Khipu-Delivery", e.id().toString());
        cabeceras.put(FirmaWebhook.CABECERA, FirmaWebhook.cabecera(secreto, t, e.payload()));
        r = destino.entregar(p.url(), cabeceras, e.payload());
        Instant ahora = clock.instant();
        if (r.exitosa()) {
            boolean anotada = uow.ejecutar(() -> webhooks.registrarExito(p, r.http(), ahora));
            if (!anotada) log.warn("Webhook {}: la entrega {} ya no estaba tomada por esta instancia; no se anota el resultado", e.endpointId(), e.id());
            return;
        }
        fallar(p, r);
    }

    private void fallar(Pendiente p, Respuesta r) {
        EntregaWebhook e = p.entrega();
        Instant ahora = clock.instant();
        int intento = e.intentos() + 1;
        Instant siguiente = intento >= MAXIMO_INTENTOS ? null : ahora.plus(ESPERAS.get(Math.min(intento, ESPERAS.size()) - 1));
        String error = r.error() != null ? r.error() : "El servidor respondió HTTP " + r.http();
        int fallos = uow.ejecutar(() -> webhooks.registrarFallo(p, r.http() == 0 ? null : r.http(), error, ahora, siguiente));
        if (fallos < 0) {
            log.warn("Webhook {}: la entrega {} ya no estaba tomada por esta instancia; no se anota el fallo", e.endpointId(), e.id());
            return;
        }
        log.warn("Webhook {} ({}): intento {} de la entrega {} fallido ({})", e.endpointId(), servidorDe(p.url()), intento, e.id(), error);
        if (fallos >= PAUSA_TRAS_FALLOS) pausar(e, p.url(), error);
    }

    private void pausar(EntregaWebhook e, String url, String ultimoError) {
        String motivo = "Se apagó solo tras " + PAUSA_TRAS_FALLOS + " entregas fallidas seguidas; la última: " + ultimoError;
        // Solo la primera pausa avisa: las demás entregas ya tomadas del mismo lote ven el endpoint apagado y se sueltan.
        boolean pausado = uow.ejecutar(() -> webhooks.pausar(e.endpointId(), motivo));
        if (!pausado) return;
        log.error("Webhook {} ({}) pausado: {}", e.endpointId(), servidorDe(url), motivo);
        try {
            String email = avisos.situacionDe(e.tenantId(), LocalDate.now(clock.withZone(CicloMensual.ZONA))).map(s -> s.cuenta()).map(c -> c.email()).orElse(null);
            if (email == null) return;
            correo.enviar(email, "[khipu] Apagamos su webhook por entregas fallidas",
                    "Su webhook " + servidorDe(url) + " dejó de responder: " + PAUSA_TRAS_FALLOS + " entregas seguidas fallaron y lo apagamos para no seguir insistiendo.\n\n"
                            + "Último error: " + ultimoError + "\n\n"
                            + "Corrija su servidor y vuelva a activarlo desde el portal (API keys y webhooks). Mientras está apagado seguimos guardando los eventos nuevos "
                            + "(hasta " + RETENCION.toDays() + " días) y las entregas pendientes salen enseguida al reactivarlo; las que ya se agotaron se pueden reenviar desde el log de entregas.");
        } catch (RuntimeException ex) {
            log.error("No se pudo avisar por correo de la pausa del webhook {}", e.endpointId(), ex);
        }
    }

    /** Solo el servidor de la URL: la consulta suele llevar un token del integrador y no debe quedar en el log ni en el correo. */
    static String servidorDe(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "(URL inválida)" : host;
        } catch (IllegalArgumentException e) {
            return "(URL inválida)";
        }
    }

    @Override public long limpiar() {
        return uow.ejecutar(() -> webhooks.limpiar(clock.instant().minus(RETENCION)));
    }
}
