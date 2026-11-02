package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.in.ConsultarConsumoUseCase;
import pe.factura.application.port.in.ConsultarMiCuentaUseCase;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.application.port.out.CupoDeCuentaRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;

import java.time.Clock;
import java.util.UUID;

/** Junta, para el portal del cliente, lo que el backoffice ya sabe de la cuenta: su nombre, su plan (#191) y su consumo del mes (#192) y lo que ocupa de los límites que no son documentos (#498). */
@RequiredArgsConstructor
public class ConsultarMiCuentaService implements ConsultarMiCuentaUseCase {
    private final CuentaRepository cuentas;
    private final CambiarPlanDeCuentaUseCase planes;
    private final ConsultarConsumoUseCase consumo;
    private final CupoDeCuentaRepository ocupados;
    private final Clock clock;

    @Override
    public MiCuenta deLaCuenta(UUID cuentaId) {
        Cuenta cuenta = cuentas.buscar(cuentaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Cuenta no encontrada"));
        return new MiCuenta(cuenta.id(), cuenta.nombre(), planes.plan(cuentaId), consumo.deCuenta(cuentaId, null), ocupados.uso(cuentaId, clock.instant()));
    }
}
