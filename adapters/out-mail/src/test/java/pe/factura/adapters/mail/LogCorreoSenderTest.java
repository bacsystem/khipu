package pe.factura.adapters.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LogCorreoSenderTest {
    @Test void noLanzaAunqueNoHayaSmtp() {
        LogCorreoSender sender = new LogCorreoSender();
        assertThatCode(() -> sender.enviar("a@b.pe", "Asunto", "Cuerpo")).doesNotThrowAnyException();
    }

    /** No lanza, pero tampoco entrega: quien necesita saber si el correo llegó (el alta asistida, #188) lo pregunta aquí. */
    @Test void diceQueNoEntregaDeVerdad() {
        assertThat(new LogCorreoSender().entregaDeVerdad()).isFalse();
    }
}
