package pe.factura.application.port.out;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * El consumo de documentos de un plan (#192). **Es código de dinero:** un documento consume solo si SUNAT lo aceptó (con o sin observaciones, aunque después
 * se diera de baja; {@code EstadoDocumento.cuentaParaElConsumo}) y en Producción ({@code Entorno.consumeElPlan}, #580: las pruebas en Beta no consumen), por su
 * fecha de emisión, dentro del mes calendario. No cuentan los rechazados, los errores de
 * envío ni los que están en camino; la comunicación de baja no suma otro documento, y un comprobante reintentado hasta que lo aceptan es uno solo (los
 * reintentos son un contador, no filas).
 */
public interface ConsumoRepository {
    /** El consumo de una empresa en un mes. */
    record ConsumoDeEmpresa(UUID tenantId, String ruc, String razonSocial, long documentos) {}

    /** Documentos que consumió la empresa en el mes; cero si no hay ninguno o la empresa no existe. */
    long documentosDeEmpresa(UUID tenantId, YearMonth mes);

    /** Documentos de Producción que consumió la cuenta, sumando todas sus empresas, en toda su vida (#613: un plan con tope en total). */
    long documentosDeCuentaEnTotal(UUID cuentaId);

    /**
     * Pruebas en Beta de la cuenta en el mes, sumando todas sus empresas (#613): las que SUNAT Beta aceptó, con la misma regla de estados que el consumo. No son
     * consumo (no se cobran): es lo que se mide contra el tope de pruebas del plan.
     */
    long pruebasDeCuenta(UUID cuentaId, YearMonth mes);

    /** Cada empresa de la cuenta —aunque no haya emitido nada— con lo que consumió en el mes, ordenadas por RUC. Vacía si la cuenta no tiene empresas. */
    List<ConsumoDeEmpresa> documentosPorEmpresaDeCuenta(UUID cuentaId, YearMonth mes);
}
