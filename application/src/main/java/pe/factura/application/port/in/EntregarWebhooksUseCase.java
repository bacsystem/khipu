package pe.factura.application.port.in;

import java.time.Duration;

/** Manda las entregas de webhooks que están por vencer (#19) y borra el log viejo. Lo corre el trabajo programado. */
public interface EntregarWebhooksUseCase {
    /** Cuánto se guarda el log de entregas cerradas. */
    Duration RETENCION = Duration.ofDays(30);

    /** Procesa un lote; devuelve cuántas entregas intentó. */
    int entregar();

    /** Borra las entregas cerradas más viejas que {@link #RETENCION}; devuelve cuántas. */
    long limpiar();
}
