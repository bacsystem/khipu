package pe.factura.application.port.out;

/** Hablar con el servidor de un integrador (#19): comprobar que la URL apunta a internet y no a la red interna de khipu, y entregarle un evento firmado. */
public interface DestinoDeWebhook {
    /** Lo que respondió el servidor ({@code http} 0 si no hubo respuesta) y, si falló, por qué en una línea. */
    record Respuesta(int http, String error) {
        public boolean exitosa() { return http >= 200 && http < 300; }
    }

    /** Lanza {@code WEBHOOK_INVALIDO} si el nombre del servidor no se resuelve o resuelve a una dirección interna (loopback, privada, enlace local). */
    void exigirDestinoPublico(String url);

    /** Un POST JSON con esas cabeceras, sin seguir redirecciones y con tope de tiempo. No lanza: un fallo de red es una {@link Respuesta} con error. */
    Respuesta entregar(String url, java.util.Map<String, String> cabeceras, String cuerpo);
}
