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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entrega los webhooks (#19). Cada entrega se firma con el secreto de su endpoint y se manda con tope de tiempo; un 2xx la cierra y cualquier otra cosa (5xx, 4xx, tiempo
 * agotado, red) la reprograma con espera creciente hasta {@link #MAXIMO_INTENTOS}, y después queda FALLIDA (se puede reenviar a mano). Un endpoint con
 * {@link #PAUSA_TRAS_FALLOS} intentos fallidos seguidos se apaga y se avisa por correo a la cuenta: dejar de intentarlo evita golpear un servidor caído y llenar el log.
 */
@Slf4j
@RequiredArgsConstructor
public class EntregarWebhooksService implements EntregarWebhooksUseCase {
    static final int LOTE = 20;
    static final Duration BLOQUEO = Duration.ofMinutes(2);
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

    @Override public int entregar() {
        List<Pendiente> lote = uow.ejecutar(() -> webhooks.tomarVencidas(LOTE, BLOQUEO));
        for (Pendiente p : lote) {
            try {
                entregar(p);
            } catch (RuntimeException e) {
                log.error("No se pudo procesar la entrega de webhook {}", p.entrega().id(), e);
            }
        }
        return lote.size();
    }

    private void entregar(Pendiente p) {
        EntregaWebhook e = p.entrega();
        long t = clock.instant().getEpochSecond();
        String secreto = new String(cifrador.descifrar(p.secretoCifrado()), StandardCharsets.UTF_8);
        Map<String, String> cabeceras = new LinkedHashMap<>();
        cabeceras.put("X-Khipu-Event", e.evento());
        cabeceras.put("X-Khipu-Delivery", e.id().toString());
        cabeceras.put(FirmaWebhook.CABECERA, FirmaWebhook.cabecera(secreto, t, e.payload()));
        Respuesta r = destino.entregar(p.url(), cabeceras, e.payload());
        Instant ahora = clock.instant();
        if (r.exitosa()) {
            uow.ejecutar(() -> webhooks.registrarExito(e.id(), e.endpointId(), r.http(), ahora));
            return;
        }
        int intento = e.intentos() + 1;
        Instant siguiente = intento >= MAXIMO_INTENTOS ? null : ahora.plus(ESPERAS.get(Math.min(intento, ESPERAS.size()) - 1));
        String error = r.error() != null ? r.error() : "El servidor respondió HTTP " + r.http();
        int fallos = uow.ejecutar(() -> webhooks.registrarFallo(e.id(), e.endpointId(), r.http() == 0 ? null : r.http(), error, ahora, siguiente));
        log.warn("Webhook {} a {}: intento {} fallido ({})", e.id(), p.url(), intento, error);
        if (fallos >= PAUSA_TRAS_FALLOS) pausar(e, p.url(), error);
    }

    private void pausar(EntregaWebhook e, String url, String ultimoError) {
        String motivo = "Se apagó solo tras " + PAUSA_TRAS_FALLOS + " entregas fallidas seguidas; la última: " + ultimoError;
        uow.ejecutar(() -> webhooks.pausar(e.endpointId(), motivo));
        log.error("Webhook {} ({}) pausado: {}", e.endpointId(), url, motivo);
        try {
            String email = avisos.situacionDe(e.tenantId(), LocalDate.now(clock.withZone(CicloMensual.ZONA))).map(s -> s.cuenta()).map(c -> c.email()).orElse(null);
            if (email == null) return;
            correo.enviar(email, "[khipu] Apagamos su webhook por entregas fallidas",
                    "Su webhook " + url + " dejó de responder: " + PAUSA_TRAS_FALLOS + " entregas seguidas fallaron y lo apagamos para no seguir insistiendo.\n\n"
                            + "Último error: " + ultimoError + "\n\n"
                            + "Corrija su servidor y vuelva a activarlo desde el portal (API keys y webhooks). Las entregas pendientes se mandan al reactivarlo; "
                            + "las que ya se agotaron se pueden reenviar desde el log de entregas.");
        } catch (RuntimeException ex) {
            log.error("No se pudo avisar por correo de la pausa del webhook {}", e.endpointId(), ex);
        }
    }

    @Override public long limpiar() {
        return uow.ejecutar(() -> webhooks.limpiar(clock.instant().minus(RETENCION)));
    }
}
