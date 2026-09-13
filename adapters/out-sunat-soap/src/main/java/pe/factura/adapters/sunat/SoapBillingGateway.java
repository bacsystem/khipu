package pe.factura.adapters.sunat;

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

public class SoapBillingGateway implements SunatBillingGateway {
    private final SunatUrls urls;
    private final Duration timeout;
    private final HttpClient http;

    public SoapBillingGateway(SunatUrls urls, Duration timeout) {
        this(urls, timeout, HttpClient.newBuilder().connectTimeout(timeout).build());
    }
    public SoapBillingGateway(SunatUrls urls, Duration timeout, HttpClient http) { this.urls = urls; this.timeout = timeout; this.http = http; }

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
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new SunatTransientException("0109", "Tiempo de espera agotado llamando a SUNAT", e);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new SunatTransientException("0000", "Error de red llamando a SUNAT: " + e.getMessage(), e);
        }

        String body = resp.body() == null ? "" : resp.body();
        String faultcode = SoapEnvelope.textoDe(body, "faultcode");
        if (faultcode != null) {
            String codigo = SoapEnvelope.codigoDeFault(faultcode);
            String msg = SoapEnvelope.textoDe(body, "faultstring");
            if (Integer.parseInt(codigo) >= 2000) throw new SunatRechazoException(codigo, msg == null ? "" : msg);
            throw new SunatTransientException(codigo, msg == null ? "SOAPFault " + faultcode : msg);
        }
        if (resp.statusCode() >= 500) throw new SunatTransientException("0000", "SUNAT respondió HTTP " + resp.statusCode());
        if (resp.statusCode() >= 400) throw new SunatTransientException("0000", "SUNAT respondió HTTP " + resp.statusCode() + " (revisar credenciales/URL)");

        String b64 = SoapEnvelope.textoDe(body, "applicationResponse");
        if (b64 == null || b64.isBlank()) throw new SunatTransientException("0000", "Respuesta inesperada de SUNAT sin applicationResponse");
        return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
    }
}
