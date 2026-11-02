package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase.PlanDeCuenta;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.TopeDeDocumentosRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CicloMensual;
import pe.factura.domain.plan.EstadoSuscripcion;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Plan;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que el plan deja emitir. **Vencido** (pasados sus días de gracia, #285), la cuenta no emite nada hasta que lo renueve; en gracia sigue emitiendo. El **tope de
 * documentos al mes** (#18): al llegar a él, la cuenta no emite más (facturas, boletas ni notas) hasta el mes siguiente o hasta que le suban el plan. Se cuenta por
 * cuenta, sumando todas sus empresas, en el mes de la fecha de emisión, como el consumo. Las empresas sin cuenta (integraciones) no se controlan, y los planes sin tope
 * solo por el vencimiento.
 */
@RequiredArgsConstructor
public class TopeDelPlanService implements TopeDelPlan {
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter RENUEVA = DateTimeFormatter.ofPattern("d 'de' MMMM", ES);

    private final TenantRepository tenants;
    private final CambiarPlanDeCuentaUseCase planes;
    private final TopeDeDocumentosRepository ocupados;
    private final Clock clock;

    @Override
    public void exigirDisponible(UUID tenantId, LocalDate fechaEmision) {
        Optional<UUID> cuenta = tenants.cuentaDe(tenantId);
        if (cuenta.isEmpty()) return;
        Instant ahora = clock.instant();
        PlanDeCuenta deLaCuenta = planes.plan(cuenta.get());
        Plan plan = deLaCuenta.planQueMandaEn(ahora);
        // Antes del tope: vencido no emite aunque le quede lugar, y no hace falta contar ni bloquear la cuenta para decirlo.
        if (deLaCuenta.estadoEn(ahora) == EstadoSuscripcion.VENCIDA) throw vencido(plan, deLaCuenta.hastaCuandoCubreEn(ahora));
        Limite tope = plan.limites().documentosAlMes();
        if (tope.ilimitado()) return;
        YearMonth mes = YearMonth.from(fechaEmision);
        if (ocupados.ocupadosBloqueando(cuenta.get(), mes) < tope.maximo()) return;
        throw new DomainException("LIMITE_PLAN", "Llegaste al tope de " + tope.maximo() + " documentos de " + mes.format(MES) + " de tu plan " + plan.nombre()
                + ". Se renueva el " + mes.plusMonths(1).atDay(1).format(RENUEVA) + "; para emitir antes, pide un cambio de plan");
    }

    /** {@code hasta} es exclusivo: el último día que se sirvió es el anterior, en hora de Lima. */
    private static DomainException vencido(Plan plan, Instant hasta) {
        String ultimoDia = hasta.atZone(CicloMensual.ZONA).minusNanos(1).toLocalDate().format(RENUEVA);
        return new DomainException("PLAN_VENCIDO", "Tu plan " + plan.nombre() + " venció y su gracia terminó el " + ultimoDia
                + ": renuévalo para volver a emitir. Lo ya emitido se puede seguir consultando y dar de baja");
    }
}
