package pe.factura.adapters.scheduler;

import org.junit.jupiter.api.Test;
import pe.factura.application.port.in.AvisarBoletasSinInformarUseCase;
import pe.factura.application.port.in.AvisarBoletasSinInformarUseCase.Resultado;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** El trabajo que avisa de las boletas sin informar (#356): llama al caso de uso con la URL del portal en cada pasada y nunca se cae. */
class AvisarBoletasSinInformarWorkerTest {
    @Test void cadaPasadaAvisaConLaUrlDelPortal() {
        List<String> urls = new ArrayList<>();
        AvisarBoletasSinInformarWorker worker = new AvisarBoletasSinInformarWorker(url -> { urls.add(url); return new Resultado(1, List.of()); }, "https://app.khipu.pe");

        worker.tick();
        worker.tick();

        assertThat(urls).containsExactly("https://app.khipu.pe", "https://app.khipu.pe");
    }

    /** Si una pasada falla, el trabajo no se muere: la siguiente reintenta. */
    @Test void unaPasadaQueFallaNoLanzaYLaSiguienteSigue() {
        List<Integer> llamadas = new ArrayList<>();
        AvisarBoletasSinInformarUseCase uso = url -> {
            llamadas.add(1);
            if (llamadas.size() == 1) throw new IllegalStateException("la base se cayó");
            return new Resultado(0, List.of());
        };
        AvisarBoletasSinInformarWorker worker = new AvisarBoletasSinInformarWorker(uso, "https://app.khipu.pe");

        assertThatCode(worker::tick).doesNotThrowAnyException();
        assertThatCode(worker::tick).doesNotThrowAnyException();

        assertThat(llamadas).hasSize(2);
    }

    @Test void unaPasadaConFallosDeCorreoNoLanza() {
        AvisarBoletasSinInformarWorker worker = new AvisarBoletasSinInformarWorker(url -> new Resultado(1, List.of("20100066603 (ana@negocio.pe): SMTP caído")), "https://app.khipu.pe");

        assertThatCode(worker::tick).doesNotThrowAnyException();
    }
}
