package pe.factura.adapters.sunat;

import pe.factura.application.port.out.SunatCredencialesException;
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
     * Los faults de autenticación de la hoja «CódigosRetorno» (#107): encabezado de seguridad incorrecto (en la práctica, el usuario SOL mal escrito),
     * usuario o clave incorrectos o inexistentes, usuario inactivo o no secundario, sin perfil ni afiliación a factura electrónica, y el RUC que no
     * corresponde al usuario (0154). No cambian por reintentar: pausan los envíos de la empresa (con un envío de prueba por hora). El 0100 genérico, el
     * 0109 («el servicio de autenticación no está disponible»), el 0110 («no se pudo obtener la información del tipo de usuario», del lado de SUNAT) y
     * los 013x son del servicio y se reintentan como cualquier caída.
     */
    static final java.util.Set<String> FAULTS_DE_CREDENCIALES = java.util.Set.of("0101", "0102", "0103", "0104", "0105", "0106", "0111", "0112", "0113", "0154");

    /**
     * Hoja "CódigosRetorno" de las reglas de validación de SUNAT: los 0100–0999 son del servicio o de autenticación (se reintentan, salvo las
     * credenciales de arriba, que pausan); 1000–1999 son errores del contenido o del emisor (1001 formato de serie, 1033 "registrado previamente con
     * otros datos", 1034–1036 nombre de archivo ≠ XML, 1059 sin firma, 1078 emisor no autorizado en el SEE) y 2000–3999 rechazos de validación. Ni los
     * 1xxx ni los 2xxx cambian por reintentar: el comprobante (o la consulta) queda resuelto tal cual, sin reintento.
     */
    static boolean esFaultDefinitivo(String codigo) {
        // 1xxx–3xxx: error del contribuyente, no cambia por reintentar. El 0127 («El ticket no existe») también es definitivo:
        // reintentarlo consumía el presupuesto de consultas sin que SUNAT tuviera nada que responder.
        return Integer.parseInt(codigo) >= 1000 || "0127".equals(codigo);
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
            if (FAULTS_DE_CREDENCIALES.contains(codigo)) throw new SunatCredencialesException(codigo, msg == null ? "Credenciales SOL rechazadas" : msg);
            throw new SunatTransientException(codigo, msg == null ? "SOAPFault " + faultcode : msg);
        }
        // Un 401 aislado es el balanceador de SUNAT y ya se reintentó arriba; tres seguidos son las credenciales (#107).
        if (resp.statusCode() == 401) throw new SunatCredencialesException("0000", "SUNAT respondió HTTP 401 en " + INTENTOS_401 + " intentos: no acepta las credenciales SOL");
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
