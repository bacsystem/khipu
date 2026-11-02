package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarConsumoUseCase;
import pe.factura.application.port.out.ConsumoRepository;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CicloMensual;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Consumo mensual (#192). Es de solo lectura: no hay nada que registrar en la bitácora. El mes en curso es el calendario de Lima ({@link CicloMensual}), el
 * mismo en que entran los cambios de límites de un plan.
 */
@RequiredArgsConstructor
public class ConsultarConsumoService implements ConsultarConsumoUseCase {
    private final ConsumoRepository consumos;
    private final CuentaRepository cuentas;
    private final TenantRepository tenants;
    private final Clock clock;

    @Override public YearMonth mesActual() { return YearMonth.now(clock.withZone(CicloMensual.ZONA)); }

    @Override public ConsumoDeEmpresa deEmpresa(UUID tenantId, YearMonth mes) {
        Tenant t = (tenantId == null ? java.util.Optional.<Tenant>empty() : tenants.buscar(tenantId)).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "La empresa no existe"));
        YearMonth m = mes == null ? mesActual() : mes;
        return new ConsumoDeEmpresa(t.id(), t.ruc(), t.razonSocial(), m, consumos.documentosDeEmpresa(t.id(), m));
    }

    @Override public ConsumoDeCuenta deCuenta(UUID cuentaId, YearMonth mes) {
        if (cuentaId == null || cuentas.buscar(cuentaId).isEmpty()) throw new DomainException("NO_ENCONTRADO", "La cuenta no existe");
        YearMonth m = mes == null ? mesActual() : mes;
        List<ConsumoDeEmpresa> empresas = consumos.documentosPorEmpresaDeCuenta(cuentaId, m).stream()
                .map(e -> new ConsumoDeEmpresa(e.tenantId(), e.ruc(), e.razonSocial(), m, e.documentos())).toList();
        return new ConsumoDeCuenta(cuentaId, m, empresas.stream().mapToLong(ConsumoDeEmpresa::documentos).sum(), empresas);
    }
}
