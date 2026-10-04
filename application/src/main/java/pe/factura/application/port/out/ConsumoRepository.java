package pe.factura.application.port.out;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * El consumo de documentos de un plan (#192). **Es código de dinero:** un documento consume solo si SUNAT lo aceptó (con o sin observaciones;
 * {@code EstadoDocumento.cuentaParaElConsumo}), por su fecha de emisión, dentro del mes calendario. No cuentan los rechazados, los errores de envío, los que
 * están en camino ni los dados de baja, y un comprobante reintentado hasta que lo aceptan es uno solo (los reintentos son un contador, no filas).
 */
public interface ConsumoRepository {
    /** El consumo de una empresa en un mes. */
    record ConsumoDeEmpresa(UUID tenantId, String ruc, String razonSocial, long documentos) {}

    /** Documentos que consumió la empresa en el mes; cero si no hay ninguno o la empresa no existe. */
    long documentosDeEmpresa(UUID tenantId, YearMonth mes);

    /** Cada empresa de la cuenta —aunque no haya emitido nada— con lo que consumió en el mes, ordenadas por RUC. Vacía si la cuenta no tiene empresas. */
    List<ConsumoDeEmpresa> documentosPorEmpresaDeCuenta(UUID cuentaId, YearMonth mes);
}
