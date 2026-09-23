package pe.factura.adapters.scheduler;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.DarDeBajaUseCase;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.service.Backoff;
import pe.factura.application.service.DarDeBajaService;
import pe.factura.application.service.EnviarDocumentoService;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OutboxWorker {
    private static final int LOTE = 50;
    /**
     * El lote se procesa en serie y cada envío puede tardar hasta el timeout HTTP (15 s): 50 × 15 s = 12,5 min.
     * El lock debe superar ese peor caso para que otra instancia no retome una fila que este worker aún procesa.
     */
    private static final Duration LOCK = Duration.ofMinutes(20);

    private final OutboxRepository outbox;
    private final UnitOfWork uow;
    private final EnviarDocumentoUseCase enviar;
    private final DarDeBajaUseCase bajas;
    private final Clock clock;
    private final int maxIntentos;


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
            switch (fila.accion()) {
                case EnviarDocumentoService.ACCION_ENVIAR -> {
                    Comprobante c = enviar.enviar(fila.tenantId(), fila.agregadoId());
                    if (c.estado() == EstadoDocumento.ERROR_ENVIO) reintentarOAgotar(fila, c.nombreArchivo(), c.ultimoError(), Backoff.siguiente(fila.intentos() + 1, clock.instant()));
                    else outbox.completar(fila.id());
                }
                case DarDeBajaService.ACCION_BAJA -> {
                    // Una baja ENVIADA (ticket) se reconsulta pronto; una en ERROR_ENVIO (sin ticket) sigue el backoff de los envíos.
                    ComunicacionBaja b = bajas.continuar(fila.tenantId(), fila.agregadoId());
                    // Una baja pendiente NUNCA se abandona: el manual (§2.7) no fija plazo para que SUNAT resuelva un RA (98 durante
                    // más de 10 min es normal), y borrar la fila al intento 20 dejaba el comprobante «en curso» para siempre —bloqueado
                    // para otra baja y para notas— y, si SUNAT sí lo anuló, ACEPTADO en khipu. ENVIADA: consulta con intervalo
                    // creciente hasta 5 min; ERROR_ENVIO: backoff, hasta que venza el plazo (FUERA_DE_PLAZO descarta arriba).
                    if (!b.pendiente()) outbox.completar(fila.id());
                    else outbox.reprogramar(fila.id(),
                            b.estado() == ComunicacionBaja.EstadoBaja.ENVIADA ? clock.instant().plus(DarDeBajaService.consultaTicket(fila.intentos() + 1)) : Backoff.siguiente(fila.intentos() + 1, clock.instant()),
                            b.ultimoError());
                }
                default -> { log.warn("Acción desconocida {} en outbox {}", fila.accion(), fila.id()); outbox.completar(fila.id()); }
            }
        } catch (DomainException e) {
            if ("ESTADO_NO_ENVIABLE".equals(e.codigo()) || "NO_ENCONTRADO".equals(e.codigo()) || "FUERA_DE_PLAZO".equals(e.codigo())) {
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

    private void reintentarOAgotar(OutboxItem fila, String nombre, String error, java.time.Instant cuando) {
        int intentos = fila.intentos() + 1;
        if (intentos >= maxIntentos) {
            log.error("{} agotó {} intentos: {}", nombre, maxIntentos, error);
            outbox.completar(fila.id());
        } else {
            outbox.reprogramar(fila.id(), cuando, error);
        }
    }
}
