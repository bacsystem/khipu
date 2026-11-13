package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.in.ConsultarConsumoUseCase;
import pe.factura.application.port.in.ConsultarMiCuentaUseCase;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeCuenta;
import pe.factura.application.port.out.ConsumoRepository;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.application.port.out.CupoDeCuentaRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.cuenta.Cuenta;

import java.time.Clock;
import java.util.UUID;

/** Junta, para el portal del cliente, lo que el backoffice ya sabe de la cuenta: su nombre, su plan (#191) y su consumo del mes (#192) o en total y sus pruebas en Beta (#613), y lo que ocupa de los límites que no son documentos (#498). */
@RequiredArgsConstructor
public class ConsultarMiCuentaService implements ConsultarMiCuentaUseCase {
    private final CuentaRepository cuentas;
    private final CambiarPlanDeCuentaUseCase planes;
    private final ConsultarConsumoUseCase consumo;
    private final CupoDeCuentaRepository ocupados;
    private final ConsumoRepository documentos;
    private final Clock clock;

    @Override
    public MiCuenta deLaCuenta(UUID cuentaId) {
        Cuenta cuenta = cuentas.buscar(cuentaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Cuenta no encontrada"));
        ConsumoDeCuenta delMes = consumo.deCuenta(cuentaId, null);
        return new MiCuenta(cuenta.id(), cuenta.nombre(), planes.plan(cuentaId), delMes, ocupados.uso(cuentaId, clock.instant()),
                documentos.documentosDeCuentaEnTotal(cuentaId), documentos.pruebasDeCuenta(cuentaId, delMes.mes()));
    }
}
