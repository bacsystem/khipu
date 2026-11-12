package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.PurgarTokensDeAccesoUseCase;

/** Cada hora borra los enlaces de recuperación y de verificación de correo vencidos hace más de la retención (#634). Solo registra cuántos: nada de hashes ni correos. */
@Slf4j
@RequiredArgsConstructor
public class LimpiezaTokensDeAccesoWorker {
    private final PurgarTokensDeAccesoUseCase purga;

    @Scheduled(fixedDelayString = "${app.tokens.intervalo-ms:3600000}", initialDelayString = "${app.tokens.inicial-ms:330000}")
    public void tick() {
        try {
            int borrados = purga.purgar();
            if (borrados > 0) log.info("{} token(s) de acceso vencido(s) borrado(s)", borrados);
        } catch (Exception e) {
            log.error("Fallo de la limpieza de tokens de acceso vencidos", e);
        }
    }
}
