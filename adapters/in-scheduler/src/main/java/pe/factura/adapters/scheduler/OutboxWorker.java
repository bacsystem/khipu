package pe.factura.adapters.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.service.Backoff;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int LOTE = 50;
    /**
     * El lote se procesa en serie y cada envío puede tardar hasta el timeout HTTP (15 s): 50 × 15 s = 12,5 min.
     * El lock debe superar ese peor caso para que otra instancia no retome una fila que este worker aún procesa.
     */
    private static final Duration LOCK = Duration.ofMinutes(20);

    private final OutboxRepository outbox;
    private final UnitOfWork uow;
    private final EnviarDocumentoUseCase enviar;
    private final Clock clock;
    private final int maxIntentos;

    public OutboxWorker(OutboxRepository outbox, UnitOfWork uow, EnviarDocumentoUseCase enviar, Clock clock, int maxIntentos) {
        this.outbox = outbox; this.uow = uow; this.enviar = enviar; this.clock = clock; this.maxIntentos = maxIntentos;
    }

    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms:10000}")
    public void tick() {
        try { procesar(); } catch (Exception e) { log.error("Fallo del ciclo de outbox", e); }
    }

    public int procesar() {
        List<OutboxItem> filas = uow.ejecutar(() -> outbox.tomarVencidas(LOTE, LOCK));
        for (OutboxItem fila : filas) procesarFila(fila);
        return filas.size();
    }

    private void procesarFila(OutboxItem fila) {
        try {
            if (!"ENVIAR".equals(fila.accion())) { log.warn("Acción desconocida {} en outbox {}", fila.accion(), fila.id()); outbox.completar(fila.id()); return; }
            Comprobante c = enviar.enviar(fila.tenantId(), fila.agregadoId());
            if (c.estado() == EstadoDocumento.ERROR_ENVIO) {
                int intentos = fila.intentos() + 1;
                if (intentos >= maxIntentos) {
                    log.error("Documento {} agotó {} intentos de envío: {}", c.nombreArchivo(), maxIntentos, c.ultimoError());
                    outbox.completar(fila.id());
                } else {
                    outbox.reprogramar(fila.id(), Backoff.siguiente(intentos, clock.instant()), c.ultimoError());
                }
            } else {
                outbox.completar(fila.id());
            }
        } catch (DomainException e) {
            if ("ESTADO_NO_ENVIABLE".equals(e.codigo()) || "NO_ENCONTRADO".equals(e.codigo())) {
                log.info("Outbox {} descartada: {} {}", fila.id(), e.codigo(), e.getMessage());
                outbox.completar(fila.id());
            } else {
                log.warn("Outbox {} con error de dominio {}, se reprograma: {}", fila.id(), e.codigo(), e.getMessage());
                outbox.reprogramar(fila.id(), Backoff.siguiente(fila.intentos() + 1, clock.instant()), e.codigo() + " - " + e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Outbox {} falló, se reprograma", fila.id(), e);
            outbox.reprogramar(fila.id(), Backoff.siguiente(fila.intentos() + 1, clock.instant()), e.toString());
        }
    }
}
