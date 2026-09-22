package pe.factura.adapters.sunat;

import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Llamada SOAP a SUNAT compartida por los gateways: un {@link HttpClient} nuevo por llamada (el frontal de SUNAT responde 401
 * a las conexiones keep-alive reutilizadas), reintento inmediato del 401 intermitente del balanceador y traducción de los
 * SOAPFault (0100–0999 transitorio, ≥ 1000 definitivo) y de los HTTP de error. Ver {@link SoapBillingGateway}.
 */
final class SoapCliente {
    static final int INTENTOS_401 = 3;
    private final Duration timeout;
    private final Supplier<HttpClient> clientes;

    SoapCliente(Duration timeout, Supplier<HttpClient> clientes) { this.timeout = timeout; this.clientes = clientes; }

    /**
     * Hoja "CódigosRetorno" de las reglas de validación de SUNAT: 0100–0999 son fallos del servicio o de
     * autenticación (reintentar); 1000–1999 son errores del contenido o del emisor (1001 formato de serie,
     * 1033 "registrado previamente con otros datos", 1034–1036 nombre de archivo ≠ XML, 1059 sin firma,
     * 1078 emisor no autorizado en el SEE) y 2000–3999 rechazos de validación. Ni los 1xxx ni los 2xxx
     * cambian por reintentar: el comprobante (o la consulta) queda resuelto tal cual, sin reintento.
     */
    static boolean esFaultDefinitivo(String codigo) {
        return Integer.parseInt(codigo) >= 1000;
    }

    String llamar(String url, String operacion, String cuerpo) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "urn:" + operacion)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = enviar(req);
        for (int intento = 2; resp.statusCode() == 401 && intento <= INTENTOS_401; intento++) resp = enviar(req);

        String body = resp.body() == null ? "" : resp.body();
        String faultcode = SoapEnvelope.textoDe(body, "faultcode");
        if (faultcode != null) {
            String codigo = SoapEnvelope.codigoDeFault(faultcode);
            String msg = SoapEnvelope.textoDe(body, "faultstring");
            if (esFaultDefinitivo(codigo)) throw new SunatRechazoException(codigo, msg == null ? "" : msg);
            throw new SunatTransientException(codigo, msg == null ? "SOAPFault " + faultcode : msg);
        }
        if (resp.statusCode() == 401) throw new SunatTransientException("0000", "SUNAT respondió HTTP 401 en " + INTENTOS_401 + " intentos (revisar credenciales SOL/URL)");
        if (resp.statusCode() >= 500) throw new SunatTransientException("0000", "SUNAT respondió HTTP " + resp.statusCode());
        if (resp.statusCode() >= 400) throw new SunatTransientException("0000", "SUNAT respondió HTTP " + resp.statusCode() + " (revisar credenciales/URL)");
        return body;
    }

    private HttpResponse<String> enviar(HttpRequest req) {
        try (HttpClient http = clientes.get()) {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new SunatTransientException("0109", "Tiempo de espera agotado llamando a SUNAT", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new SunatTransientException("0000", "Error de red llamando a SUNAT: " + e.getMessage(), e);
        }
    }
}
