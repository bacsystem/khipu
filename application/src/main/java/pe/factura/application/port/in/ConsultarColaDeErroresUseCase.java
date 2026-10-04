package pe.factura.application.port.in;

import pe.factura.domain.documento.ClaseDeError;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.documento.FaultSunat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La cola global de errores del backoffice (#196): los comprobantes con problema de **todas** las empresas en una sola lista, cada uno con el fault de SUNAT y los intentos que
 * lleva. Lectura pura. Qué cuenta como problema lo decide {@link ClaseDeError}.
 */
public interface ConsultarColaDeErroresUseCase {
    /**
     * {@code clase} y {@code empresaId} acotan por tipo de error y por empresa (nulos = todas). {@code texto} busca en el RUC y la razón social de la empresa, sin distinguir
     * mayúsculas ni tildes (nulo o en blanco = sin búsqueda).
     */
    record Filtro(ClaseDeError clase, UUID empresaId, String texto) {}

    /**
     * Un comprobante con problema. {@code fault} es nulo si no hay nada que mostrar. {@code proximoIntento}: cuándo lo reintentará el trabajo del outbox; nulo si no hay un
     * reintento programado. {@code accionable}: un administrador puede reintentar o descartar (solo un error de envío; las otras dos clases ya son terminales).
     */
    record ErrorDeEmision(UUID comprobanteId, UUID empresaId, String ruc, String razonSocial, UUID cuentaId, String cuentaNombre, String nombreArchivo, String tipo, String serie,
                          long numero, LocalDate fechaEmision, EstadoDocumento estado, ClaseDeError clase, int intentos, FaultSunat fault, Instant proximoIntento, Instant actualizadoEn,
                          boolean accionable) {}

    record Pagina(List<ErrorDeEmision> errores, long total) {}

    Pagina listar(Filtro filtro, int pagina, int porPagina);
}
