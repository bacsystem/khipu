package pe.factura.application.port.out;

import java.util.List;

public interface CorreoSender {
    void enviar(String para, String asunto, String cuerpoTexto);
    /** Mismo correo con archivos adjuntos (PDF, XML y CDR de un comprobante). */
    void enviar(String para, String asunto, String cuerpoTexto, List<Adjunto> adjuntos);
}
