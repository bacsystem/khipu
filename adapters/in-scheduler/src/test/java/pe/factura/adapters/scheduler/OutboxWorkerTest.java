package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.DarDeBajaUseCase;
import pe.factura.application.port.in.EnviarDocumentoUseCase;
import pe.factura.application.port.out.OutboxItem;
import pe.factura.application.port.out.OutboxRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.application.service.Backoff;
import pe.factura.application.service.DarDeBajaService;
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
    DarDeBajaUseCase bajas = mock(DarDeBajaUseCase.class);
    OutboxWorker worker = new OutboxWorker(outbox, uow, enviar, bajas, clock, 20);
    UUID tenant = UUID.randomUUID(), doc = UUID.randomUUID(), fila = UUID.randomUUID();

    private Comprobante conEstado(EstadoDocumento e, int intentos) {
        return Comprobante.persistido(doc, tenant, TipoDocumento.FACTURA, "F001", 1L, LocalDate.of(2026, 9, 13), e, new Receptor("6", "20601234565", "X", null), List.of(new Item("P", "d", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).firma("h", "20100066603-01-F001-1", "k").envio(intentos, e == EstadoDocumento.ERROR_ENVIO ? "timeout" : null).rehidratar();
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
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, bajas, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 2)));
        when(enviar.enviar(tenant, doc)).thenReturn(conEstado(EstadoDocumento.ERROR_ENVIO, 3));
        w.procesar();
        verify(outbox).completar(fila);
        verify(outbox, never()).reprogramar(any(), any(), any());
    }

    private Comprobante boletaEnError(LocalDate emision, int intentos) {
        return Comprobante.persistido(doc, tenant, TipoDocumento.BOLETA, "B001", 1L, emision, EstadoDocumento.ERROR_ENVIO, new Receptor("1", "12345678", "JUAN PEREZ", null),
                List.of(new Item("P", "d", "NIU", BigDecimal.ONE, BigDecimal.TEN, TipoAfectacionIgv.GRAVADO))).firma("h", "20100066603-03-B001-1", "k").envio(intentos, "timeout").rehidratar();
    }

    /**
     * #356, camino 2: con el backoff, 20 intentos son unas 74 horas. Una boleta que SUNAT no recibe el día de la emisión se rendía al 3.er día, con cuatro todavía por delante en el
     * resumen diario, y quedaba sin informarse sin que nadie lo viera. Mientras siga en plazo, la fila se reprograma aunque haya agotado los intentos; pasado el 7.º día se agota.
     */
    @Test void unaBoletaEnPlazoNoSeAgotaAunqueSuperePorMaximo() {
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, bajas, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 2)));
        when(enviar.enviar(tenant, doc)).thenReturn(boletaEnError(LocalDate.of(2026, 9, 10), 3));   // día 3 de 7
        w.procesar();
        verify(outbox, never()).completar(fila);
        verify(outbox).reprogramar(fila, Backoff.siguiente(3, clock.instant()), "timeout");
    }

    @Test void unaBoletaEnElUltimoDiaDelResumenTodaviaNoSeAgota() {
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, bajas, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 30)));
        when(enviar.enviar(tenant, doc)).thenReturn(boletaEnError(LocalDate.of(2026, 9, 6), 31));   // 7.º día: el último en plazo
        w.procesar();
        verify(outbox, never()).completar(fila);
        verify(outbox).reprogramar(eq(fila), any(), eq("timeout"));
    }

    @Test void unaBoletaPasadoElSeptimoDiaYaPuedeAgotarse() {
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, bajas, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 30)));
        when(enviar.enviar(tenant, doc)).thenReturn(boletaEnError(LocalDate.of(2026, 9, 5), 31));   // 8.º día
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

    /** El plazo venció mientras reintentaba (#37): el envío lo cierra como FUERA_DE_PLAZO y la fila se completa, no se reprograma. */
    @Test void fueraDePlazoCompleta() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 5)));
        when(enviar.enviar(tenant, doc)).thenThrow(new DomainException("FUERA_DE_PLAZO", "2108 - venció el 2026-09-12"));
        worker.procesar();
        verify(outbox).completar(fila);
        verify(outbox, never()).reprogramar(any(), any(), any());
    }

    /** LOG-01: SUNAT ya lo tiene y nadie puede recuperar la constancia: el envío dejó ERROR_ENVIO con el motivo; la fila se completa, no se reenvía el mismo XML. */
    @Test void sunatYaLoTieneCompletaSinReprogramar() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 3)));
        when(enviar.enviar(tenant, doc)).thenThrow(new DomainException("SUNAT_YA_LO_TIENE", "B001-1: 1033 - registrado previamente"));
        worker.procesar();
        verify(outbox).completar(fila);
        verify(outbox, never()).reprogramar(any(), any(), any());
    }

    @Test void domainExceptionDeConfiguracionReprograma() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 0)));
        when(enviar.enviar(tenant, doc)).thenThrow(new DomainException("CREDENCIALES_SOL_NO_CARGADAS", "sin credenciales"));
        worker.procesar();
        verify(outbox).reprogramar(fila, clock.instant().plus(Duration.ofMinutes(2)), "CREDENCIALES_SOL_NO_CARGADAS - sin credenciales");
        verify(outbox, never()).completar(any());
    }

    @Test void excepcionInesperadaReprograma() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 0)));
        when(enviar.enviar(tenant, doc)).thenThrow(new IllegalStateException("storage caído"));
        worker.procesar();
        verify(outbox).reprogramar(eq(fila), eq(clock.instant().plus(Duration.ofMinutes(2))), contains("storage caído"));
    }

    /**
     * LOG-02: la rama de excepción reprogramaba sin pasar por el tope de intentos, así que una falla que no se convierte en ERROR_ENVIO (una excepción al
     * confirmar la transacción, un error de dominio de configuración que nadie corrige) reenviaba el mismo XML para siempre. El tope aplica también aquí.
     */
    @Test void excepcionInesperadaAlUltimoIntentoSeAgota() {
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, bajas, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 2)));
        when(enviar.enviar(tenant, doc)).thenThrow(new IllegalStateException("storage caído"));
        w.procesar();
        verify(outbox).completar(fila);
        verify(outbox, never()).reprogramar(any(), any(), any());
    }

    @Test void domainExceptionDeConfiguracionAlUltimoIntentoSeAgota() {
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, bajas, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "ENVIAR", 2)));
        when(enviar.enviar(tenant, doc)).thenThrow(new DomainException("CREDENCIALES_SOL_NO_CARGADAS", "sin credenciales"));
        w.procesar();
        verify(outbox).completar(fila);
        verify(outbox, never()).reprogramar(any(), any(), any());
    }

    /** Una baja nunca se abandona (ver el comentario del worker): la excepción inesperada se reprograma aunque haya agotado los intentos. */
    @Test void unaBajaConExcepcionInesperadaNoSeAgota() {
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, bajas, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, pe.factura.application.service.DarDeBajaService.ACCION_BAJA, 2)));
        when(bajas.continuar(tenant, doc)).thenThrow(new IllegalStateException("storage caído"));
        w.procesar();
        verify(outbox).reprogramar(eq(fila), any(), contains("storage caído"));
        verify(outbox, never()).completar(any());
    }

    @Test void sinFilasNoHaceNada() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of());
        assertThat(worker.procesar()).isZero();
        verifyNoInteractions(enviar);
    }

    private ComunicacionBaja baja(ComunicacionBaja.EstadoBaja estado) {
        return ComunicacionBaja.rehidratar(doc, tenant, LocalDate.of(2026, 9, 13), 1, UUID.randomUUID(), TipoDocumento.FACTURA, false, "F001", 1, LocalDate.of(2026, 9, 13), "Error",
                ComunicacionBaja.Condicion.BAJA, estado, estado == ComunicacionBaja.EstadoBaja.GENERADA ? null : "T-1", "k.xml", null, null, 1, estado == ComunicacionBaja.EstadoBaja.ENVIADA ? "98 - en proceso" : "timeout", 1, 1);
    }

    /**
     * Una baja pendiente no se abandona nunca (auditoría de bajas): con max-intentos 3 y una fila en el intento 10 se sigue
     * reprogramando —a 5 min, el tope del intervalo de consulta— en vez de completar (borrar) la fila y dejar el comprobante «en curso».
     */
    @Test void unaBajaEnviadaNuncaSeAgota() {
        OutboxWorker w = new OutboxWorker(outbox, uow, enviar, bajas, clock, 3);
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "BAJA", 10)));
        when(bajas.continuar(tenant, doc)).thenReturn(baja(ComunicacionBaja.EstadoBaja.ENVIADA));
        w.procesar();
        verify(outbox, never()).completar(fila);
        verify(outbox).reprogramar(fila, clock.instant().plus(DarDeBajaService.CONSULTA_MAXIMA), "98 - en proceso");
        // ERROR_ENVIO (sin ticket) tampoco: sigue el backoff.
        when(bajas.continuar(tenant, doc)).thenReturn(baja(ComunicacionBaja.EstadoBaja.ERROR_ENVIO));
        w.procesar();
        verify(outbox, never()).completar(fila);
        verify(outbox).reprogramar(fila, Backoff.siguiente(11, clock.instant()), "timeout");
    }

    @Test void bajaAceptadaCompletaYEnviadaSeReconsultaPronto() {
        when(outbox.tomarVencidas(anyInt(), any())).thenReturn(List.of(new OutboxItem(fila, tenant, doc, "BAJA", 0)));
        when(bajas.continuar(tenant, doc)).thenReturn(baja(ComunicacionBaja.EstadoBaja.ACEPTADA));
        worker.procesar();
        verify(outbox).completar(fila);

        when(bajas.continuar(tenant, doc)).thenReturn(baja(ComunicacionBaja.EstadoBaja.ENVIADA));
        worker.procesar();
        verify(outbox).reprogramar(fila, clock.instant().plus(DarDeBajaService.REINTENTO_CONSULTA), "98 - en proceso");

        // Sin ticket (ERROR_ENVIO) sigue el backoff de los envíos, no los 30 s de la consulta.
        when(bajas.continuar(tenant, doc)).thenReturn(baja(ComunicacionBaja.EstadoBaja.ERROR_ENVIO));
        worker.procesar();
        verify(outbox).reprogramar(fila, Backoff.siguiente(1, clock.instant()), "timeout");
    }
}
