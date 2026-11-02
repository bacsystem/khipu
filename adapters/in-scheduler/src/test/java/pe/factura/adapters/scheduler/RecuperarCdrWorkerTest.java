package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pe.factura.application.port.in.RecuperarCdrUseCase;
import pe.factura.application.port.in.RecuperarCdrUseCase.Barrido;
import pe.factura.application.port.in.RecuperarCdrUseCase.Fallo;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.TipoAfectacionIgv;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El barrido horario de CDR no deja fallos mudos: cada uno queda en el log con su comprobante, y un fault definitivo con su traza (#357). */
@ExtendWith(OutputCaptureExtension.class)
class RecuperarCdrWorkerTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    static Comprobante factura(long numero) {
        Comprobante c = Comprobante.factura(UUID.randomUUID(), "F001", LocalDate.of(2026, 9, 13), "PEN", "0101", new Receptor("6", "20601234565", "CLIENTE SAC", null),
                List.of(new Item("P1", "Prod", "NIU", BigDecimal.ONE, new BigDecimal("118.00"), TipoAfectacionIgv.GRAVADO))).crear(CLOCK);
        c.asignarNumero(numero, "20100066603");
        return c;
    }

    @Test void unFaultDefinitivoQuedaEnElLogConSuComprobanteYSuTraza(CapturedOutput log) {
        Comprobante c = factura(7);
        RecuperarCdrUseCase cdrs = new RecuperarCdrUseCase() {
            public Comprobante recuperar(UUID t, UUID id) { throw new AssertionError(); }
            public Barrido recuperarPendientes() {
                return new Barrido(List.of(), List.of(new Fallo(c, new SunatRechazoException("0151", "El archivo ZIP no contiene el comprobante"))));
            }
        };

        new RecuperarCdrWorker(cdrs).tick();

        assertThat(log.getAll()).contains("No se recuperó el CDR de " + c.nombreArchivo()).contains("SunatRechazoException").contains("0151");
    }

    @Test void sunatCaidoEsUnaLineaPorComprobanteSinTraza(CapturedOutput log) {
        Comprobante c = factura(8);
        RecuperarCdrUseCase cdrs = new RecuperarCdrUseCase() {
            public Comprobante recuperar(UUID t, UUID id) { throw new AssertionError(); }
            public Barrido recuperarPendientes() { return new Barrido(List.of(), List.of(new Fallo(c, new SunatTransientException("0109", "El sistema no puede responder")))); }
        };

        new RecuperarCdrWorker(cdrs).tick();

        assertThat(log.getAll()).contains("No se recuperó el CDR de " + c.nombreArchivo() + ": El sistema no puede responder").doesNotContain("SunatTransientException");
    }

    /** Revisión de #357 (H4): lo que el dominio no admite (un estado, un comprobante sin firma) también es lo esperado: una línea, sin traza. */
    @Test void unaReglaDelDominioEsUnaLineaPorComprobanteSinTraza(CapturedOutput log) {
        Comprobante c = factura(9);
        RecuperarCdrUseCase cdrs = new RecuperarCdrUseCase() {
            public Comprobante recuperar(UUID t, UUID id) { throw new AssertionError(); }
            public Barrido recuperarPendientes() { return new Barrido(List.of(), List.of(new Fallo(c, new DomainException("SIN_FIRMA", "El comprobante aún no está firmado")))); }
        };

        new RecuperarCdrWorker(cdrs).tick();

        assertThat(log.getAll()).contains("No se recuperó el CDR de " + c.nombreArchivo() + ": El comprobante aún no está firmado").doesNotContain("DomainException");
    }
}
