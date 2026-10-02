package pe.factura.application.port.out;

import pe.factura.domain.plataforma.RegistroAuditoria;

/**
 * Bitácora de las acciones del administrador de la plataforma. Quien la use debe llamarla dentro del mismo
 * {@link UnitOfWork} que la acción auditada: si la escritura falla, la acción no debe quedar hecha.
 */
public interface AuditoriaAdminRepository {
    void registrar(RegistroAuditoria registro);
}
