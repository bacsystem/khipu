package pe.factura.application.port.out;

public interface CorreoSender {
    void enviar(String para, String asunto, String cuerpoTexto);
}
