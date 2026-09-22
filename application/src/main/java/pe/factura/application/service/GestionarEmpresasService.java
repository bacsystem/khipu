package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.GestionarEmpresasUseCase;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class GestionarEmpresasService implements GestionarEmpresasUseCase {
    private final TenantRepository tenants;
    private final CuentaRepository cuentas;
    private final UnitOfWork uow;

    @Override public List<Tenant> listar(UUID cuentaId) { return tenants.listarPorCuenta(cuentaId); }

    @Override
    public Tenant crear(UUID cuentaId, String ruc, String razonSocial, Entorno entorno) {
        cuentas.buscar(cuentaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Cuenta no encontrada"));
        pe.factura.domain.tenant.Ruc.exigirValido(ruc, "RUC_INVALIDO", "Empresa");
        if (tenants.buscarPorRuc(ruc).isPresent()) throw new DomainException("DUPLICADO", "Ya existe una empresa con RUC " + ruc);
        Tenant t = new Tenant(UUID.randomUUID(), ruc, razonSocial, entorno == null ? Entorno.BETA : entorno, null, null);
        uow.ejecutar(() -> { tenants.guardar(t); tenants.asignarCuenta(t.id(), cuentaId); });
        return t;
    }

    @Override
    public void exigirPertenencia(UUID cuentaId, UUID tenantId) {
        if (!tenants.cuentaDe(tenantId).filter(cuentaId::equals).isPresent())
            throw new DomainException("EMPRESA_AJENA", "La empresa no pertenece a la cuenta");
    }
}
