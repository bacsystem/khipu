package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.DetalleCuentaAdminUseCase;
import pe.factura.application.port.out.CuentasAdminRepository;
import pe.factura.domain.DomainException;

import java.util.UUID;

@RequiredArgsConstructor
public class DetalleCuentaAdminService implements DetalleCuentaAdminUseCase {
    private final CuentasAdminRepository cuentas;

    @Override public CuentaDetalle detalle(UUID cuentaId) {
        if (cuentaId == null) throw noExiste();
        return cuentas.detalle(cuentaId).orElseThrow(DetalleCuentaAdminService::noExiste);
    }

    private static DomainException noExiste() { return new DomainException("NO_ENCONTRADO", "La cuenta no existe"); }
}
