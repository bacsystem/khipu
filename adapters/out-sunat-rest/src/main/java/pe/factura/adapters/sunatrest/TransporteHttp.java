package pe.factura.adapters.sunatrest;

import pe.factura.application.port.out.SunatTransientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * El POST (y el GET de la consulta de tickets) HTTP a las APIs de SUNAT, compartido por el token y los servicios. Sin redirecciones: una 302 se llevaría el {@code Authorization} (o el
 * formulario con el secreto) a otro servidor. La red caída y el tiempo agotado son {@link SunatTransientException} (los mismos códigos que el cliente SOAP:
 * 0109 tiempo agotado, 0000 lo demás); el código HTTP lo interpreta quien llama, que sabe qué significa en su servicio.
 */
final class TransporteHttp {
    record Respuesta(int status, String cuerpo) {}

    private final HttpClient http;
    private final Duration espera;

    TransporteHttp(Duration conexion, Duration espera) {
        this.http = HttpClient.newBuilder().connectTimeout(conexion).followRedirects(HttpClient.Redirect.NEVER).build();
        this.espera = espera;
    }

    Respuesta post(String url, String contentType, String cuerpo, String autorizacion) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(espera)
                .header("Content-Type", contentType).header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        return enviar(req, autorizacion);
    }

    /** La consulta del ticket de la GRE (#39) es un GET sin cuerpo. */
    Respuesta get(String url, String autorizacion) {
        return enviar(HttpRequest.newBuilder(URI.create(url)).timeout(espera).header("Accept", "application/json").GET(), autorizacion);
    }

    private Respuesta enviar(HttpRequest.Builder req, String autorizacion) {
        if (autorizacion != null) req.header("Authorization", autorizacion);
        try {
            HttpResponse<String> r = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Respuesta(r.statusCode(), r.body() == null ? "" : r.body());
        } catch (HttpTimeoutException e) {
            throw new SunatTransientException("0109", "Tiempo de espera agotado llamando a la API de SUNAT (" + espera.toSeconds() + " s)", e);
        } catch (IOException e) {
            // El mensaje de la excepción de red trae host y puerto, nunca el cuerpo: no lleva secretos.
            throw new SunatTransientException("0000", "Error de red llamando a la API de SUNAT: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SunatTransientException("0000", "Llamada a la API de SUNAT interrumpida", e);
        }
    }
}
