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
import pe.factura.domain.plan.PeriodoDelTope;
import pe.factura.domain.plan.Plan;
import pe.factura.domain.tenant.Entorno;
import pe.factura.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que el plan deja emitir. **Vencido** (pasados sus días de gracia, #285), la cuenta no emite nada hasta que lo renueve; en gracia sigue emitiendo. Después, cada
 * entorno tiene su tope, y lo emitido en uno no ocupa el del otro:
 * <ul>
 *   <li>**Producción**, el tope de documentos (#18, #580): al mes calendario de la fecha de emisión, o en toda la vida de la cuenta si el plan lo tiene en total
 *   (#613, Gratis). Al llegar a él la cuenta no emite más (facturas, boletas ni notas) hasta el mes siguiente —el total no se renueva— o hasta que le suban el plan.</li>
 *   <li>**Beta**, el de pruebas (#613): las pruebas por RUC del plan por sus RUC, al mes. Al llegar, {@code LIMITE_PRUEBAS}, distinto de {@code LIMITE_PLAN} para
 *   que un integrador sepa que lo que se acabó son las pruebas.</li>
 * </ul>
 * Se cuenta por cuenta, sumando todas sus empresas. Las empresas sin cuenta (integraciones) no se controlan, y un tope ilimitado solo por el vencimiento.
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
        // Sin la empresa a mano (no debería pasar al emitir) se controla como Producción: ante la duda, el tope de documentos manda.
        Entorno entorno = tenants.buscar(tenantId).map(Tenant::entorno).orElse(Entorno.PRODUCCION);
        if (entorno.consumeElPlan()) exigirDocumentos(cuenta.get(), plan, fechaEmision);
        else exigirPruebas(cuenta.get(), plan, fechaEmision, entorno);
    }

    private void exigirDocumentos(UUID cuenta, Plan plan, LocalDate fechaEmision) {
        Limite tope = plan.limites().documentosAlMes();
        if (tope.ilimitado()) return;
        if (plan.limites().periodoDeDocumentos() == PeriodoDelTope.TOTAL) {
            if (ocupados.ocupadosBloqueando(cuenta, Entorno.PRODUCCION, null) < tope.maximo()) return;
            throw new DomainException("LIMITE_PLAN", "Usaste los " + tope.maximo() + " comprobantes reales de tu plan " + plan.nombre()
                    + ", que no se renuevan. Para seguir emitiendo, pide un cambio de plan; las pruebas en Beta siguen disponibles");
        }
        YearMonth mes = YearMonth.from(fechaEmision);
        if (ocupados.ocupadosBloqueando(cuenta, Entorno.PRODUCCION, mes) < tope.maximo()) return;
        throw new DomainException("LIMITE_PLAN", "Llegaste al tope de " + tope.maximo() + " documentos de " + mes.format(MES) + " de tu plan " + plan.nombre()
                + ". Se renueva el " + renueva(mes) + "; para emitir antes, pide un cambio de plan");
    }

    private void exigirPruebas(UUID cuenta, Plan plan, LocalDate fechaEmision, Entorno entorno) {
        Limite tope = plan.limites().pruebasBetaAlMes();
        if (tope.ilimitado()) return;
        YearMonth mes = YearMonth.from(fechaEmision);
        if (ocupados.ocupadosBloqueando(cuenta, entorno, mes) < tope.maximo()) return;
        throw new DomainException("LIMITE_PRUEBAS", "Llegaste a las " + tope.maximo() + " pruebas en Beta de " + mes.format(MES) + " de tu plan " + plan.nombre()
                + " (" + plan.limites().pruebasBetaPorRuc().maximo() + " por cada RUC del plan). Se renuevan el " + renueva(mes)
                + "; para probar más antes, pide un cambio de plan");
    }

    private static String renueva(YearMonth mes) { return mes.plusMonths(1).atDay(1).format(RENUEVA); }

    /** {@code hasta} es exclusivo: el último día que se sirvió es el anterior, en hora de Lima. */
    private static DomainException vencido(Plan plan, Instant hasta) {
        String ultimoDia = hasta.atZone(CicloMensual.ZONA).minusNanos(1).toLocalDate().format(RENUEVA);
        return new DomainException("PLAN_VENCIDO", "Tu plan " + plan.nombre() + " venció y su gracia terminó el " + ultimoDia
                + ": renuévalo para volver a emitir. Lo ya emitido se puede seguir consultando y dar de baja");
    }
}
