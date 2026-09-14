package pe.factura.adapters.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import pe.factura.application.port.out.CorreoSender;

@RequiredArgsConstructor
public class SmtpCorreoSender implements CorreoSender {
    private final JavaMailSender mailSender;
    private final String remitente;

    @Override public void enviar(String para, String asunto, String cuerpoTexto) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(para);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpoTexto);
        mailSender.send(mensaje);
    }
}
