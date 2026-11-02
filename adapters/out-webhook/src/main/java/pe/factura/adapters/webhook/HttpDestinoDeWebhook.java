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
 * una dirección interna), nunca se usa un proxy (el del sistema podría llevar a una red interna) y la respuesta no se lee: solo importa el código.
 *
 * <p>Límite conocido: el cliente HTTP vuelve a resolver el nombre al conectar, así que un DNS que cambie justo entre la comprobación y la conexión (rebinding) no se detiene
 * aquí; lo acota la caché de DNS de la JVM, que se fija en {@value #CACHE_DNS_SEGUNDOS} s a propósito. Los mensajes de error no revelan la dirección resuelta ni distinguen un
 * nombre que no existe de uno interno: serían un oráculo para recorrer la red interna.
 */
public class HttpDestinoDeWebhook implements DestinoDeWebhook {
    static final Duration CONEXION = Duration.ofSeconds(5);
    static final Duration ESPERA = Duration.ofSeconds(10);
    static final String CACHE_DNS_SEGUNDOS = "30";
    static final String RECHAZO = "La URL del webhook debe apuntar a un servidor público de internet";

    private final boolean permitirRedesPrivadas;
    private final HttpClient http;

    /** {@code permitirRedesPrivadas}: solo para desarrollo y pruebas (un servidor en localhost); en producción siempre falso. */
    public HttpDestinoDeWebhook(boolean permitirRedesPrivadas) {
        this.permitirRedesPrivadas = permitirRedesPrivadas;
        // Con la caché apagada (ttl 0) el cliente volvería a resolver siempre y el rebinding entre la comprobación y la conexión sería trivial; se fija un valor acotado.
        if (java.security.Security.getProperty("networkaddress.cache.ttl") == null) java.security.Security.setProperty("networkaddress.cache.ttl", CACHE_DNS_SEGUNDOS);
        this.http = HttpClient.newBuilder().connectTimeout(CONEXION).followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY).build();
    }

    @Override public void exigirDestinoPublico(String url) {
        if (permitirRedesPrivadas) return;
        String host = URI.create(url).getHost();
        InetAddress[] direcciones;
        try {
            direcciones = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new DomainException("WEBHOOK_INVALIDO", RECHAZO);
        }
        for (InetAddress d : direcciones)
            if (!esPublica(d)) throw new DomainException("WEBHOOK_INVALIDO", RECHAZO);
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
            // Sin el mensaje de la excepción: trae direcciones y puertos que, contra una red interna, serían un oráculo.
            return new Respuesta(0, "No se pudo conectar con el servidor");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Respuesta(0, "Entrega interrumpida");
        }
    }

    /** Una dirección que se alcanza desde internet: ni loopback, ni comodín, ni privada, ni de enlace local, ni multicast, ni reservada (ver {@link #publicaIpv4}), ni IPv6 única local (fc00::/7). */
    static boolean esPublica(InetAddress d) {
        if (d.isAnyLocalAddress() || d.isLoopbackAddress() || d.isLinkLocalAddress() || d.isSiteLocalAddress() || d.isMulticastAddress()) return false;
        byte[] b = d.getAddress();
        if (d instanceof Inet4Address) return publicaIpv4(b);
        if (d instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) return false;
            // NAT64 (64:ff9b::/96) lleva una IPv4 en los últimos 4 bytes: vale lo que valga esa.
            if (b[0] == 0x00 && b[1] == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b && ceros(b, 4, 12)) {
                try {
                    return esPublica(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
            // ::a.b.c.d (IPv4 compatible): java ya entrega ::ffff:a.b.c.d como Inet4Address; esto cubre la otra forma.
            if (ceros(b, 0, 12)) return false;
        }
        return true;
    }

    private static boolean ceros(byte[] b, int desde, int hasta) {
        for (int i = desde; i < hasta; i++) if (b[i] != 0) return false;
        return true;
    }

    /**
     * Fuera de las privadas, loopback y enlace local que ya filtra {@link InetAddress}: 0.0.0.0/8, CGNAT 100.64.0.0/10, 192.0.0.0/24 (protocolo IETF), 198.18.0.0/15 (pruebas de
     * rendimiento) y 240.0.0.0/4 (reservado y difusión).
     */
    private static boolean publicaIpv4(byte[] b) {
        int a = b[0] & 0xff, c = b[1] & 0xff, d = b[2] & 0xff;
        if (a == 0) return false;
        if (a == 100 && (c & 0xc0) == 64) return false;
        if (a == 192 && c == 0 && d == 0) return false;
        if (a == 198 && (c & 0xfe) == 18) return false;
        return a < 240;
    }
}
