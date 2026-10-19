package pe.factura.adapters.alertas;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los dos canales de alerta al operador (#341): el webhook (contra un servidor HTTP real) y el correo. */
class CanalesDeAlertasTest {
    HttpServer servidor;
    final List<String> cuerpos = new CopyOnWriteArrayList<>();
    final List<String> tipos = new CopyOnWriteArrayList<>();
    volatile int respuesta = 200;

    String levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/alertas", ex -> {
            cuerpos.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            tipos.add(ex.getRequestHeaders().getFirst("Content-Type"));
            ex.sendResponseHeaders(respuesta, -1);
            ex.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/alertas";
    }

    @AfterEach void parar() { if (servidor != null) servidor.stop(0); }

    @Test void elWebhookHaceUnPostJsonConElTextoParaSlackYDiscordYLosCamposSeparados() throws IOException {
        WebhookDeAlertas webhook = new WebhookDeAlertas(levantar());

        webhook.enviar("ALERTA · Outbox", "12 envíos\ncon \"comillas\" y \\barra");

        assertThat(webhook.configurado()).isTrue();
        assertThat(tipos).containsExactly("application/json; charset=utf-8");
        assertThat(cuerpos).singleElement().satisfies(c -> assertThat(c)
                .contains("\"text\":\"ALERTA · Outbox\\n12 envíos\\ncon \\\"comillas\\\" y \\\\barra\"")
                .contains("\"content\":\"ALERTA · Outbox\\n12 envíos")
                .contains("\"asunto\":\"ALERTA · Outbox\"")
                .contains("\"cuerpo\":\"12 envíos\\ncon \\\"comillas\\\" y \\\\barra\""));
    }

    @Test void unaRespuestaQueNoEs2xxEsUnFalloParaQueElVigilanteReintente() throws IOException {
        WebhookDeAlertas webhook = new WebhookDeAlertas(levantar());
        respuesta = 500;

        assertThatThrownBy(() -> webhook.enviar("a", "b")).isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP 500");
    }

    @Test void unDestinoInalcanzableEsUnFallo() {
        WebhookDeAlertas webhook = new WebhookDeAlertas("http://127.0.0.1:1/alertas");

        assertThatThrownBy(() -> webhook.enviar("a", "b")).isInstanceOf(IllegalStateException.class).hasMessageContaining("No se pudo llamar al webhook");
    }

    @Test void sinUrlElCanalNoEstaConfiguradoYUnaUrlMalaFallaAlArrancar() {
        assertThat(new WebhookDeAlertas("").configurado()).isFalse();
        assertThat(new WebhookDeAlertas(null).configurado()).isFalse();
        assertThat(new WebhookDeAlertas("   ").configurado()).isFalse();
        assertThatThrownBy(() -> new WebhookDeAlertas("ftp://servidor/alertas")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookDeAlertas("alertas.example.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void losCaracteresDeControlSeEscapanEnElJson() {
        assertThat(WebhookDeAlertas.comillas("a\tb\u0001c")).isEqualTo("\"a\\tb\\u0001c\"");
    }

    static class Correo implements CorreoSender {
        final List<String> enviados = new ArrayList<>();
        boolean entrega = true;
        String fallaPara;
        public void enviar(String para, String asunto, String cuerpoTexto) {
            if (para.equals(fallaPara)) throw new IllegalStateException("SMTP caído");
            enviados.add(para + "|" + asunto + "|" + cuerpoTexto);
        }
        public void enviar(String para, String asunto, String cuerpoTexto, List<Adjunto> adjuntos) { enviar(para, asunto, cuerpoTexto); }
        @Override public boolean entregaDeVerdad() { return entrega; }
    }

    @Test void elCorreoMandaUnMensajeACadaDireccionConElPrefijoDeLaPlataforma() {
        Correo correo = new Correo();
        CorreoDeAlertas canal = new CorreoDeAlertas(correo, " ops@khipu.pe, guardia@khipu.pe ,, ");

        canal.enviar("ALERTA · Outbox", "detalle");

        assertThat(canal.configurado()).isTrue();
        assertThat(correo.enviados).containsExactly("ops@khipu.pe|[khipu] ALERTA · Outbox|detalle", "guardia@khipu.pe|[khipu] ALERTA · Outbox|detalle");
    }

    @Test void siUnaDireccionFallaLaOtraIgualRecibeYSoloFallaSiNingunaRecibio() {
        Correo correo = new Correo();
        correo.fallaPara = "ops@khipu.pe";

        new CorreoDeAlertas(correo, "ops@khipu.pe,guardia@khipu.pe").enviar("a", "b");

        assertThat(correo.enviados).singleElement().asString().startsWith("guardia@khipu.pe");
        assertThatThrownBy(() -> new CorreoDeAlertas(correo, "ops@khipu.pe").enviar("a", "b")).isInstanceOf(IllegalStateException.class);
    }

    @Test void sinDireccionesOSinCorreoQueEntregueNoEstaConfigurado() {
        Correo correo = new Correo();
        assertThat(new CorreoDeAlertas(correo, "").configurado()).isFalse();
        assertThat(new CorreoDeAlertas(correo, null).configurado()).isFalse();

        correo.entrega = false;
        assertThat(new CorreoDeAlertas(correo, "ops@khipu.pe").configurado()).isFalse();
    }
}
