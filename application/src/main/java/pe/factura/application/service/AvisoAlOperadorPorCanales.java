package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.factura.application.port.out.AvisoAlOperador;
import pe.factura.application.port.out.CanalDeAlertas;

import java.util.List;

/**
 * El aviso suelto al operador (#581) por los canales de alertas que estén configurados. Siempre queda en el log (nivel INFO: es un hecho a atender, no una
 * falla), así que sin ningún canal configurado Railway lo muestra igual. Un canal que falla se registra y no corta a los demás ni al llamador.
 */
@Slf4j
@RequiredArgsConstructor
public class AvisoAlOperadorPorCanales implements AvisoAlOperador {
    private final List<CanalDeAlertas> canales;

    @Override public void avisar(String asunto, String cuerpo) {
        log.info("{}: {}", asunto, cuerpo);
        for (CanalDeAlertas canal : canales) {
            if (!canal.configurado()) continue;
            try {
                canal.enviar(asunto, cuerpo);
            } catch (RuntimeException e) {
                log.error("El canal de alertas {} no pudo entregar «{}»", canal.nombre(), asunto, e);
            }
        }
    }
}
