package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.RecuperarCdrUseCase;
import pe.factura.application.port.in.RecuperarCdrUseCase.Barrido;
import pe.factura.application.port.in.RecuperarCdrUseCase.Fallo;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;

/** Cada hora pide a SUNAT (getStatusCdr) las constancias que khipu no tiene (#36); solo empresas en producción. */
@Slf4j
@RequiredArgsConstructor
public class RecuperarCdrWorker {
    private final RecuperarCdrUseCase cdrs;

    @Scheduled(fixedDelayString = "${app.cdr.intervalo-ms:3600000}", initialDelayString = "${app.cdr.inicial-ms:120000}")
    public void tick() {
        try {
            registrar(cdrs.recuperarPendientes());
        } catch (Exception e) {
            log.error("Fallo del barrido de recuperación de CDR", e);
        }
    }

    /**
     * Lo esperado (SUNAT caído, sin CDR todavía, un estado que el dominio no admite) va en una línea por comprobante; lo demás (un fault definitivo de SUNAT, un error
     * del storage o del CDR) con su traza, porque no se arregla solo y el barrido lo seguirá encontrando cada hora (#357).
     */
    static void registrar(Barrido barrido) {
        if (!barrido.recuperados().isEmpty()) log.info("CDR recuperados de SUNAT: {}", barrido.recuperados().stream().map(Comprobante::nombreArchivo).toList());
        for (Fallo f : barrido.fallos()) {
            String comprobante = f.comprobante().nombreArchivo();
            if (f.causa() instanceof SunatTransientException || f.causa() instanceof DomainException)
                log.warn("No se recuperó el CDR de {}: {}", comprobante, f.causa().getMessage());
            else
                log.error("No se recuperó el CDR de {}; el barrido siguió con los demás", comprobante, f.causa());
        }
    }
}
