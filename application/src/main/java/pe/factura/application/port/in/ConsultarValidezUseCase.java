package pe.factura.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface ConsultarValidezUseCase {
    record Criterios(String rucEmisor, String tipo, String serie, long numero, String tipoDocReceptor, String numDocReceptor, LocalDate fechaEmision, BigDecimal importeTotal) {}
    /** Estado que SUNAT informa de un comprobante (propio o de un tercero) por sus datos, vía billValidService. */
    record Validez(String estado, String codigo, String mensaje) {}
    Validez consultar(UUID tenantId, Criterios criterios);
}
