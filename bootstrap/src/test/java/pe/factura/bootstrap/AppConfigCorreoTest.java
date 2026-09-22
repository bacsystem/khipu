package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import pe.factura.adapters.mail.LogCorreoSender;
import pe.factura.adapters.mail.SmtpCorreoSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El correo es opcional: apagado se escribe en el log, encendido exige SMTP de verdad. Spring crea el bean
 * JavaMailSender aunque `spring.mail.host` esté vacío, así que comprobar solo que el bean exista dejaría pasar una
 * configuración incompleta y cada correo fallaría recién al enviarse (recuperación de contraseña, comprobantes).
 */
class AppConfigCorreoTest {
    private static AppProperties con(boolean mailHabilitado) {
        return new AppProperties("multi", "clave", "pepper", "plataforma", "jwt-secret", "http://localhost:3000",
                null, null, null, new AppProperties.Mail(mailHabilitado, "no-responder@khipu.pe"), null, "America/Lima");
    }

    private static ObjectProvider<JavaMailSender> proveedor(JavaMailSender sender) {
        return new ObjectProvider<>() {
            @Override public JavaMailSender getObject() { return sender; }
            @Override public JavaMailSender getObject(Object... args) { return sender; }
            @Override public JavaMailSender getIfAvailable() { return sender; }
            @Override public JavaMailSender getIfUnique() { return sender; }
        };
    }

    @Test void correoApagadoEscribeEnElLog() {
        assertThat(new AppConfig().correoSender(con(false), proveedor(null), ""))
                .isInstanceOf(LogCorreoSender.class);
    }

    @Test void correoEncendidoConHostEnviaPorSmtp() {
        assertThat(new AppConfig().correoSender(con(true), proveedor(new JavaMailSenderImpl()), "smtp.proveedor.com"))
                .isInstanceOf(SmtpCorreoSender.class);
    }

    /** Con el host vacío el bean existe igual: si no se valida, la app arranca y los correos fallan uno por uno. */
    @Test void correoEncendidoSinHostAbortaAlArrancar() {
        assertThatThrownBy(() -> new AppConfig().correoSender(con(true), proveedor(new JavaMailSenderImpl()), "  "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_HOST");
        assertThatThrownBy(() -> new AppConfig().correoSender(con(true), proveedor(null), "smtp.proveedor.com"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_HOST");
    }
}
