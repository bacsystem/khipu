package pe.factura.application.port.out;

import java.util.List;

public interface CorreoSender {
    void enviar(String para, String asunto, String cuerpoTexto);
    /** Mismo correo con archivos adjuntos (PDF, XML y CDR de un comprobante). */
    void enviar(String para, String asunto, String cuerpoTexto, List<Adjunto> adjuntos);

    /**
     * Si un {@code enviar} que no lanzó significa que el correo salió de verdad. Un adaptador que no entrega —el que lo escribe en
     * el log cuando no hay SMTP— debe responder {@code false}: quien informa «enviado» a una persona (el alta asistida, #188) lo
     * pregunta aquí en vez de suponerlo porque no hubo excepción.
     */
    default boolean entregaDeVerdad() { return true; }
}
