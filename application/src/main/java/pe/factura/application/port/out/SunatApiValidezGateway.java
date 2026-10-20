package pe.factura.application.port.out;

import pe.factura.domain.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * «Consulta integrada de comprobante de pago» por la API REST de SUNAT (#318): {@code POST .../contribuyentes/{ruc}/validarcomprobante} con el token OAuth2 que
 * se obtiene con las {@link pe.factura.domain.tenant.CredencialesApiSunat} de la empresa. A diferencia de {@code billValidService} (SOAP), no usa la clave SOL.
 *
 * <p>Errores: {@link SunatCredencialesException} si SUNAT no acepta el client_id/client_secret (o el token sigue rechazado tras renovarlo);
 * {@link SunatTransientException} si no responde (red, tiempo agotado, 5xx, respuesta ilegible); {@link SunatRechazoException} si responde que el
 * pedido es inválido (400/422 de la consulta, {@code success: false}) (qué hacer con él lo decide {@code ConsultarValidezService}: hoy cae a SOAP mientras el contrato no esté verificado).
 */
public interface SunatApiValidezGateway {
    /** {@code fechaEmision} es obligatoria en la API; {@code importeTotal} lo es para los comprobantes electrónicos (nulo para los físicos). */
    Validez validar(Tenant tenant, String rucEmisor, String tipo, String serie, long numero, LocalDate fechaEmision, BigDecimal importeTotal);

    /**
     * {@code data} de la respuesta, con los códigos tal como los da SUNAT (anexo del manual): {@code estadoCp} 0 no existe, 1 aceptado, 2 anulado (comunicado
     * en una baja), 3 autorizado (imprenta), 4 no autorizado; {@code estadoRuc} 00 activo, 01/02 baja provisional, 03 suspensión temporal, 10/11 baja definitiva o
     * de oficio, 22 inhabilitado; {@code condicionDomicilio} 00 habido, 09 pendiente, 11 por verificar, 12 no habido, 20 no hallado. Los dos últimos pueden faltar.
     */
    record Validez(String estadoCp, String estadoRuc, String condicionDomicilio, List<String> observaciones) {
        public Validez { observaciones = observaciones == null ? List.of() : List.copyOf(observaciones); }
    }
}
