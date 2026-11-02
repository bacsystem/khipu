package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarConsumoDeCuentasUseCase;
import pe.factura.application.port.in.FiltroDeConsumo;
import pe.factura.application.port.in.OrdenDeConsumo;
import pe.factura.application.port.out.ConsumoPorCuentaRepository;
import pe.factura.application.port.out.ConsumoPorCuentaRepository.Consulta;
import pe.factura.application.port.out.ConsumoPorCuentaRepository.Registro;
import pe.factura.domain.plan.CicloMensual;
import pe.factura.domain.plan.Limite;
import pe.factura.domain.plan.Suscripcion;
import pe.factura.domain.plan.UsoDeLimite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * La tabla de consumo de todas las cuentas (#193). Es de solo lectura: no hay nada que registrar en la bitácora. El porcentaje y la alerta salen de {@link UsoDeLimite}
 * y el estado de pago de {@link Suscripcion#estadoDeLaVigente}, las mismas definiciones que el resto del backoffice; el repositorio ya filtra y ordena con ellas.
 */
@RequiredArgsConstructor
public class ConsultarConsumoDeCuentasService implements ConsultarConsumoDeCuentasUseCase {
    private final ConsumoPorCuentaRepository consumos;
    private final Clock clock;

    @Override public YearMonth mesActual() { return YearMonth.now(clock.withZone(CicloMensual.ZONA)); }

    @Override public List<Fila> listar(YearMonth mes, FiltroDeConsumo filtro, OrdenDeConsumo orden, int pagina, int porPagina) {
        Consulta q = consulta(mes, filtro, orden);
        return consumos.listar(q, pagina, porPagina).stream().map(r -> fila(r, q.ahora())).toList();
    }

    @Override public long contar(YearMonth mes, FiltroDeConsumo filtro) { return consumos.contar(consulta(mes, filtro, null)); }

    @Override public List<Fila> todas(YearMonth mes, FiltroDeConsumo filtro, OrdenDeConsumo orden) {
        Consulta q = consulta(mes, filtro, orden);
        return consumos.todas(q).stream().map(r -> fila(r, q.ahora())).toList();
    }

    private Consulta consulta(YearMonth mes, FiltroDeConsumo filtro, OrdenDeConsumo orden) {
        return new Consulta(mes == null ? mesActual() : mes, clock.instant(), filtro == null ? FiltroDeConsumo.TODAS : filtro, orden == null ? OrdenDeConsumo.PORCENTAJE : orden,
                UsoDeLimite.UMBRAL_DE_ALERTA);
    }

    private static Fila fila(Registro r, Instant ahora) {
        Limite tope = r.limite() == null ? Limite.sinLimite() : Limite.de(r.limite());
        return new Fila(r.cuentaId(), r.nombre(), r.email(), r.planId(), r.planNombre(), r.documentos(), tope, UsoDeLimite.porcentaje(r.documentos(), tope).stream().boxed().findFirst().orElse(null),
                UsoDeLimite.enAlerta(r.documentos(), tope), Suscripcion.estadoDeLaVigente(r.venceEn(), r.diasDeGracia(), ahora), r.venceEn(), r.diasDeGracia(),
                r.venceEn() == null ? null : r.venceEn().plus(Duration.ofDays(r.diasDeGracia())));
    }
}
