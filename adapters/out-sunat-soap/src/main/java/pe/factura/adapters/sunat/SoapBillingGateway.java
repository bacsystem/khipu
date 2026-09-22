package pe.factura.adapters.sunat;

import pe.factura.application.port.out.SunatBillingGateway;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.tenant.Tenant;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Supplier;

public class SoapBillingGateway implements SunatBillingGateway {
    private final SunatUrls urls;
    private final SoapCliente cliente;

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

    SoapBillingGateway(SunatUrls urls, Duration timeout, Supplier<HttpClient> clientes) {
        this.urls = urls;
        this.cliente = new SoapCliente(timeout, clientes);
    }

    /** La lógica real vive en {@link SoapCliente#esFaultDefinitivo}: es genérica a cualquier llamada SOAP a SUNAT, no solo a sendBill. */
    static boolean esFaultDefinitivo(String codigo) { return SoapCliente.esFaultDefinitivo(codigo); }

    /**
     * Reintentos inmediatos ante {@code HTTP 401}. El balanceador de SUNAT devuelve 401 de forma intermitente con credenciales
     * correctas (en e-beta, casi exactamente una de cada dos peticiones consecutivas, incluso con conexiones nuevas); con
     * credenciales erróneas devuelve el mismo 401. Tres intentos separan ambos casos en la práctica sin esperar al outbox.
     */
    static final int INTENTOS_401 = SoapCliente.INTENTOS_401;

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

    private String llamar(Tenant tenant, String operacion, String cuerpo) {
        return cliente.llamar(urls.para(tenant.entorno()), operacion, cuerpo);
    }

    private static byte[] base64(String body, String elemento) {
        String b64 = SoapEnvelope.textoDe(body, elemento);
        if (b64 == null || b64.isBlank()) throw new SunatTransientException("0000", "Respuesta inesperada de SUNAT sin " + elemento);
        return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
    }
}
