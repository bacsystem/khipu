package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pe.factura.application.port.out.PadronRucRepository.CargaRegistrada;
import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** #361: la carga del padrón dura minutos y el planificador tiene un solo hilo; el worker la encarga a otro y vuelve. */
@ExtendWith(OutputCaptureExtension.class)
class CargaDelPadronRucWorkerTest {
    static final CargaRegistrada CARGA = new CargaRegistrada(Instant.parse("2026-10-15T09:30:00Z"), Instant.parse("2026-10-15T09:40:00Z"), true, "sunat", 100, 1, 2, 3, 4, null);

    @Test void tickVuelveSinEsperarQueTermineLaCarga() throws Exception {
        CountDownLatch empezo = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        var otroHilo = Executors.newSingleThreadExecutor();
        try {
            var worker = new CargaDelPadronRucWorker(() -> {
                empezo.countDown();
                // Con tope: si tick() corriera la carga en su propio hilo, esperaría esto y el assert de abajo fallaría en vez de colgar la prueba.
                try { soltar.await(4, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return CARGA;
            }, otroHilo);

            long t0 = System.nanoTime();
            worker.tick();
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)).as("tick no espera la carga").isLessThan(2000);
            assertThat(empezo.await(5, TimeUnit.SECONDS)).as("la carga sí empezó, en otro hilo").isTrue();
            soltar.countDown();
        } finally {
            otroHilo.shutdown();
            assertThat(otroHilo.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void mientrasUnaCargaSigueNoSeApilaOtra(CapturedOutput log) {
        List<Runnable> pendientes = new ArrayList<>();
        Executor encolar = pendientes::add;
        AtomicInteger cargas = new AtomicInteger();
        var worker = new CargaDelPadronRucWorker(() -> { cargas.incrementAndGet(); return CARGA; }, encolar);

        worker.tick();
        worker.tick();

        assertThat(pendientes).as("la segunda se omite mientras la primera no termina").hasSize(1);
        assertThat(log.getAll()).contains("sigue en curso");
        pendientes.get(0).run();
        assertThat(cargas).hasValue(1);

        worker.tick();
        assertThat(pendientes).as("terminada la primera, vuelve a poder correr").hasSize(2);
    }

    @Test void unaCargaQueFallaNoImpideLaSiguienteYQuedaEnElLog(CapturedOutput log) {
        List<Runnable> pendientes = new ArrayList<>();
        AtomicInteger intento = new AtomicInteger();
        var worker = new CargaDelPadronRucWorker(() -> {
            if (intento.incrementAndGet() == 1) throw new DomainException("PADRON_INCOMPLETO", "trae pocas filas");
            if (intento.get() == 2) throw new IllegalStateException("se cayó la base");
            return CARGA;
        }, pendientes::add);

        worker.tick(); pendientes.remove(0).run();
        assertThat(log.getAll()).contains("PADRON_INCOMPLETO").contains("Sigue sirviendo el anterior");
        worker.tick(); pendientes.remove(0).run();
        assertThat(log.getAll()).contains("se cayó la base");
        worker.tick(); pendientes.remove(0).run();
        assertThat(log.getAll()).contains("Padrón de RUC cargado desde sunat: 100 filas");
    }

    @Test void siElEjecutorRechazaLaTareaSePuedeVolverAIntentar(CapturedOutput log) {
        var worker = new CargaDelPadronRucWorker(() -> CARGA, tarea -> { throw new java.util.concurrent.RejectedExecutionException("lleno"); });

        worker.tick();
        worker.tick();

        assertThat(log.getAll()).contains("No se pudo encargar la carga").doesNotContain("sigue en curso");
    }
}
