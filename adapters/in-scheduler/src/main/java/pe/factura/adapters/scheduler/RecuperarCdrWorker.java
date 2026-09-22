package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.RecuperarCdrUseCase;
import pe.factura.domain.documento.Comprobante;

import java.util.List;

/** Cada hora pide a SUNAT (getStatusCdr) las constancias que khipu no tiene (#36); solo empresas en producción. */
@Slf4j
@RequiredArgsConstructor
public class RecuperarCdrWorker {
    private final RecuperarCdrUseCase cdrs;

    @Scheduled(fixedDelayString = "${app.cdr.intervalo-ms:3600000}", initialDelayString = "${app.cdr.inicial-ms:120000}")
    public void tick() {
        try {
            List<Comprobante> recuperados = cdrs.recuperarPendientes();
            if (!recuperados.isEmpty()) log.info("CDR recuperados de SUNAT: {}", recuperados.stream().map(Comprobante::nombreArchivo).toList());
        } catch (Exception e) {
            log.error("Fallo del barrido de recuperación de CDR", e);
        }
    }
}
