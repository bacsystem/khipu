package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CambiarPlanDeCuentaUseCase;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.application.port.out.TopeDeDocumentosRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Plan;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * El tope de documentos al mes del plan (#18): al llegar a él, la cuenta no emite más (facturas, boletas ni notas) hasta el mes siguiente o hasta que le suban el
 * plan. Se cuenta por cuenta, sumando todas sus empresas, en el mes de la fecha de emisión, como el consumo. Las empresas sin cuenta (integraciones) y los planes
 * sin tope no se controlan.
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
        Plan plan = planes.plan(cuenta.get()).planQueMandaEn(clock.instant());
        Limite tope = plan.limites().documentosAlMes();
        if (tope.ilimitado()) return;
        YearMonth mes = YearMonth.from(fechaEmision);
        if (ocupados.ocupadosBloqueando(cuenta.get(), mes) < tope.maximo()) return;
        throw new DomainException("LIMITE_PLAN", "Llegaste al tope de " + tope.maximo() + " documentos de " + mes.format(MES) + " de tu plan " + plan.nombre()
                + ". Se renueva el " + mes.plusMonths(1).atDay(1).format(RENUEVA) + "; para emitir antes, pide un cambio de plan");
    }
}
