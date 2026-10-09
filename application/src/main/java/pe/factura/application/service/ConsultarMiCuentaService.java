package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.in.ConsultarConsumoUseCase;
import pe.factura.application.port.in.ConsultarMiCuentaUseCase;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;

import java.util.UUID;

/** Junta, para el portal del cliente, lo que el backoffice ya sabe de la cuenta: su nombre, su plan (#191) y su consumo del mes (#192). */
@RequiredArgsConstructor
public class ConsultarMiCuentaService implements ConsultarMiCuentaUseCase {
    private final CuentaRepository cuentas;
    private final CambiarPlanDeCuentaUseCase planes;
    private final ConsultarConsumoUseCase consumo;

    @Override
    public MiCuenta deLaCuenta(UUID cuentaId) {
        Cuenta cuenta = cuentas.buscar(cuentaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Cuenta no encontrada"));
        return new MiCuenta(cuenta.id(), cuenta.nombre(), planes.plan(cuentaId), consumo.deCuenta(cuentaId, null));
    }
}
