package pe.factura.application.port.in;

import pe.factura.domain.documento.EstadoDocumento;

import java.time.LocalDate;
import java.util.UUID;

/**
 * La ficha de un comprobante en el backoffice (#251): a qué empresa pertenece, su estado, sus intentos de envío y si sus archivos están guardados. Solo lectura y sin el
 * contenido de los archivos: es para entender qué le pasa a un comprobante sin entrar como el cliente.
 */
public interface ConsultarComprobanteAdminUseCase {
    /**
     * {@code ultimoError}: el último fallo de envío, nulo si no hubo. {@code cdr}: la respuesta de SUNAT, nula si todavía no hay. {@code cuentaId}: nula en una empresa de
     * integración.
     */
    record Ficha(UUID id, UUID empresaId, String ruc, String razonSocial, UUID cuentaId, String nombreArchivo, String tipo, String serie, Long numero, LocalDate fechaEmision,
                 EstadoDocumento estado, int intentos, String ultimoError, RespuestaSunat cdr, boolean tieneXml, boolean tieneCdr) {}

    record RespuestaSunat(String codigo, String descripcion) {}

    /** {@code 404 NO_ENCONTRADO} si no existe. */
    Ficha ficha(UUID comprobanteId);
}
