package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxWorkerTest {
    Clock clock = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));
    UnitOfWork uow = new UnitOfWork() {
        public <T> T ejecutar(Supplier<T> w) { return w.get(); }
        public void ejecutar(Runnable w) { w.run(); }
    };
    OutboxRepository outbox = mock(OutboxRepository.class);
    EnviarDocumentoUseCase enviar = mock(EnviarDocumentoUseCase.class);
    OutboxWorker worker = new OutboxWorker(outbox, uow, enviar, clock, 20);
    UUID tenant = UUID.randomUUID(), doc = UUID.randomUUID(), fila = UUID.randomUUID();

    private Comprobante conEstado(EstadoDocumento e, int intentos) {
        return Comprobante.rehidratar(doc, tenant, TipoDocumento.FACTURA, "F001", 1L, LocalDate.of(2026, 9, 13), "PEN", "0101",
                new Receptor("6", "20601234567", "X", null), List.of(new Item("P", "d", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO)),
                e, "h", "20100066603-01-F001-1", "k", null, null, intentos, e == EstadoDocumento.ERROR_ENVIO ? "timeout" : null);
    }

    @Test void aceptadoCompleta() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 0)));
        when(enviar.enviar(tenant, doc)).thenReturn(conEstado(EstadoDocumento.ACEPTADO, 1));
        assertThat(worker.procesar()).isEqualTo(1);
        verify(outbox).completar(fila);
    }

    @Test void errorEnvioReprogramaConBackoff() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 2)));
        when(enviar.enviar(tenant, doc)).thenReturn(conEstado(EstadoDocumento.ERROR_ENVIO, 3));
        worker.procesar();
        verify(outbox).reprogramar(fila, clock.instant().plus(Duration.ofMinutes(8)), "timeout");
    }

    @Test void superaMaximoYCompleta() {
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 2)));
        when(enviar.enviar(tenant, doc)).thenReturn(conEstado(EstadoDocumento.ERROR_ENVIO, 3));
        w.procesar();
        verify(outbox).completar(fila);
        verify(outbox, never()).reprogramar(any(), any(), any());
    }

    @Test void estadoNoEnviableCompleta() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 0)));
        when(enviar.enviar(tenant, doc)).thenThrow(new DomainException("ESTADO_NO_ENVIABLE", "ya aceptado"));
        worker.procesar();
        verify(outbox).completar(fila);
    }

    @Test void excepcionInesperadaReprograma() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 0)));
        when(enviar.enviar(tenant, doc)).thenThrow(new IllegalStateException("storage caído"));
        worker.procesar();
        verify(outbox).reprogramar(eq(fila), eq(clock.instant().plus(Duration.ofMinutes(2))), contains("storage caído"));
    }

    @Test void sinFilasNoHaceNada() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of());
        assertThat(worker.procesar()).isZero();
        verifyNoInteractions(enviar);
    }
}
