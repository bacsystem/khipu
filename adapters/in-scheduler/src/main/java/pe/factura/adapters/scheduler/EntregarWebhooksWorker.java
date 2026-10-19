package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.EntregarWebhooksUseCase;

/**
 * Cada 5 segundos manda las entregas de webhooks que les toca (#19): el integrador se entera de un comprobante aceptado casi al instante. Una pasada que falla no detiene
 * las siguientes. La limpieza del log viejo corre una vez al día.
 */
@Slf4j
@RequiredArgsConstructor
public class EntregarWebhooksWorker {
    private final EntregarWebhooksUseCase entregas;

    @Scheduled(fixedDelayString = "${app.webhooks.intervalo-ms:5000}", initialDelayString = "${app.webhooks.inicial-ms:30000}")
    public void tick() {
        try {
            int n = entregas.entregar();
            if (n > 0) log.debug("{} entrega(s) de webhook procesada(s)", n);
        } catch (Exception e) {
            log.error("Falló la pasada de entrega de webhooks", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.webhooks.limpieza-ms:86400000}", initialDelayString = "${app.webhooks.limpieza-inicial-ms:600000}")
    public void limpiar() {
        try {
            long n = entregas.limpiar();
            if (n > 0) log.info("{} entrega(s) de webhook cerradas hace más de {} días borradas", n, EntregarWebhooksUseCase.RETENCION.toDays());
        } catch (Exception e) {
            log.error("Falló la limpieza del log de webhooks", e);
        }
    }
}
