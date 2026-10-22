package pe.factura.application.port.out;

import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.LocalDateTime;

/**
 * Envío de la guía de remisión electrónica por la API REST de SUNAT (#39): {@link #enviar} entrega el XML firmado y devuelve un ticket; {@link #consultar}
 * pregunta por él hasta que SUNAT lo procesa. Lo pide la API con un token OAuth2 del grant {@code password}: las credenciales API SUNAT de la empresa
 * (client_id y client_secret) más su usuario y clave SOL.
 *
 * <p>Errores: faltan las credenciales API SUNAT o SOL → {@code DomainException} (CREDENCIALES_API_SUNAT_NO_CARGADAS, CREDENCIALES_SOL_NO_CARGADAS);
 * no hay API GRE para el entorno de la empresa → {@code DomainException("GRE_NO_DISPONIBLE")}; SUNAT no acepta las credenciales →
 * {@link SunatCredencialesException}; rechaza el pedido por sus datos (HTTP 400/422, p. ej. 504 el nombre del archivo) → {@link SunatRechazoException} con el
 * código de su detalle; no responde o falla → {@link SunatTransientException}.
 */
public interface SunatGreGateway {
    /** SUNAT recibió el archivo: el ticket a consultar y la fecha de recepción que informa (hora de Lima, sin zona), si la entiende. */
    record Recepcion(String ticket, LocalDateTime fechaRecepcion) {}

    /**
     * La respuesta de la consulta del ticket: {@code codigo} 98 en proceso, 0 envío correcto, 99 envío con error; el CDR (ZIP) cuando SUNAT lo generó, el error
     * cuando es 99, y la URL de consulta de la guía que traiga el CDR (la del QR de la representación impresa), si la trae.
     */
    record EstadoDelTicket(String codigo, byte[] cdrZip, String numError, String desError, String enlace) {
        public static final String EN_PROCESO = "98", CORRECTO = "0", CON_ERROR = "99";
        public boolean enProceso() { return EN_PROCESO.equals(codigo); }
        public boolean correcto() { return CORRECTO.equals(codigo); }
        public boolean conError() { return CON_ERROR.equals(codigo); }
        public boolean conCdr() { return cdrZip != null && cdrZip.length > 0; }
    }

    /** Si hay una API GRE configurada para ese entorno: por defecto solo producción (SUNAT no publica una de pruebas, y no se le mandan guías de prueba a la real). */
    boolean disponible(Entorno entorno);

    /** {@code nombreArchivo}: {@code RUC-TT-SSSS-N}, sin extensión; {@code xmlFirmado}, el XML tal como se guardó. */
    Recepcion enviar(Tenant t, String nombreArchivo, byte[] xmlFirmado);

    EstadoDelTicket consultar(Tenant t, String ticket);
}
