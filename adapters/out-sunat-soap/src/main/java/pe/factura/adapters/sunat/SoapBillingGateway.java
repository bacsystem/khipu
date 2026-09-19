package pe.factura.adapters.sunat;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.SunatRechazoException;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.tenant.Tenant;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class SoapBillingGateway implements SunatBillingGateway {
    private final SunatUrls urls;
    private final Duration timeout;
    private final Supplier<HttpClient> clientes;

    /**
     * Un {@link HttpClient} nuevo por envío, cerrado al terminar. El frontal de SUNAT responde {@code 401 Authorization
     * Required} a <em>todas</em> las peticiones que reutilizan una conexión keep-alive después de la primera (comprobado
     * en la homologación #32: sobre una misma conexión, 200 y luego 401 en cada envío siguiente). El cliente del JDK no
     * permite enviar {@code Connection: close}, así que se descarta el pool entero en cada llamada; el coste (un handshake
     * TLS por comprobante) es despreciable frente a la validación de SUNAT. Los 401 que aparecen aun con conexiones nuevas
     * son otro fenómeno (el balanceador) y los cubre {@link #INTENTOS_401}.
     */
    public SoapBillingGateway(SunatUrls urls, Duration timeout) {
        this(urls, timeout, () -> HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    /**
     * Hoja "CódigosRetorno" de las reglas de validación de SUNAT: 0100–0999 son fallos del servicio o de
     * autenticación (reintentar); 1000–1999 son errores del contenido o del emisor (1001 formato de serie,
     * 1033 "registrado previamente con otros datos", 1034–1036 nombre de archivo ≠ XML, 1059 sin firma,
     * 1078 emisor no autorizado en el SEE) y 2000–3999 rechazos de validación. Ni los 1xxx ni los 2xxx
     * cambian por reintentar: el comprobante queda rechazado y hay que corregirlo y volver a emitir.
     */
    static boolean esFaultDefinitivo(String codigo) {
        return Integer.parseInt(codigo) >= 1000;
    }

    /**
     * Reintentos inmediatos ante {@code HTTP 401}. El balanceador de SUNAT devuelve 401 de forma intermitente con credenciales
     * correctas (en e-beta, casi exactamente una de cada dos peticiones consecutivas, incluso con conexiones nuevas); con
     * credenciales erróneas devuelve el mismo 401. Tres intentos separan ambos casos en la práctica sin esperar al outbox.
     */
    static final int INTENTOS_401 = 3;

    @Override public byte[] sendBill(Tenant tenant, String nombreArchivo, byte[] xmlFirmado) {
        byte[] zip = ZipUtil.comprimir(nombreArchivo + ".xml", xmlFirmado);
        String body = llamar(tenant, "sendBill", SoapEnvelope.sendBill(tenant.sol().usernameToken(tenant.ruc()), tenant.sol().clave(), nombreArchivo + ".zip", zip));
        return base64(body, "applicationResponse");
    }

    @Override public String sendSummary(Tenant tenant, String nombreArchivo, byte[] xmlFirmado) {
        byte[] zip = ZipUtil.comprimir(nombreArchivo + ".xml", xmlFirmado);
        String body = llamar(tenant, "sendSummary", SoapEnvelope.sendSummary(tenant.sol().usernameToken(tenant.ruc()), tenant.sol().clave(), nombreArchivo + ".zip", zip));
        String ticket = SoapEnvelope.textoDe(body, "ticket");
        if (ticket == null || ticket.isBlank()) throw new SunatTransientException("0000", "Respuesta inesperada de SUNAT sin ticket");
        return ticket;
    }

    @Override public EstadoTicket getStatus(Tenant tenant, String ticket) {
        String body = llamar(tenant, "getStatus", SoapEnvelope.getStatus(tenant.sol().usernameToken(tenant.ruc()), tenant.sol().clave(), ticket));
        String statusCode = SoapEnvelope.textoDe(body, "statusCode");
        if (statusCode == null || statusCode.isBlank()) throw new SunatTransientException("0000", "Respuesta inesperada de SUNAT sin statusCode para el ticket " + ticket);
        String content = SoapEnvelope.textoDe(body, "content");
        return new EstadoTicket(statusCode.trim(), content == null || content.isBlank() ? null : Base64.getDecoder().decode(content.replaceAll("\\s", "")));
    }

    /** Envía el sobre SOAP (reintentando el 401 intermitente) y traduce los SOAPFault y los HTTP de error; devuelve el cuerpo XML. */
    private String llamar(Tenant tenant, String operacion, String cuerpo) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(urls.para(tenant.entorno())))
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

    private static byte[] base64(String body, String elemento) {
        String b64 = SoapEnvelope.textoDe(body, elemento);
        if (b64 == null || b64.isBlank()) throw new SunatTransientException("0000", "Respuesta inesperada de SUNAT sin " + elemento);
        return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
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
