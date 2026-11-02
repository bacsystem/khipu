package pe.factura.adapters.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoNoEnviadoException;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * Manda los correos por SMTP. El remitente se lee **en cada envío** (#199): un administrador lo puede cambiar sin reiniciar y el cambio vale para el correo siguiente. El nombre
 * viaja codificado en la cabecera (un «Facturación Perú» con tilde no se corrompe) y las respuestas pueden ir a otra dirección.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpCorreoSender implements CorreoSender {
    private final JavaMailSender mailSender;
    private final Supplier<RemitenteDeCorreo> remitente;

    @Override public void enviar(String para, String asunto, String cuerpoTexto) {
        enviar(para, asunto, cuerpoTexto, List.of());
    }

    @Override public boolean entregaDeVerdad() { return true; }

    /**
     * Quien llama puede convertir el fallo en un «no salió» sin causa (el alta asistida, #188), así que la causa se registra aquí,
     * donde se conoce. Va el destinatario enmascarado (#325: es un dato personal del cliente de un cliente), nunca el cuerpo: puede llevar un enlace de un
     * solo uso. Hacia arriba sale un {@link CorreoNoEnviadoException} con la clase de fallo: el detalle (host, respuesta del servidor) queda en la causa.
     */
    private void enviarRegistrandoFallos(String para, Runnable envio) {
        try {
            envio.run();
        } catch (MailException e) {
            log.error("No se pudo enviar el correo a {}", enmascarado(para), e);
            throw new CorreoNoEnviadoException(motivoDe(e), e);
        }
    }

    /** {@code c***@empresa.pe}: basta para reconocerlo en el log sin dejarlo entero. */
    static String enmascarado(String email) {
        if (email == null) return "(sin destinatario)";
        int arroba = email.indexOf('@');
        if (arroba <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(arroba);
    }

    /** La clase de fallo, mirando toda la cadena de causas: lo que JavaMail lanza por debajo del envoltorio de Spring. */
    static CorreoNoEnviadoException.Motivo motivoDe(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof org.springframework.mail.MailAuthenticationException || t instanceof jakarta.mail.AuthenticationFailedException)
                return CorreoNoEnviadoException.Motivo.AUTENTICACION;
            if (t instanceof jakarta.mail.SendFailedException s && s.getInvalidAddresses() != null && s.getInvalidAddresses().length > 0)
                return CorreoNoEnviadoException.Motivo.DESTINATARIO_RECHAZADO;
            if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException || t instanceof java.net.SocketTimeoutException
                    || t.getClass().getSimpleName().equals("MailConnectException"))
                return CorreoNoEnviadoException.Motivo.CONEXION;
            if (t instanceof org.springframework.mail.MailSendException m)
                for (Exception f : m.getFailedMessages().values()) {
                    CorreoNoEnviadoException.Motivo interno = motivoDe(f);
                    if (interno != CorreoNoEnviadoException.Motivo.OTRO) return interno;
                }
        }
        return CorreoNoEnviadoException.Motivo.OTRO;
    }

    @Override public void enviar(String para, String asunto, String cuerpoTexto, List<Adjunto> adjuntos) {
        RemitenteDeCorreo de = remitente.get();
        MimeMessage mensaje = mailSender.createMimeMessage();
        try {
            MimeMessageHelper h = new MimeMessageHelper(mensaje, !adjuntos.isEmpty(), StandardCharsets.UTF_8.name());
            if (de.nombre() == null) h.setFrom(de.email());
            else h.setFrom(de.email(), de.nombre());
            if (de.responderA() != null) h.setReplyTo(de.responderA());
            h.setTo(para);
            h.setSubject(asunto);
            h.setText(cuerpoTexto, false);
            for (Adjunto a : adjuntos) h.addAttachment(a.nombre(), new ByteArrayResource(a.contenido()), a.tipoContenido());
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("No se pudo construir el correo para " + para, e);
        }
        enviarRegistrandoFallos(para, () -> mailSender.send(mensaje));
    }
}
