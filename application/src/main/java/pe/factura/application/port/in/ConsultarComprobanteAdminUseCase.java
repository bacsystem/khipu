package pe.factura.application.port.in;

import pe.factura.domain.documento.EstadoDocumento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La ficha de un comprobante en el backoffice (#251): a qué empresa pertenece, su estado, sus intentos de envío y si sus archivos están guardados. Solo lectura y sin el
 * contenido de los archivos: es para entender qué le pasa a un comprobante sin entrar como el cliente.
 */
public interface ConsultarComprobanteAdminUseCase {
    /**
     * {@code ultimoError}: el último fallo de envío, nulo si no hubo. {@code cdr}: la respuesta de SUNAT, nula si todavía no hay. {@code cuentaId}: nula en una empresa de
     * integración. {@code bajas}: sus comunicaciones de baja o resúmenes diarios, de la más reciente a la más antigua; vacía si no tiene (#362).
     */
    record Ficha(UUID id, UUID empresaId, String ruc, String razonSocial, UUID cuentaId, String nombreArchivo, String tipo, String serie, Long numero, LocalDate fechaEmision,
                 EstadoDocumento estado, int intentos, String ultimoError, RespuestaSunat cdr, boolean tieneXml, boolean tieneCdr, List<BajaDeFicha> bajas) {}

    record RespuestaSunat(String codigo, String descripcion) {}

    /**
     * #362 H9: una comunicación de baja (RA) o un resumen diario (RC) del comprobante, tal como la ve soporte. {@code condicion}: {@code BAJA} anula, {@code ALTA} informa una
     * boleta tardía. {@code motivo}: el que se pidió al anular o el que khipu escribe en un alta; en una boleta o una nota B el RC no lo lleva, queda solo en khipu.
     * {@code ticket} y {@code cdr}: nulos mientras SUNAT no los haya dado.
     */
    record BajaDeFicha(UUID id, String identificador, String condicion, String estado, String ticket, String motivo, LocalDate fechaGeneracion, LocalDate fechaReferencia,
                       boolean fueraDePlazo, RespuestaSunat cdr, int intentos, String ultimoError) {}

    /** {@code 404 NO_ENCONTRADO} si no existe. */
    Ficha ficha(UUID comprobanteId);
}
