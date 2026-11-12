package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** #634: la limpieza corre sola cada hora; un fallo se registra y no la detiene, y el log lleva solo el conteo. */
@ExtendWith(OutputCaptureExtension.class)
class LimpiezaTokensDeAccesoWorkerTest {
    @Test void unaExcepcionSeRegistraYNoSePropaga(CapturedOutput salida) {
        var worker = new LimpiezaTokensDeAccesoWorker(() -> { throw new IllegalStateException("la base no responde"); });

        assertThatCode(worker::tick).doesNotThrowAnyException();
        assertThat(salida.getAll()).contains("Fallo de la limpieza de tokens de acceso vencidos");
    }

    @Test void registraSoloCuantosBorro(CapturedOutput salida) {
        new LimpiezaTokensDeAccesoWorker(() -> 42).tick();

        assertThat(salida.getAll()).contains("42 token(s) de acceso vencido(s) borrado(s)");
    }

    @Test void sinNadaQueBorrarNoEscribeNada(CapturedOutput salida) {
        new LimpiezaTokensDeAccesoWorker(() -> 0).tick();

        assertThat(salida.getAll()).doesNotContain("borrado");
    }
}
