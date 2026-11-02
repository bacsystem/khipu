package pe.factura.adapters.webhook;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.factura.application.port.out.DestinoDeWebhook.Respuesta;
import pe.factura.domain.DomainException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El destino HTTP de los webhooks (#19): entrega con las cabeceras, sin seguir redirecciones, y rechazo de direcciones internas (SSRF). */
class HttpDestinoDeWebhookTest {
    HttpServer servidor;
    final List<String> cuerpos = new CopyOnWriteArrayList<>();
    final List<Map<String, String>> cabecerasRecibidas = new CopyOnWriteArrayList<>();
    volatile int respuesta = 200;
    volatile boolean redirige;

    String levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/hook", ex -> {
            cuerpos.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Map<String, String> h = new LinkedHashMap<>();
            ex.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.get(0)));
            cabecerasRecibidas.add(h);
            if (redirige) ex.getResponseHeaders().add("Location", "http://127.0.0.1:1/interno");
            ex.sendResponseHeaders(redirige ? 302 : respuesta, -1);
            ex.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/hook";
    }

    @AfterEach void parar() { if (servidor != null) servidor.stop(0); }

    @Test void entregaUnPostJsonConLasCabecerasYDevuelveElCodigo() throws IOException {
        HttpDestinoDeWebhook destino = new HttpDestinoDeWebhook(true);
        Map<String, String> cab = new LinkedHashMap<>();
        cab.put("X-Khipu-Event", "comprobante.aceptado");
        cab.put("X-Khipu-Signature", "t=1,v1=abc");

        Respuesta r = destino.entregar(levantar(), cab, "{\"a\":1}");

        assertThat(r.exitosa()).isTrue();
        assertThat(r.http()).isEqualTo(200);
        assertThat(cuerpos).containsExactly("{\"a\":1}");
        assertThat(cabecerasRecibidas.get(0)).containsEntry("x-khipu-event", "comprobante.aceptado").containsEntry("x-khipu-signature", "t=1,v1=abc")
                .containsEntry("content-type", "application/json; charset=utf-8").containsEntry("user-agent", "khipu-webhooks/1");
    }

    @Test void unaRespuestaQueNoEs2xxNoEsExitosa() throws IOException {
        respuesta = 503;

        Respuesta r = new HttpDestinoDeWebhook(true).entregar(levantar(), Map.of(), "{}");

        assertThat(r.exitosa()).isFalse();
        assertThat(r.http()).isEqualTo(503);
    }

    @Test void unaRedireccionNoSeSigueYCuentaComoFallo() throws IOException {
        redirige = true;

        Respuesta r = new HttpDestinoDeWebhook(true).entregar(levantar(), Map.of(), "{}");

        assertThat(r.http()).isEqualTo(302);
        assertThat(r.exitosa()).isFalse();
        assertThat(cuerpos).hasSize(1);
    }

    @Test void sinServidorEsUnFalloConMensajeYNoUnaExcepcion() {
        Respuesta r = new HttpDestinoDeWebhook(true).entregar("http://127.0.0.1:1/hook", Map.of(), "{}");

        assertThat(r.exitosa()).isFalse();
        assertThat(r.http()).isZero();
        assertThat(r.error()).isEqualTo("No se pudo conectar con el servidor");
    }

    @Test void conRedesPrivadasRechazadasNoConectaAUnaDireccionInterna() throws IOException {
        String url = levantar();

        Respuesta r = new HttpDestinoDeWebhook(false).entregar(url, Map.of(), "{}");

        assertThat(r.http()).isZero();
        assertThat(r.error()).isEqualTo(HttpDestinoDeWebhook.RECHAZO);
        assertThat(cuerpos).as("el servidor interno no recibió nada").isEmpty();
    }

    @Test void exigirDestinoPublicoRechazaLoopbackPrivadasYLaMetadataDeLaNube() {
        HttpDestinoDeWebhook destino = new HttpDestinoDeWebhook(false);
        for (String url : List.of("https://127.0.0.1/x", "https://localhost/x", "https://10.0.0.5/x", "https://192.168.1.10/x", "https://172.16.0.1/x",
                "https://169.254.169.254/latest/meta-data", "https://100.64.0.1/x", "https://0.0.0.0/x", "https://[::1]/x", "https://[fd00::1]/x"))
            assertThatThrownBy(() -> destino.exigirDestinoPublico(url)).as(url).isInstanceOf(DomainException.class).hasMessage(HttpDestinoDeWebhook.RECHAZO);
    }

    @Test void unNombreQueNoSeResuelveSeRechazaConElMismoMensajeQueUnoInterno() {
        assertThatThrownBy(() -> new HttpDestinoDeWebhook(false).exigirDestinoPublico("https://no-existe.invalid/x")).isInstanceOf(DomainException.class)
                .hasMessage(HttpDestinoDeWebhook.RECHAZO);
    }

    @Test void nuncaUsaUnProxyDelSistema() throws Exception {
        java.net.ProxySelector anterior = java.net.ProxySelector.getDefault();
        java.util.concurrent.atomic.AtomicInteger consultas = new java.util.concurrent.atomic.AtomicInteger();
        java.net.ProxySelector.setDefault(new java.net.ProxySelector() {
            public List<java.net.Proxy> select(java.net.URI uri) { consultas.incrementAndGet(); return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 1))); }
            public void connectFailed(java.net.URI uri, java.net.SocketAddress sa, IOException ioe) {}
        });
        try {
            Respuesta r = new HttpDestinoDeWebhook(true).entregar(levantar(), Map.of(), "{}");
            assertThat(r.exitosa()).as("llegó directo al servidor, sin pasar por el proxy configurado").isTrue();
            assertThat(consultas).hasValue(0);
        } finally {
            java.net.ProxySelector.setDefault(anterior);
        }
    }

    @Test void conRedesPrivadasPermitidasNoSeComprueba() {
        new HttpDestinoDeWebhook(true).exigirDestinoPublico("https://127.0.0.1/x");
    }

    @Test void esPublicaDistingueLasDireccionesDeInternetDeLasInternas() throws Exception {
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("100.63.255.255"))).isTrue();
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("100.127.255.255"))).isFalse();
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("100.128.0.1"))).isTrue();
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("::ffff:10.0.0.1"))).isFalse();
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("::10.0.0.1"))).isFalse();
        // Rangos reservados que InetAddress no marca como internos.
        for (String reservada : List.of("192.0.0.192", "198.18.0.1", "198.19.255.254", "240.0.0.1", "255.255.255.255", "0.1.2.3", "64:ff9b::a00:1", "64:ff9b::c0a8:1"))
            assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName(reservada))).as(reservada).isFalse();
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("198.20.0.1"))).isTrue();
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("64:ff9b::808:808"))).as("NAT64 hacia 8.8.8.8").isTrue();
        assertThat(HttpDestinoDeWebhook.esPublica(InetAddress.getByName("223.255.255.255"))).isTrue();
    }
}
