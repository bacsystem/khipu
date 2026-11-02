package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.DetalleEmpresaAdminUseCase;
import pe.factura.application.port.out.EmpresasAdminRepository;
import pe.factura.domain.DomainException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@RequiredArgsConstructor
public class DetalleEmpresaAdminService implements DetalleEmpresaAdminUseCase {
    private final EmpresasAdminRepository empresas;
    private final Clock clock;

    @Override public EmpresaDetalle detalle(UUID empresaId) {
        if (empresaId == null) throw noExiste();
        return empresas.detalle(empresaId, LocalDate.now(clock)).orElseThrow(DetalleEmpresaAdminService::noExiste);
    }

    private static DomainException noExiste() { return new DomainException("NO_ENCONTRADO", "La empresa no existe"); }
}
