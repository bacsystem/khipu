package pe.factura.application.port.in;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Cuántos documentos consumió una empresa o una cuenta en un mes (#192). El mes es el calendario en America/Lima; sin indicarlo, el mes en curso. Solo cuentan los
 * comprobantes que SUNAT aceptó: ver {@code ConsumoRepository}.
 */
public interface ConsultarConsumoUseCase {
    record ConsumoDeEmpresa(UUID tenantId, String ruc, String razonSocial, YearMonth mes, long documentos) {}

    /** El consumo de una cuenta: el total y el detalle por empresa (cada una, aunque no haya emitido nada). */
    record ConsumoDeCuenta(UUID cuentaId, YearMonth mes, long documentos, List<ConsumoDeEmpresa> empresas) {}

    /** El mes en curso, en hora de Lima. */
    YearMonth mesActual();

    /** {@code NO_ENCONTRADO} si la empresa no existe. {@code mes} nulo es el mes en curso. */
    ConsumoDeEmpresa deEmpresa(UUID tenantId, YearMonth mes);

    /** {@code NO_ENCONTRADO} si la cuenta no existe. El total es la suma de sus empresas. {@code mes} nulo es el mes en curso. */
    ConsumoDeCuenta deCuenta(UUID cuentaId, YearMonth mes);
}
