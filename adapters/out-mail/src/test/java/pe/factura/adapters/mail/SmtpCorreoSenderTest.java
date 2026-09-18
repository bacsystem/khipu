package pe.factura.adapters.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import pe.factura.application.port.out.Adjunto;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpCorreoSenderTest {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    SmtpCorreoSender sender = new SmtpCorreoSender(mailSender, "no-responder@factura.pe");

    @Test void construyeYEnviaElMensaje() {
        sender.enviar("cliente@empresa.pe", "Restablecer contraseña", "Enlace: https://portal/restablecer/abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage m = captor.getValue();
        assertThat(m.getFrom()).isEqualTo("no-responder@factura.pe");
        assertThat(m.getTo()).containsExactly("cliente@empresa.pe");
        assertThat(m.getSubject()).isEqualTo("Restablecer contraseña");
        assertThat(m.getText()).contains("https://portal/restablecer/abc");
    }

    @Test void conAdjuntosArmaUnMimeMultipartConCadaArchivo() throws Exception {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);

        sender.enviar("cliente@empresa.pe", "Factura F001-1 - EMPRESA SAC", "Adjuntamos la factura.",
                List.of(new Adjunto("20100066603-01-F001-1.pdf", "application/pdf", "%PDF".getBytes()),
                        new Adjunto("20100066603-01-F001-1.xml", "application/xml", "<Invoice/>".getBytes())));

        verify(mailSender).send(mime);
        mime.saveChanges();
        assertThat(mime.getSubject()).isEqualTo("Factura F001-1 - EMPRESA SAC");
        assertThat(mime.getFrom()[0].toString()).isEqualTo("no-responder@factura.pe");
        assertThat(nombresDeAdjuntos((Multipart) mime.getContent())).containsExactly("20100066603-01-F001-1.pdf", "20100066603-01-F001-1.xml");
    }

    /** El helper anida multipart/mixed → multipart/related → texto; los adjuntos cuelgan del mixed con su fileName. */
    private static List<String> nombresDeAdjuntos(Multipart mp) throws Exception {
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart p = mp.getBodyPart(i);
            if (p.getContent() instanceof Multipart anidado) nombres.addAll(nombresDeAdjuntos(anidado));
            else if (p.getFileName() != null) nombres.add(p.getFileName());
        }
        return nombres;
    }
}
