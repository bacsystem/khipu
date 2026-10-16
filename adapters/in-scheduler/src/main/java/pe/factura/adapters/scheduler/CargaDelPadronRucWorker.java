package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.CargarPadronRucUseCase;
import pe.factura.application.port.out.PadronRucRepository.CargaRegistrada;
import pe.factura.domain.DomainException;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Una vez al día trae el padrón reducido de SUNAT a la base (#361). SUNAT lo publica de madrugada; la carga corre a las 04:30 hora de Lima por defecto
 * ({@code app.padron-ruc.cron}). Un fallo no tumba nada: el padrón anterior sigue sirviendo y el intento queda en {@code carga_padron_ruc}.
 * <p>
 * La carga dura minutos (descarga de ~400 MB y 18 millones de filas) y el planificador de Spring tiene un solo hilo: hecha en el hilo del {@code @Scheduled}, el outbox, el
 * barrido de plazos y la recuperación de CDR se detendrían todo ese rato. Por eso {@link #tick()} solo la encarga a {@code ejecutor} y vuelve.
 */
@Slf4j
@RequiredArgsConstructor
public class CargaDelPadronRucWorker {
    private final CargarPadronRucUseCase carga;
    private final Executor ejecutor;
    private final AtomicBoolean enCurso = new AtomicBoolean();

    @Scheduled(cron = "${app.padron-ruc.cron:0 30 4 * * *}", zone = "America/Lima")
    public void tick() {
        // Si la anterior sigue corriendo no se apila otra: la segunda terminaría en CARGA_EN_CURSO de todos modos, pero ocuparía el hilo para nada.
        if (!enCurso.compareAndSet(false, true)) {
            log.warn("La carga anterior del padrón de RUC sigue en curso; se omite esta");
            return;
        }
        try {
            ejecutor.execute(this::cargar);
        } catch (RuntimeException e) {
            enCurso.set(false);
            log.error("No se pudo encargar la carga del padrón de RUC", e);
        }
    }

    private void cargar() {
        try {
            CargaRegistrada r = carga.cargar();
            log.info("Padrón de RUC cargado desde {}: {} filas ({} nuevas, {} cambiadas, {} borradas, {} descartadas)", r.origen(), r.filas(), r.nuevas(), r.cambiadas(), r.borradas(), r.descartadas());
        } catch (DomainException e) {
            log.warn("No se cargó el padrón de RUC ({}): {}. Sigue sirviendo el anterior", e.codigo(), e.getMessage());
        } catch (Exception e) {
            log.error("Fallo la carga del padrón de RUC; sigue sirviendo el anterior", e);
        } finally {
            enCurso.set(false);
        }
    }
}
