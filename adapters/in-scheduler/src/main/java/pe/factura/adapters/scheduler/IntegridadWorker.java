package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.VerificarIntegridadUseCase;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Verificación periódica de integridad del storage (#38): una vez al día revisa los comprobantes de los últimos
 * {@code app.integridad.dias} días (por defecto 7, así cada uno se revisa varias veces mientras es reciente) y deja en el
 * log un ERROR por cada XML o CDR faltante o alterado. Es de solo lectura: reparar es tarea del operador (README §Storage).
 */
@Slf4j
@RequiredArgsConstructor
public class IntegridadWorker {
    private final VerificarIntegridadUseCase integridad;
    private final Clock clock;
    private final int dias;

    @Scheduled(fixedDelayString = "${app.integridad.intervalo-ms:86400000}", initialDelayString = "${app.integridad.inicial-ms:300000}")
    public void tick() {
        LocalDate hoy = LocalDate.now(clock);
        try {
            var informe = integridad.verificar(hoy.minusDays(Math.max(dias, 1) - 1L), hoy);
            if (informe.limpio()) log.info("Integridad del storage: {} comprobantes verificados ({}..{}), sin problemas", informe.verificados(), informe.desde(), informe.hasta());
            else informe.problemas().forEach(p -> log.error("Integridad del storage: {} en {} (tenant {}, comprobante {}): {}", p.tipo(), p.nombreArchivo(), p.tenantId(), p.comprobanteId(), p.detalle()));
        } catch (Exception e) {
            log.error("Fallo de la verificación de integridad del storage", e);
        }
    }
}
