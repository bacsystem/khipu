package pe.factura.adapters.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
