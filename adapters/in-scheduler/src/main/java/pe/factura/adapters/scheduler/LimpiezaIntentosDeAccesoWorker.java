package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.PurgarIntentosDeAccesoUseCase;

/** Cada hora borra los contadores de intentos de login y de recuperación que ya no limitan nada (#261). */
@Slf4j
@RequiredArgsConstructor
public class LimpiezaIntentosDeAccesoWorker {
    private final PurgarIntentosDeAccesoUseCase purga;

    @Scheduled(fixedDelayString = "${app.intentos.intervalo-ms:3600000}", initialDelayString = "${app.intentos.inicial-ms:180000}")
    public void tick() {
        try {
            int borrados = purga.purgar();
            if (borrados > 0) log.info("{} contador(es) de intentos de acceso viejo(s) borrado(s)", borrados);
        } catch (Exception e) {
            log.error("Fallo de la limpieza de contadores de intentos de acceso", e);
        }
    }
}
