package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.ControlarPlazoEnvioUseCase;
import pe.factura.domain.documento.Comprobante;

import java.util.List;

/** Cada hora marca FUERA_DE_PLAZO lo que no llegó a SUNAT a tiempo (#37); el outbox descarta sus reintentos al encontrarlos no enviables. */
@Slf4j
@RequiredArgsConstructor
public class PlazoEnvioWorker {
    private final ControlarPlazoEnvioUseCase plazos;

    @Scheduled(fixedDelayString = "${app.plazo-envio.intervalo-ms:3600000}", initialDelayString = "${app.plazo-envio.inicial-ms:60000}")
    public void tick() {
        try {
            List<Comprobante> vencidos = plazos.marcarVencidos();
            if (!vencidos.isEmpty()) log.warn("{} comprobante(s) fuera de plazo de envío: {}", vencidos.size(), vencidos.stream().map(Comprobante::nombreArchivo).toList());
        } catch (Exception e) {
            log.error("Fallo del control de plazo de envío", e);
        }
    }
}
