package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.PurgarSesionesUseCase;

/** Cada hora borra las sesiones de refresh vencidas hace más de la retención (#611). Solo registra cuántas: nada de ids, hashes ni correos. */
@Slf4j
@RequiredArgsConstructor
public class LimpiezaSesionesWorker {
    private final PurgarSesionesUseCase purga;

    @Scheduled(fixedDelayString = "${app.sesiones.intervalo-ms:3600000}", initialDelayString = "${app.sesiones.inicial-ms:240000}")
    public void tick() {
        try {
            int borradas = purga.purgar();
            if (borradas > 0) log.info("{} sesion(es) vencida(s) borrada(s)", borradas);
        } catch (Exception e) {
            log.error("Fallo de la limpieza de sesiones vencidas", e);
        }
    }
}
