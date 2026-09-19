package pe.factura.application.port.out;

import pe.factura.domain.tenant.Establecimiento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EstablecimientoRepository {
    void guardar(Establecimiento e); // insert o update por (tenant, código)
    Optional<Establecimiento> buscar(UUID tenantId, String codigo);
    /** Anexos de la empresa (activos e inactivos), por código; el 0000 no está aquí: es el domicilio del tenant. */
    List<Establecimiento> listar(UUID tenantId);
}
