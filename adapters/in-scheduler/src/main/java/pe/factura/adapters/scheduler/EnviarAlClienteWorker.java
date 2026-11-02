package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.EnviarAlClienteUseCase;

/**
 * Cada 10 segundos manda a sus clientes los comprobantes aceptados que les toca (#325): el cliente recibe su factura poco después de que SUNAT la acepta. Una pasada que falla no
 * detiene las siguientes.
 */
@Slf4j
@RequiredArgsConstructor
public class EnviarAlClienteWorker {
    private final EnviarAlClienteUseCase envios;

    @Scheduled(fixedDelayString = "${app.envio-al-cliente.intervalo-ms:10000}", initialDelayString = "${app.envio-al-cliente.inicial-ms:30000}")
    public void tick() {
        try {
            int n = envios.enviarPendientes();
            if (n > 0) log.debug("{} envío(s) de comprobantes a clientes procesado(s)", n);
        } catch (Exception e) {
            log.error("Falló la pasada de envío de comprobantes a los clientes", e);
        }
    }
}
