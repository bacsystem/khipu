package pe.factura.adapters.alertas;

import pe.factura.application.port.out.CanalDeAlertas;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Alertas al operador por un webhook (#341): un {@code POST} con JSON a la URL de {@code ALERTAS_WEBHOOK_URL}. El cuerpo lleva el mismo texto bajo {@code text} (Slack,
 * Mattermost, Teams) y {@code content} (Discord), más {@code asunto} y {@code cuerpo} por separado para quien lo procese. Cualquier respuesta que no sea 2xx es un fallo.
 */
public class WebhookDeAlertas implements CanalDeAlertas {
    private static final Duration ESPERA = Duration.ofSeconds(10);

    private final URI destino;
    private final HttpClient http;

    /** {@code url} vacía o nula: el canal queda sin configurar. Una URL que no es http(s) se rechaza al arrancar, no en la primera alerta. */
    public WebhookDeAlertas(String url) {
        this.destino = url == null || url.isBlank() ? null : validar(url.strip());
        this.http = HttpClient.newBuilder().connectTimeout(ESPERA).build();
    }

    private static URI validar(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            // El mensaje de URI.create trae la URL, y la de un webhook de Slack o Discord es un secreto: no puede ir al log de arranque.
            throw new IllegalArgumentException("ALERTAS_WEBHOOK_URL no es una URL válida");
        }
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())))
            throw new IllegalArgumentException("ALERTAS_WEBHOOK_URL debe ser una URL http(s) completa");
        return uri;
    }

    @Override public String nombre() { return "webhook"; }

    @Override public boolean configurado() { return destino != null; }

    @Override public void enviar(String asunto, String cuerpo) {
        String texto = asunto + "\n" + cuerpo;
        String json = "{\"text\":" + comillas(texto) + ",\"content\":" + comillas(texto) + ",\"asunto\":" + comillas(asunto) + ",\"cuerpo\":" + comillas(cuerpo) + "}";
        HttpRequest pedido = HttpRequest.newBuilder(destino).timeout(ESPERA).header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        try {
            HttpResponse<Void> r = http.send(pedido, HttpResponse.BodyHandlers.discarding());
            if (r.statusCode() / 100 != 2) throw new IllegalStateException("El webhook respondió HTTP " + r.statusCode());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo llamar al webhook: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la llamada al webhook", e);
        }
    }

    /** Un literal JSON: comillas, barras y todo carácter de control escapados. */
    static String comillas(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }
}
