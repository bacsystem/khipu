package pe.factura.adapters.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import pe.factura.application.port.in.AvisarBoletasSinInformarUseCase;

/**
 * Cada hora avisa por correo a los clientes que tienen boletas sin informar a SUNAT (#356). El aviso sale el día que una boleta llega al 5.º sin informarse y otra vez cuando la
 * más antigua llega al 7.º; el registro de lo enviado impide repetirlo, así que correr cada hora no manda más correos: solo hace que el aviso llegue el mismo día en que corresponde.
 */
@Slf4j
@RequiredArgsConstructor
public class AvisarBoletasSinInformarWorker {
    private final AvisarBoletasSinInformarUseCase avisos;
    private final String urlPortal;

    @Scheduled(fixedDelayString = "${app.avisos.boletas.intervalo-ms:3600000}", initialDelayString = "${app.avisos.boletas.inicial-ms:120000}")
    public void tick() {
        try {
            AvisarBoletasSinInformarUseCase.Resultado r = avisos.avisar(urlPortal);
            if (r.enviados() > 0) log.info("{} aviso(s) de boletas sin informar enviado(s)", r.enviados());
            for (String fallo : r.fallos()) log.warn("No se pudo avisar de boletas sin informar a {}; se reintenta en la siguiente pasada", fallo);
        } catch (Exception e) {
            log.error("Fallo al avisar de las boletas sin informar", e);
        }
    }
}
