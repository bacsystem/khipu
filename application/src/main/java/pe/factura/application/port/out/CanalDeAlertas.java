package pe.factura.application.port.out;

/**
 * Por dónde le llega una alerta al operador de la plataforma (#341): correo, un webhook de chat. Cada canal se configura por variable de entorno y, si no está configurado,
 * {@link #configurado()} responde {@code false} y el vigilante no lo usa.
 */
public interface CanalDeAlertas {
    String nombre();

    /** Si este canal tiene destino y puede entregar de verdad (el correo que solo escribe en el log no cuenta). */
    boolean configurado();

    /** Lanza si no pudo entregarla. */
    void enviar(String asunto, String cuerpo);
}
