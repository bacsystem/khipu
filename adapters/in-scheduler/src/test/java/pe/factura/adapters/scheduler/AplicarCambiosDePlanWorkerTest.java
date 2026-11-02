package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AplicarCambiosDePlanUseCase;
import pe.factura.application.port.in.AplicarCambiosDePlanUseCase.Resultado;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** El trabajo que pasa a vigente la bajada de plan cuando llega su fecha (#191): llama al caso de uso en cada pasada y nunca se cae. */
class AplicarCambiosDePlanWorkerTest {
    @Test void cadaPasadaAplicaLosCambiosVencidos() {
        AtomicInteger llamadas = new AtomicInteger();
        AplicarCambiosDePlanWorker worker = new AplicarCambiosDePlanWorker(() -> { llamadas.incrementAndGet(); return new Resultado(2, 0); });

        worker.tick();
        worker.tick();

        assertThat(llamadas.get()).isEqualTo(2);
    }

    /** Si una pasada falla, el trabajo no se muere: la siguiente reintenta (si lanzara, el planificador de Spring seguiría igual, pero el error quedaría sin registrar). */
    @Test void unaPasadaQueFallaNoLanzaYLaSiguienteSigue() {
        AtomicInteger llamadas = new AtomicInteger();
        AplicarCambiosDePlanUseCase uso = () -> {
            if (llamadas.incrementAndGet() == 1) throw new IllegalStateException("la base se cayó");
            return new Resultado(1, 0);
        };
        AplicarCambiosDePlanWorker worker = new AplicarCambiosDePlanWorker(uso);

        assertThatCode(worker::tick).doesNotThrowAnyException();
        assertThatCode(worker::tick).doesNotThrowAnyException();

        assertThat(llamadas.get()).isEqualTo(2);
    }

    @Test void unaPasadaConFallidosNoLanza() {
        AplicarCambiosDePlanWorker worker = new AplicarCambiosDePlanWorker(() -> new Resultado(3, 2));

        assertThatCode(worker::tick).doesNotThrowAnyException();
    }
}
