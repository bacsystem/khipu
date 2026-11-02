package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.VigilarOperacionUseCase;

/**
 * Cada 5 minutos revisa lo que hoy falla en silencio y avisa al operador (#341). El registro de lo avisado impide repetirlo, así que correr seguido no manda más mensajes: solo
 * hace que el aviso llegue pronto. Una pasada que falla no detiene las siguientes.
 */
@Slf4j
@RequiredArgsConstructor
public class VigilarOperacionWorker {
    private final VigilarOperacionUseCase vigilante;

    @Scheduled(fixedDelayString = "${app.alertas.intervalo-ms:300000}", initialDelayString = "${app.alertas.inicial-ms:120000}")
    public void tick() {
        try {
            VigilarOperacionUseCase.Resultado r = vigilante.vigilar();
            if (r.avisadas() > 0 || r.resueltas() > 0) log.info("Vigilante: {} alerta(s) avisada(s), {} resuelta(s)", r.avisadas(), r.resueltas());
            for (String clave : r.fallos()) log.warn("Vigilante: no se pudo avisar de {}; se reintenta en la siguiente pasada", clave);
        } catch (Exception e) {
            log.error("Falló la pasada del vigilante de la operación", e);
        }
    }
}
