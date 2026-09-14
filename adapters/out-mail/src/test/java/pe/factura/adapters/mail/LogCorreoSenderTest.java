package pe.factura.adapters.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class LogCorreoSenderTest {
    @Test void noLanzaAunqueNoHayaSmtp() {
        LogCorreoSender sender = new LogCorreoSender();
        assertThatCode(() -> sender.enviar("a@b.pe", "Asunto", "Cuerpo")).doesNotThrowAnyException();
    }
}
