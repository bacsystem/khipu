package pe.factura.adapters.sunat;

import pe.factura.application.port.out.SunatConsultaGateway;
import pe.factura.application.port.out.SunatTransientException;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * {@code billConsultService} (getStatus / getStatusCdr) y {@code billValidService} (validaCDPcriterios), contratos tomados
 * de los WSDL publicados por SUNAT (2026-09-19). Son servicios de producción: e-beta no los publica, así que un tenant en
 * BETA solo puede usarlos si se configuran URLs de prueba.
 */
public class SoapConsultaGateway implements SunatConsultaGateway {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final String consultaUrl, consultaBetaUrl, validezUrl, validezBetaUrl;
    private final SoapCliente cliente;

    public SoapConsultaGateway(String consultaUrl, String consultaBetaUrl, String validezUrl, String validezBetaUrl, Duration timeout) {
        this(consultaUrl, consultaBetaUrl, validezUrl, validezBetaUrl, timeout, () -> HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    SoapConsultaGateway(String consultaUrl, String consultaBetaUrl, String validezUrl, String validezBetaUrl, Duration timeout, Supplier<HttpClient> clientes) {
        this.consultaUrl = consultaUrl; this.consultaBetaUrl = consultaBetaUrl; this.validezUrl = validezUrl; this.validezBetaUrl = validezBetaUrl;
        this.cliente = new SoapCliente(timeout, clientes);
    }

    @Override public Consulta getStatusCdr(Tenant t, String ruc, String tipo, String serie, long numero) {
        String body = cliente.llamar(url(t, consultaUrl, consultaBetaUrl, "consulta de CDR"), "getStatusCdr",
                SoapEnvelope.consulta("getStatusCdr", t.sol().usernameToken(t.ruc()), t.sol().clave(), ruc, tipo, serie, numero));
        return parsear(body);
    }

    @Override public Consulta getStatus(Tenant t, String ruc, String tipo, String serie, long numero) {
        String body = cliente.llamar(url(t, consultaUrl, consultaBetaUrl, "consulta de estado"), "getStatus",
                SoapEnvelope.consulta("getStatus", t.sol().usernameToken(t.ruc()), t.sol().clave(), ruc, tipo, serie, numero));
        return parsear(body);
    }

    @Override public Consulta validar(Tenant t, String rucEmisor, String tipo, String serie, long numero, String tipoDocReceptor, String numDocReceptor, LocalDate fechaEmision, BigDecimal importeTotal) {
        String body = cliente.llamar(url(t, validezUrl, validezBetaUrl, "consulta de validez"), "validaCDPcriterios",
                SoapEnvelope.validaCdp(t.sol().usernameToken(t.ruc()), t.sol().clave(), rucEmisor, tipo, serie, numero, tipoDocReceptor, numDocReceptor,
                        fechaEmision == null ? null : fechaEmision.format(FECHA), importeTotal));
        return parsear(body);
    }

    private static String url(Tenant t, String prod, String beta, String servicio) {
        if (t.entorno() == Entorno.PRODUCCION) return prod;
        if (beta == null || beta.isBlank())
            throw new DomainException("NO_DISPONIBLE_EN_BETA", "SUNAT no publica la " + servicio + " en e-beta: la empresa debe estar en PRODUCCION");
        return beta;
    }

    private static Consulta parsear(String body) {
        String statusCode = SoapEnvelope.textoDe(body, "statusCode");
        if (statusCode == null || statusCode.isBlank()) throw new SunatTransientException("0000", "Respuesta inesperada de SUNAT sin statusCode");
        String content = SoapEnvelope.textoDe(body, "content");
        String mensaje = SoapEnvelope.textoDe(body, "statusMessage");
        return new Consulta(statusCode.trim(), mensaje == null ? "" : mensaje.trim(), content == null || content.isBlank() ? null : Base64.getDecoder().decode(content.replaceAll("\\s", "")));
    }
}
