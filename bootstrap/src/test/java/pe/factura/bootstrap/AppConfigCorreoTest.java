package pe.factura.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import pe.factura.adapters.mail.LogCorreoSender;
import pe.factura.adapters.mail.SmtpCorreoSender;
import pe.factura.application.port.out.RemitenteRepository;
import pe.factura.domain.plataforma.RemitenteDeCorreo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El correo es opcional: apagado se escribe en el log, encendido exige SMTP de verdad. Spring crea el bean
 * JavaMailSender aunque `spring.mail.host` esté vacío, así que comprobar solo que el bean exista dejaría pasar una
 * configuración incompleta y cada correo fallaría recién al enviarse (recuperación de contraseña, comprobantes).
 */
class AppConfigCorreoTest {
    static final RemitenteDeCorreo PREDETERMINADO = new RemitenteDeCorreo(null, "no-responder@khipu.pe", null);

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

    private static RemitenteRepository sinRemitente() {
        return new RemitenteRepository() {
            public Optional<Guardado> buscar() { return Optional.empty(); }
            public void guardar(RemitenteDeCorreo remitente, Instant ahora, UUID por) { throw new AssertionError(); }
            public boolean quitar() { throw new AssertionError(); }
        };
    }

    @Test void correoApagadoEscribeEnElLog() {
        assertThat(new AppConfig().correoSender(con(false), proveedor(null), "", sinRemitente(), PREDETERMINADO))
                .isInstanceOf(LogCorreoSender.class);
    }

    @Test void correoEncendidoConHostEnviaPorSmtp() {
        assertThat(new AppConfig().correoSender(con(true), proveedor(new JavaMailSenderImpl()), "smtp.proveedor.com", sinRemitente(), PREDETERMINADO))
                .isInstanceOf(SmtpCorreoSender.class);
    }

    /** Con el host vacío el bean existe igual: si no se valida, la app arranca y los correos fallan uno por uno. */
    @Test void correoEncendidoSinHostAbortaAlArrancar() {
        assertThatThrownBy(() -> new AppConfig().correoSender(con(true), proveedor(new JavaMailSenderImpl()), "  ", sinRemitente(), PREDETERMINADO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_HOST");
        assertThatThrownBy(() -> new AppConfig().correoSender(con(true), proveedor(null), "smtp.proveedor.com", sinRemitente(), PREDETERMINADO))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_HOST");
    }

    // --- el remitente editable (#199) ---------------------------------------------------------------------------------------------------

    @Test void sinRemitenteGuardadoSaleElDeLaConfiguracion() {
        assertThat(AppConfig.remitenteVigente(sinRemitente(), PREDETERMINADO)).isSameAs(PREDETERMINADO);
    }

    @Test void conRemitenteGuardadoSaleEseYNoElDeLaConfiguracion() {
        RemitenteDeCorreo fijado = new RemitenteDeCorreo("khipu", "avisos@khipu.pe", "soporte@khipu.pe");
        RemitenteRepository repo = new RemitenteRepository() {
            public Optional<Guardado> buscar() { return Optional.of(new Guardado(fijado, Instant.EPOCH)); }
            public void guardar(RemitenteDeCorreo remitente, Instant ahora, UUID por) { throw new AssertionError(); }
            public boolean quitar() { throw new AssertionError(); }
        };

        assertThat(AppConfig.remitenteVigente(repo, PREDETERMINADO)).isSameAs(fijado);
    }

    /** Un correo no deja de salir porque la base no contestó al leer el remitente. */
    @Test void siLaBaseFallaAlLeerElRemitenteSaleElDeLaConfiguracion() {
        RemitenteRepository caida = new RemitenteRepository() {
            public Optional<Guardado> buscar() { throw new IllegalStateException("base caída"); }
            public void guardar(RemitenteDeCorreo remitente, Instant ahora, UUID por) { throw new AssertionError(); }
            public boolean quitar() { throw new AssertionError(); }
        };

        assertThat(AppConfig.remitenteVigente(caida, PREDETERMINADO)).isSameAs(PREDETERMINADO);
    }

    @Test void elRemitenteDeLaConfiguracionValidoSeUsaTalCual() {
        assertThat(AppConfig.remitenteDeLaConfiguracion("no-responder@khipu.pe")).isEqualTo(new RemitenteDeCorreo(null, "no-responder@khipu.pe", null));
    }

    /** Un valor que antes se aceptaba (el servidor de correo lo entendía) no impide arrancar: se conserva tal cual. */
    @Test void unRemitenteDeLaConfiguracionEnFormatoViejoNoImpideArrancar() {
        assertThat(AppConfig.remitenteDeLaConfiguracion("khipu <no-responder@khipu.pe>")).isEqualTo(new RemitenteDeCorreo(null, "khipu <no-responder@khipu.pe>", null));
    }
}
