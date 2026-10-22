package pe.factura.adapters.mail;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoNoEnviadoException;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpCorreoSenderTest {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    AtomicReference<RemitenteDeCorreo> remitente = new AtomicReference<>(new RemitenteDeCorreo(null, "no-responder@factura.pe", null));
    SmtpCorreoSender sender = new SmtpCorreoSender(mailSender, remitente::get);

    /** Un mensaje nuevo por cada llamada a {@code createMimeMessage}, como el sender de verdad. */
    List<MimeMessage> creados = new ArrayList<>();

    {
        when(mailSender.createMimeMessage()).thenAnswer(i -> {
            MimeMessage m = new MimeMessage(Session.getInstance(new Properties()));
            creados.add(m);
            return m;
        });
    }

    MimeMessage ultimo() throws Exception {
        MimeMessage m = creados.get(creados.size() - 1);
        m.saveChanges();
        return m;
    }

    @Test void construyeYEnviaElMensaje() throws Exception {
        sender.enviar("cliente@empresa.pe", "Restablecer contraseña", "Enlace: https://portal/restablecer/abc");

        MimeMessage m = ultimo();
        verify(mailSender).send(m);
        assertThat(m.getFrom()[0].toString()).isEqualTo("no-responder@factura.pe");
        assertThat(m.getAllRecipients()[0].toString()).isEqualTo("cliente@empresa.pe");
        assertThat(m.getSubject()).isEqualTo("Restablecer contraseña");
        assertThat(m.getContent().toString()).contains("https://portal/restablecer/abc");
        assertThat(m.getReplyTo()[0].toString()).as("sin respuesta aparte, las respuestas vuelven al remitente").isEqualTo("no-responder@factura.pe");
    }

    @Test void sinAdjuntosElCorreoEsTextoPlano() throws Exception {
        sender.enviar("cliente@empresa.pe", "Hola", "Texto");

        assertThat(ultimo().getContentType()).startsWith("text/plain");
    }

    @Test void conAdjuntosArmaUnMimeMultipartConCadaArchivo() throws Exception {
        sender.enviar("cliente@empresa.pe", "Factura F001-1 - EMPRESA SAC", "Adjuntamos la factura.",
                List.of(new Adjunto("20100066603-01-F001-1.pdf", "application/pdf", "%PDF".getBytes()),
                        new Adjunto("20100066603-01-F001-1.xml", "application/xml", "<Invoice/>".getBytes())));

        MimeMessage mime = ultimo();
        verify(mailSender).send(mime);
        assertThat(mime.getSubject()).isEqualTo("Factura F001-1 - EMPRESA SAC");
        assertThat(mime.getFrom()[0].toString()).isEqualTo("no-responder@factura.pe");
        assertThat(nombresDeAdjuntos((Multipart) mime.getContent())).containsExactly("20100066603-01-F001-1.pdf", "20100066603-01-F001-1.xml");
    }

    @Test void entregaDeVerdad() {
        assertThat(sender.entregaDeVerdad()).isTrue();
    }

    // --- el remitente editable (#199) ---------------------------------------------------------------------------------------------------

    @Test void conNombreElRemitenteSaleConSuNombre() throws Exception {
        remitente.set(new RemitenteDeCorreo("khipu", "avisos@khipu.pe", null));

        sender.enviar("cliente@empresa.pe", "Hola", "Texto");

        InternetAddress de = (InternetAddress) ultimo().getFrom()[0];
        assertThat(de.getAddress()).isEqualTo("avisos@khipu.pe");
        assertThat(de.getPersonal()).isEqualTo("khipu");
    }

    @Test void unNombreConTildesViajaCodificadoYSeLeeIgual() throws Exception {
        remitente.set(new RemitenteDeCorreo("Facturación Perú · khipu", "avisos@khipu.pe", null));

        sender.enviar("cliente@empresa.pe", "Hola", "Texto");

        MimeMessage m = ultimo();
        assertThat(((InternetAddress) m.getFrom()[0]).getPersonal()).isEqualTo("Facturación Perú · khipu");
        assertThat(m.getHeader("From", null)).as("en la cabecera va codificado, no con bytes sueltos").contains("=?UTF-8?");
    }

    @Test void lasRespuestasPuedenIrAOtraDireccion() throws Exception {
        remitente.set(new RemitenteDeCorreo("khipu", "no-responder@khipu.pe", "soporte@khipu.pe"));

        sender.enviar("cliente@empresa.pe", "Hola", "Texto");

        MimeMessage m = ultimo();
        assertThat(m.getFrom()[0].toString()).contains("no-responder@khipu.pe");
        assertThat(m.getReplyTo()).hasSize(1);
        assertThat(((InternetAddress) m.getReplyTo()[0]).getAddress()).isEqualTo("soporte@khipu.pe");
    }

    @Test void elRemitenteSeLeeEnCadaEnvioYUnCambioValeParaElSiguiente() throws Exception {
        sender.enviar("a@empresa.pe", "Uno", "Texto");
        assertThat(((InternetAddress) ultimo().getFrom()[0]).getAddress()).isEqualTo("no-responder@factura.pe");

        remitente.set(new RemitenteDeCorreo(null, "nuevo@khipu.pe", null));
        sender.enviar("b@empresa.pe", "Dos", "Texto");

        assertThat(((InternetAddress) ultimo().getFrom()[0]).getAddress()).isEqualTo("nuevo@khipu.pe");
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test void tambienConAdjuntosSaleConElRemitenteVigente() throws Exception {
        remitente.set(new RemitenteDeCorreo("khipu", "avisos@khipu.pe", "soporte@khipu.pe"));

        sender.enviar("cliente@empresa.pe", "Factura", "Adjunto", List.of(new Adjunto("f.pdf", "application/pdf", "%PDF".getBytes())));

        MimeMessage m = ultimo();
        assertThat(((InternetAddress) m.getFrom()[0]).getPersonal()).isEqualTo("khipu");
        assertThat(((InternetAddress) m.getReplyTo()[0]).getAddress()).isEqualTo("soporte@khipu.pe");
    }

    /**
     * Quien llama puede convertir el fallo en un «no salió» (el alta asistida, #188): si el adaptador no lo registra, la causa
     * —credenciales, buzón rechazado, SMTP caído— se pierde. Se registra el destinatario y la causa, nunca el cuerpo: puede llevar
     * un enlace de un solo uso.
     */
    @Test void unFalloDeEnvioSeRegistraConSuCausaYSePropaga() {
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));
        Logger logger = (Logger) LoggerFactory.getLogger(SmtpCorreoSender.class);
        ListAppender<ILoggingEvent> registro = new ListAppender<>();
        registro.start();
        logger.addAppender(registro);
        try {
            assertThatThrownBy(() -> sender.enviar("cliente@empresa.pe", "Bienvenida", "Enlace secreto: /restablecer/token-secreto"))
                    .isInstanceOf(CorreoNoEnviadoException.class).hasCauseInstanceOf(MailSendException.class);
        } finally {
            logger.detachAppender(registro);
        }

        assertThat(registro.list).hasSize(1);
        ILoggingEvent evento = registro.list.get(0);
        assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
        // #325: el destinatario va enmascarado, nunca entero.
        assertThat(evento.getFormattedMessage()).contains("c***@empresa.pe").doesNotContain("cliente@empresa.pe").doesNotContain("token-secreto");
        assertThat(evento.getThrowableProxy()).as("la causa va con el registro").isNotNull();
        assertThat(evento.getThrowableProxy().getMessage()).contains("SMTP caído");
    }

    /** #325-H3: lo que sube es la clase de fallo; el host y el puerto internos del SMTP de la plataforma quedan en la causa, que solo va al log. */
    @Test void elFalloQueSubeNoDiceElHostInternoSinoLaClaseDeFallo() {
        doThrow(new MailSendException("Mail server connection failed; nested exception is com.sun.mail.util.MailConnectException: Couldn't connect to host, port: smtp.interno.khipu:587",
                new java.net.ConnectException("Connection refused: smtp.interno.khipu:587"))).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.enviar("cliente@empresa.pe", "Factura", "cuerpo", List.of()))
                .isInstanceOf(CorreoNoEnviadoException.class)
                .hasMessage("No se pudo conectar con el servidor de correo")
                .hasMessageNotContaining("smtp.interno")
                .extracting("motivo").isEqualTo(CorreoNoEnviadoException.Motivo.CONEXION);
    }

    @Test void clasificaAutenticacionDestinatarioYElResto() throws Exception {
        assertThat(SmtpCorreoSender.motivoDe(new org.springframework.mail.MailAuthenticationException("535 bad creds"))).isEqualTo(CorreoNoEnviadoException.Motivo.AUTENTICACION);
        var rechazado = new jakarta.mail.SendFailedException("550 no such user", null, new jakarta.mail.Address[0], new jakarta.mail.Address[0], new jakarta.mail.Address[]{new InternetAddress("x@y.pe")});
        assertThat(SmtpCorreoSender.motivoDe(new MailSendException("falló", rechazado))).isEqualTo(CorreoNoEnviadoException.Motivo.DESTINATARIO_RECHAZADO);
        assertThat(SmtpCorreoSender.motivoDe(new MailSendException("algo raro"))).isEqualTo(CorreoNoEnviadoException.Motivo.OTRO);
        assertThat(SmtpCorreoSender.enmascarado("ana.perez@cliente.pe")).isEqualTo("a***@cliente.pe");
        assertThat(SmtpCorreoSender.enmascarado("sin-arroba")).isEqualTo("***");
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
