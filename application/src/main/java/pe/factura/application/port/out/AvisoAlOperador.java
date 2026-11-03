package pe.factura.application.port.out;

/**
 * Un aviso suelto al operador de la plataforma (#581), por los canales de alertas configurados ({@code ALERTAS_EMAIL}, {@code ALERTAS_WEBHOOK_URL}). Es para
 * hechos que el operador tiene que atender, no para problemas que se repiten: esos los vigila {@code VigilarOperacionService}, que lleva su estado.
 */
public interface AvisoAlOperador {
    /** Nunca lanza: un canal caído no debe hacer fallar lo que se estaba haciendo. Lo que no sale, queda en el log. */
    void avisar(String asunto, String cuerpo);
}
