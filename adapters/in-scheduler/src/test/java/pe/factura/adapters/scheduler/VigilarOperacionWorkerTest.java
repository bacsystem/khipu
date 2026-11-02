package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.VigilarOperacionUseCase.Resultado;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** #341: una pasada del vigilante que falla (la base caída, por ejemplo) no mata el trabajo programado: la siguiente vuelve a intentar. */
class VigilarOperacionWorkerTest {
    @Test void cadaPasadaLlamaAlVigilante() {
        AtomicInteger llamadas = new AtomicInteger();
        VigilarOperacionWorker worker = new VigilarOperacionWorker(() -> { llamadas.incrementAndGet(); return new Resultado(1, 0, List.of("CORREO_DESHABILITADO")); });

        worker.tick();
        worker.tick();

        assertThat(llamadas).hasValue(2);
    }

    @Test void unaExcepcionNoSePropagaYLaSiguientePasadaCorre() {
        AtomicInteger llamadas = new AtomicInteger();
        VigilarOperacionWorker worker = new VigilarOperacionWorker(() -> {
            if (llamadas.incrementAndGet() == 1) throw new IllegalStateException("base caída");
            return new Resultado(0, 0, List.of());
        });

        assertThatCode(worker::tick).doesNotThrowAnyException();
        worker.tick();

        assertThat(llamadas).hasValue(2);
    }
}
