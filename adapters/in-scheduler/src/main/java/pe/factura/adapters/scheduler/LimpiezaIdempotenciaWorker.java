package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.LimpiarIdempotenciaUseCase;

/** Cada hora borra las claves de idempotencia vencidas (#115). */
@Slf4j
@RequiredArgsConstructor
public class LimpiezaIdempotenciaWorker {
    private final LimpiarIdempotenciaUseCase limpieza;

    @Scheduled(fixedDelayString = "${app.idempotencia.intervalo-ms:3600000}", initialDelayString = "${app.idempotencia.inicial-ms:120000}")
    public void tick() {
        try {
            int borradas = limpieza.limpiar();
            if (borradas > 0) log.info("{} clave(s) de idempotencia vencida(s) borrada(s)", borradas);
        } catch (Exception e) {
            log.error("Fallo de la limpieza de claves de idempotencia", e);
        }
    }
}
