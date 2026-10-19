package pe.factura.adapters.webhook;

import pe.factura.application.port.out.DestinoDeWebhook;
import pe.factura.domain.DomainException;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * La entrega de webhooks por HTTP (#19). El servidor lo elige el integrador, así que antes de conectar se resuelve su nombre y se rechaza todo lo que no sea una dirección
 * pública (loopback, redes privadas, enlace local, la metadata de la nube en 169.254.169.254, CGNAT): sin esto un webhook serviría para que khipu consulte su propia red
 * interna (SSRF). La comprobación se repite en cada entrega, no solo al registrar, porque el DNS puede cambiar después. No se siguen redirecciones (una 302 podría llevar a
 * una dirección interna) y la respuesta no se lee: solo importa el código.
 */
public class HttpDestinoDeWebhook implements DestinoDeWebhook {
    static final Duration CONEXION = Duration.ofSeconds(5);
    static final Duration ESPERA = Duration.ofSeconds(10);

    private final boolean permitirRedesPrivadas;
    private final HttpClient http;

    /** {@code permitirRedesPrivadas}: solo para desarrollo y pruebas (un servidor en localhost); en producción siempre falso. */
    public HttpDestinoDeWebhook(boolean permitirRedesPrivadas) {
        this.permitirRedesPrivadas = permitirRedesPrivadas;
        this.http = HttpClient.newBuilder().connectTimeout(CONEXION).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public void exigirDestinoPublico(String url) {
        if (permitirRedesPrivadas) return;
        String host = URI.create(url).getHost();
        InetAddress[] direcciones;
        try {
            direcciones = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new DomainException("WEBHOOK_INVALIDO", "No se pudo resolver el servidor «" + host + "» de la URL del webhook");
        }
        for (InetAddress d : direcciones)
            if (!esPublica(d)) throw new DomainException("WEBHOOK_INVALIDO", "La URL del webhook apunta a una dirección interna (" + d.getHostAddress() + "); use un servidor público");
    }

    @Override public Respuesta entregar(String url, Map<String, String> cabeceras, String cuerpo) {
        try {
            exigirDestinoPublico(url);
        } catch (DomainException e) {
            return new Respuesta(0, e.getMessage());
        }
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url)).timeout(ESPERA).header("Content-Type", "application/json; charset=utf-8").header("User-Agent", "khipu-webhooks/1")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        cabeceras.forEach(pedido::header);
        try {
            HttpResponse<Void> r = http.send(pedido.build(), HttpResponse.BodyHandlers.discarding());
            return new Respuesta(r.statusCode(), null);
        } catch (HttpTimeoutException e) {
            return new Respuesta(0, "Tiempo de espera agotado (" + ESPERA.toSeconds() + " s)");
        } catch (IOException e) {
            return new Respuesta(0, "No se pudo conectar: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Respuesta(0, "Entrega interrumpida");
        }
    }

    /** Una dirección que se alcanza desde internet: ni loopback, ni comodín, ni privada, ni de enlace local, ni multicast, ni CGNAT (100.64.0.0/10), ni IPv6 única local (fc00::/7). */
    static boolean esPublica(InetAddress d) {
        if (d.isAnyLocalAddress() || d.isLoopbackAddress() || d.isLinkLocalAddress() || d.isSiteLocalAddress() || d.isMulticastAddress()) return false;
        byte[] b = d.getAddress();
        if (d instanceof Inet4Address) return !((b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64) && (b[0] & 0xff) != 0;
        if (d instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) return false;
            // ::ffff:a.b.c.d (IPv4 mapeada): java ya la entrega como Inet4Address; esto cubre ::a.b.c.d compatible
            boolean compatible = true;
            for (int i = 0; i < 12; i++) if (b[i] != 0) { compatible = false; break; }
            if (compatible) return false;
        }
        return true;
    }
}
