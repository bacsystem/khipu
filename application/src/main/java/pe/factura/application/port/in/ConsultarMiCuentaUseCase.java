package pe.factura.application.port.in;

import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.PlanDeCuenta;
import pe.factura.application.port.in.ConsultarConsumoUseCase.ConsumoDeCuenta;

import pe.factura.domain.plan.UsoDeRecursos;

import java.util.UUID;

/**
 * Lo que el cliente ve de su propia cuenta en el portal (C1, C7): cómo se llama, qué plan tiene y hasta cuándo, y cuánto consumió este mes contra su tope, y cuántas API keys, usuarios y RUCs ocupa contra los de su plan (#498). Lectura
 * pura: cambiar de plan lo hace el administrador (#191).
 */
public interface ConsultarMiCuentaUseCase {
    record MiCuenta(UUID cuentaId, String nombre, PlanDeCuenta plan, ConsumoDeCuenta consumo, UsoDeRecursos uso) {}

    /** {@code NO_ENCONTRADO} si la cuenta no existe. El consumo es el del mes en curso (Lima). */
    MiCuenta deLaCuenta(UUID cuentaId);
}
