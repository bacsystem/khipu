package pe.factura.application.port.out;

import pe.factura.domain.documento.TipoDocumento;
import pe.factura.domain.tenant.Establecimiento;

import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve, en una sola consulta, el establecimiento asignado a una serie de emisión —une los aggregates de {@code Serie}
 * y {@code Establecimiento} sin acoplar sus repositorios entre sí—. Vacío si la serie no existe o está en el domicilio
 * fiscal ({@code 0000}); si está asignada a un código sin establecimiento registrado, {@link Asignacion#establecimiento()}
 * viene nulo para que el caller decida (EmisorDeSerie.paraEmitir rechaza, paraImprimir cae al domicilio fiscal).
 */
public interface EmisorDeSerieRepository {
    Optional<Asignacion> buscarAsignacionDeSerie(UUID tenantId, TipoDocumento tipo, String serie);

    /** {@code codigo}: establecimiento asignado a la serie (nunca {@code 0000}); {@code establecimiento}: nulo si ese código no existe. */
    record Asignacion(String codigo, Establecimiento establecimiento) {}
}
