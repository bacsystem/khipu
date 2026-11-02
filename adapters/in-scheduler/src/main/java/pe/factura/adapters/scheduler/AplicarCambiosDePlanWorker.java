package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.AplicarCambiosDePlanUseCase;

/**
 * Cada diez minutos pasa a vigente la bajada de plan que ya llegó a su fecha (#191). La bajada se programa para el inicio del ciclo siguiente (la medianoche del día 1
 * en Lima); este trabajo la convierte en la suscripción activa, con la fecha programada como inicio, así que un retraso de unos minutos no cambia el historial.
 */
@Slf4j
@RequiredArgsConstructor
public class AplicarCambiosDePlanWorker {
    private final AplicarCambiosDePlanUseCase cambios;

    @Scheduled(fixedDelayString = "${app.planes.intervalo-ms:600000}", initialDelayString = "${app.planes.inicial-ms:90000}")
    public void tick() {
        try {
            AplicarCambiosDePlanUseCase.Resultado r = cambios.aplicarVencidos();
            if (r.aplicados() > 0) log.info("{} cambio(s) de plan aplicado(s)", r.aplicados());
            if (r.fallidos() > 0) log.warn("{} cambio(s) de plan no se pudieron aplicar; se reintentan en la siguiente pasada", r.fallidos());
        } catch (Exception e) {
            log.error("Fallo al aplicar los cambios de plan vencidos", e);
        }
    }
}
