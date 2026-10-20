package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.DarDeBajaUseCase;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.in.EnviarGuiaRemisionUseCase;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.service.Backoff;
import pe.factura.application.service.EnviarGuiaRemisionService;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** #39: el outbox reintenta el envío de la guía mientras khipu pueda hacerlo solo y consulta su ticket hasta que SUNAT responda o se deje de consultar. */
class OutboxWorkerGuiasTest {
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    UnitOfWork uow = new UnitOfWork() {
        public <T> T ejecutar(Supplier<T> w) { return w.get(); }
        public void ejecutar(Runnable w) { w.run(); }
    };
    OutboxRepository outbox = mock(OutboxRepository.class);
    EnviarGuiaRemisionUseCase guias = mock(EnviarGuiaRemisionUseCase.class);
    OutboxWorker worker = new OutboxWorker(outbox, uow, mock(EnviarDocumentoUseCase.class), mock(DarDeBajaUseCase.class), clock, 5, guias);
    UUID tenant = UUID.randomUUID(), guia = UUID.randomUUID(), fila = UUID.randomUUID();

    GuiaRemision con(EstadoGuia estado, boolean reintento) {
        Traslado t = new Traslado(LocalDate.of(2026, 9, 13), BigDecimal.TEN, "KGM", null, PuntoDeTraslado.de("150101", "Av. X 1"), PuntoDeTraslado.de("040101", "Calle 1"),
                List.of(new Vehiculo("ABC123")), List.of(new Conductor("1", "45678912", "JUAN", "QUISPE", "Q45678912")), Set.of());
        return GuiaRemision.rehidratar(guia, tenant, "T001", 1L, LocalDate.of(2026, 9, 13), LocalTime.NOON, new ParteDeGuia("6", "20601234565", "COMERCIAL"),
                new DatosDeGuia.DeRemitente("01", "Venta", ModalidadTraslado.PRIVADO, null), t, List.of(new ItemDeGuia(null, "Caja", "BX", BigDecimal.ONE, null)), List.of(), null, estado,
                new GuiaRemision.Envio("20100066603-09-T001-1", "h", "k.xml", null, null, "tk", null, 1, 0, "0109 - tiempo agotado", reintento, null));
    }

    void tomar(String accion, int intentos) {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, guia, accion, intentos)));
    }

    @Test void unEnvioQueSunatNoRespondioSeReprogramaConBackoff() {
        tomar(EnviarGuiaRemisionService.ACCION_ENVIAR, 1);
        when(guias.enviar(tenant, guia)).thenReturn(con(EstadoGuia.ERROR_ENVIO, true));
        worker.procesar();
        verify(outbox).reprogramar(fila, Backoff.siguiente(2, clock.instant()), "0109 - tiempo agotado");
        verify(outbox, never()).completar(any());
    }

    @Test void unEnvioQueNoSeReintentaSoloOQueYaTieneTicketCompletaLaFila() {
        tomar(EnviarGuiaRemisionService.ACCION_ENVIAR, 0);
        when(guias.enviar(tenant, guia)).thenReturn(con(EstadoGuia.ERROR_ENVIO, false), con(EstadoGuia.ENVIADO_PENDIENTE_TICKET, true));
        worker.procesar();
        worker.procesar();
        verify(outbox, times(2)).completar(fila);
        verify(outbox, never()).reprogramar(any(), any(), any());
    }

    @Test void elEnvioSeAgotaAlTopeDeIntentos() {
        tomar(EnviarGuiaRemisionService.ACCION_ENVIAR, 4);
        when(guias.enviar(tenant, guia)).thenReturn(con(EstadoGuia.ERROR_ENVIO, true));
        worker.procesar();
        verify(outbox).completar(fila);
        // H4: la guía deja de decir que se reintenta sola.
        verify(guias).agotarReintentos(tenant, guia, 5);
    }

    @Test void elTicketEnProcesoSeVuelveAConsultarCadaVezMasEspaciado() {
        tomar(EnviarGuiaRemisionService.ACCION_TICKET, 3);
        when(guias.consultarTicket(tenant, guia)).thenReturn(con(EstadoGuia.ENVIADO_PENDIENTE_TICKET, true));
        worker.procesar();
        verify(outbox).reprogramar(eq(fila), eq(clock.instant().plus(Duration.ofMinutes(2))), any());
    }

    @Test void elTicketResueltoODetenidoCompletaLaFila() {
        tomar(EnviarGuiaRemisionService.ACCION_TICKET, 3);
        when(guias.consultarTicket(tenant, guia)).thenReturn(con(EstadoGuia.ACEPTADO, false), con(EstadoGuia.ENVIADO_PENDIENTE_TICKET, false));
        worker.procesar();
        worker.procesar();
        verify(outbox, times(2)).completar(fila);
        verify(outbox, never()).reprogramar(any(), any(), any());
    }

    @Test void unaGuiaQueYaNoEsperaElTicketDescartaLaFila() {
        tomar(EnviarGuiaRemisionService.ACCION_TICKET, 0);
        when(guias.consultarTicket(tenant, guia)).thenThrow(new DomainException("TICKET_NO_PENDIENTE", "ya aceptada"));
        worker.procesar();
        verify(outbox).completar(fila);
    }

    @Test void sinServicioDeGuiasLaFilaSeDescarta() {
        OutboxWorker sin = new OutboxWorker(outbox, uow, mock(EnviarDocumentoUseCase.class), mock(DarDeBajaUseCase.class), clock, 5);
        tomar(EnviarGuiaRemisionService.ACCION_TICKET, 0);
        sin.procesar();
        verify(outbox).completar(fila);
    }
}
