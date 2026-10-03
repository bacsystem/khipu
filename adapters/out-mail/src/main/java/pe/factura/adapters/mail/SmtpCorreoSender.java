package pe.factura.adapters.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
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
        enviarRegistrandoFallos(para, () -> mailSender.send(mensaje));
    }

    @Override public boolean entregaDeVerdad() { return true; }

    /**
     * Quien llama puede convertir el fallo en un «no salió» sin causa (el alta asistida, #188), así que la causa se registra aquí,
     * donde se conoce. Va el destinatario, nunca el cuerpo: puede llevar un enlace de un solo uso.
     */
    private void enviarRegistrandoFallos(String para, Runnable envio) {
        try {
            envio.run();
        } catch (MailException e) {
            log.error("No se pudo enviar el correo a {}", para, e);
            throw e;
        }
    }

    @Override public void enviar(String para, String asunto, String cuerpoTexto, List<Adjunto> adjuntos) {
        MimeMessage mensaje = mailSender.createMimeMessage();
        try {
            MimeMessageHelper h = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            h.setFrom(remitente);
            h.setTo(para);
            h.setSubject(asunto);
            h.setText(cuerpoTexto, false);
            for (Adjunto a : adjuntos) h.addAttachment(a.nombre(), new ByteArrayResource(a.contenido()), a.tipoContenido());
        } catch (MessagingException e) {
            throw new IllegalStateException("No se pudo construir el correo para " + para, e);
        }
        enviarRegistrandoFallos(para, () -> mailSender.send(mensaje));
    }
}
