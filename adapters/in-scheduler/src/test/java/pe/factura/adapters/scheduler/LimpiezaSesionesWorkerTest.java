package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** #611: la limpieza corre sola cada hora; un fallo se registra y no la detiene, y el log lleva solo el conteo. */
@ExtendWith(OutputCaptureExtension.class)
class LimpiezaSesionesWorkerTest {
    @Test void unaExcepcionSeRegistraYNoSePropaga(CapturedOutput salida) {
        var worker = new LimpiezaSesionesWorker(() -> { throw new IllegalStateException("la base no responde"); });

        assertThatCode(worker::tick).doesNotThrowAnyException();
        assertThat(salida.getAll()).contains("Fallo de la limpieza de sesiones vencidas");
    }

    @Test void registraSoloCuantasBorro(CapturedOutput salida) {
        new LimpiezaSesionesWorker(() -> 42).tick();

        assertThat(salida.getAll()).contains("42 sesion(es) vencida(s) borrada(s)");
    }

    @Test void sinNadaQueBorrarNoEscribeNada(CapturedOutput salida) {
        new LimpiezaSesionesWorker(() -> 0).tick();

        assertThat(salida.getAll()).doesNotContain("borrada");
    }
}
