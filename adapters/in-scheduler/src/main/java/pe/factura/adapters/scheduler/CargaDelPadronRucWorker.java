package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.CargarPadronRucUseCase;
import pe.factura.application.port.out.PadronRucRepository.CargaRegistrada;
import pe.factura.domain.DomainException;

/**
 * Una vez al día trae el padrón reducido de SUNAT a la base (#361). SUNAT lo publica de madrugada; la carga corre a las 04:30 hora de Lima por defecto
 * ({@code app.padron-ruc.cron}). Un fallo no tumba nada: el padrón anterior sigue sirviendo y el intento queda en {@code carga_padron_ruc}.
 */
@Slf4j
@RequiredArgsConstructor
public class CargaDelPadronRucWorker {
    private final CargarPadronRucUseCase carga;

    @Scheduled(cron = "${app.padron-ruc.cron:0 30 4 * * *}", zone = "America/Lima")
    public void tick() {
        try {
            CargaRegistrada r = carga.cargar();
            log.info("Padrón de RUC cargado desde {}: {} filas ({} nuevas, {} cambiadas, {} borradas, {} descartadas)", r.origen(), r.filas(), r.nuevas(), r.cambiadas(), r.borradas(), r.descartadas());
        } catch (DomainException e) {
            log.warn("No se cargó el padrón de RUC ({}): {}. Sigue sirviendo el anterior", e.codigo(), e.getMessage());
        } catch (Exception e) {
            log.error("Fallo la carga del padrón de RUC; sigue sirviendo el anterior", e);
        }
    }
}
