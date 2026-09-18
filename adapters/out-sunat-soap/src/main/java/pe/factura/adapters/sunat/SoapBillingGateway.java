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
     * Un {@link HttpClient} nuevo por envío, cerrado al terminar. El frontal de SUNAT (e-beta y producción comparten
     * configuración) responde {@code 401 Authorization Required} a cualquier petición que reutilice una conexión
     * keep-alive: solo la primera petición de cada conexión TCP se autentica. Comprobado en la homologación (#32):
     * con un pool, los envíos alternos fallaban con 401. El cliente del JDK no permite enviar {@code Connection: close},
     * así que se descarta el pool entero en cada llamada; el coste (un handshake TLS por comprobante) es despreciable
     * frente a la validación de SUNAT.
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
        String nombreZip = nombreArchivo + ".zip";
        String cuerpo = SoapEnvelope.sendBill(tenant.sol().usernameToken(tenant.ruc()), tenant.sol().clave(), nombreZip, zip);

        HttpRequest req = HttpRequest.newBuilder(URI.create(urls.para(tenant.entorno())))
                .timeout(timeout)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "urn:sendBill")
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

        String b64 = SoapEnvelope.textoDe(body, "applicationResponse");
        if (b64 == null || b64.isBlank()) throw new SunatTransientException("0000", "Respuesta inesperada de SUNAT sin applicationResponse");
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
