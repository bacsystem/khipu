package pe.factura.application.port.in;

import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.util.List;
import java.util.UUID;

/** Empresas (tenants) de una cuenta del portal. */
public interface GestionarEmpresasUseCase {
    List<Tenant> listar(UUID cuentaId);
    Tenant crear(UUID cuentaId, String ruc, String razonSocial, Entorno entorno);
    /** Lanza DomainException("EMPRESA_AJENA") si la empresa no pertenece a la cuenta. */
    void exigirPertenencia(UUID cuentaId, UUID tenantId);
}
