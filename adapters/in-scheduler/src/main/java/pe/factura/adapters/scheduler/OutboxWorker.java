package pe.factura.adapters.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.DarDeBajaUseCase;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.EnviarGuiaRemisionUseCase;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.service.Backoff;
import pe.factura.application.service.DarDeBajaService;
import pe.factura.application.service.EnviarDocumentoService;
import pe.factura.application.service.EnviarGuiaRemisionService;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.ComunicacionBaja;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.EstadoGuia;
import pe.factura.domain.documento.GuiaRemision;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Slf4j
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
    /** #39: el envío de las guías de remisión y la consulta de su ticket; {@code null} donde no se emiten guías (pruebas del resto). */
    private final EnviarGuiaRemisionUseCase guias;

    public OutboxWorker(OutboxRepository outbox, UnitOfWork uow, EnviarDocumentoUseCase enviar, DarDeBajaUseCase bajas, Clock clock, int maxIntentos, EnviarGuiaRemisionUseCase guias) {
        this.outbox = outbox; this.uow = uow; this.enviar = enviar; this.bajas = bajas; this.clock = clock; this.maxIntentos = maxIntentos; this.guias = guias;
    }

    public OutboxWorker(OutboxRepository outbox, UnitOfWork uow, EnviarDocumentoUseCase enviar, DarDeBajaUseCase bajas, Clock clock, int maxIntentos) {
        this(outbox, uow, enviar, bajas, clock, maxIntentos, null);
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
            switch (fila.accion()) {
                case EnviarDocumentoService.ACCION_ENVIAR -> {
                    Comprobante c = enviar.enviar(fila.tenantId(), fila.agregadoId());
                    if (c.estado() == EstadoDocumento.ERROR_ENVIO) reintentarOAgotar(fila, c, Backoff.siguiente(fila.intentos() + 1, clock.instant()));
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
                // #39: una guía en error de envío se reintenta con el backoff de los envíos mientras khipu pueda reintentarla sola (SUNAT no respondió) y hasta
                // agotar los intentos; si SUNAT dio el ticket, la consulta la programa el servicio (acción GRE_TICKET).
                case EnviarGuiaRemisionService.ACCION_ENVIAR -> {
                    if (guias == null) { log.warn("Outbox {}: {} sin servicio de guías", fila.id(), fila.accion()); outbox.completar(fila.id()); break; }
                    GuiaRemision g = guias.enviar(fila.tenantId(), fila.agregadoId());
                    if (g.estado() == EstadoGuia.ERROR_ENVIO && g.reintentoAutomatico()) {
                        if (fila.intentos() + 1 >= maxIntentos) {
                            // H4: la guía deja de esperar un reintento que ya no llegará, y lo dice.
                            log.error("{} agotó {} intentos: {}", g.nombreArchivo(), maxIntentos, g.ultimoError());
                            guias.agotarReintentos(fila.tenantId(), fila.agregadoId(), maxIntentos);
                            outbox.completar(fila.id());
                        }
                        else outbox.reprogramar(fila.id(), Backoff.siguiente(fila.intentos() + 1, clock.instant()), g.ultimoError());
                    } else outbox.completar(fila.id());
                }
                // #39: el ticket se consulta mientras SUNAT diga «en proceso», cada vez más espaciado; el tope de consultas lo aplica el servicio (deja reintento_automatico en false).
                case EnviarGuiaRemisionService.ACCION_TICKET -> {
                    if (guias == null) { log.warn("Outbox {}: {} sin servicio de guías", fila.id(), fila.accion()); outbox.completar(fila.id()); break; }
                    GuiaRemision g = guias.consultarTicket(fila.tenantId(), fila.agregadoId());
                    if (g.estado().esperaTicket() && g.reintentoAutomatico())
                        outbox.reprogramar(fila.id(), clock.instant().plus(EnviarGuiaRemisionService.intervaloDeConsulta(fila.intentos() + 1)), g.ultimoError());
                    else outbox.completar(fila.id());
                }
                default -> { log.warn("Acción desconocida {} en outbox {}", fila.accion(), fila.id()); outbox.completar(fila.id()); }
            }
        } catch (DomainException e) {
            // SUNAT_YA_LO_TIENE (LOG-01): el envío ya dejó el comprobante en ERROR_ENVIO con el motivo y nadie puede recuperar su constancia; reintentar mandaría el mismo XML.
            if ("ESTADO_NO_ENVIABLE".equals(e.codigo()) || "NO_ENCONTRADO".equals(e.codigo()) || "FUERA_DE_PLAZO".equals(e.codigo()) || "TICKET_NO_PENDIENTE".equals(e.codigo())
                    || "SUNAT_YA_LO_TIENE".equals(e.codigo())) {
                log.info("Outbox {} descartada: {} {}", fila.id(), e.codigo(), e.getMessage());
                outbox.completar(fila.id());
            } else {
                log.warn("Outbox {} con error de dominio {}: {}", fila.id(), e.codigo(), e.getMessage());
                reprogramarOAgotarTrasExcepcion(fila, e.codigo() + " - " + e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Outbox {} falló", fila.id(), e);
            reprogramarOAgotarTrasExcepcion(fila, e.toString());
        }
    }

    /**
     * LOG-02: una excepción (de dominio no descartable o inesperada) también cuenta contra el tope de intentos; antes se reprogramaba sin mirarlo y una falla que no
     * se convierte en ERROR_ENVIO reenviaba el mismo XML para siempre. La baja es la excepción: nunca se abandona (ver {@code ACCION_BAJA} arriba), porque sin su
     * fila el comprobante queda «en curso» para siempre.
     */
    private void reprogramarOAgotarTrasExcepcion(OutboxItem fila, String motivo) {
        int intentos = fila.intentos() + 1;
        if (intentos >= maxIntentos && !DarDeBajaService.ACCION_BAJA.equals(fila.accion())) {
            log.error("Outbox {} ({} {}) agotó {} intentos con excepción: {}", fila.id(), fila.accion(), fila.agregadoId(), maxIntentos, motivo);
            outbox.completar(fila.id());
        } else {
            outbox.reprogramar(fila.id(), Backoff.siguiente(intentos, clock.instant()), motivo);
        }
    }

    /**
     * #356: una boleta no se agota mientras siga en plazo del resumen diario (hasta el 7.º día): 20 intentos son unas 74 horas y, rendida al 3.er día, quedaba sin informarse con
     * cuatro por delante. Desde el 6.º día {@code enviar} la manda al resumen diario, así que seguir reintentando es lo que la rescata. Pasado el plazo se agota como el resto:
     * queda en error de envío, visible en «atención requerida» y con el envío a mano.
     */
    private void reintentarOAgotar(OutboxItem fila, Comprobante c, java.time.Instant cuando) {
        int intentos = fila.intentos() + 1;
        if (intentos >= maxIntentos && !c.noSeAbandonaTodavia(LocalDate.now(clock))) {
            log.error("{} agotó {} intentos: {}", c.nombreArchivo(), maxIntentos, c.ultimoError());
            outbox.completar(fila.id());
        } else {
            outbox.reprogramar(fila.id(), cuando, c.ultimoError());
        }
    }
}
