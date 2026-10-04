package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.ConsultarPagosUseCase;
import pe.factura.application.port.in.RegistrarPagoUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.PagoRepository;
import pe.factura.application.port.out.SuscripcionRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.plan.CicloMensual;
import pe.factura.domain.plan.Pago;
import pe.factura.domain.plan.Suscripcion;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Los pagos que se registran a mano (#194). El pago, la extensión del vencimiento (si se pidió) y la bitácora van en **una** transacción: si cualquiera falla no queda
 * ninguna. La extensión es condicional (solo si el vencimiento sigue siendo el que se vio), así dos administradores que pagan a la vez no se pisan. La bitácora no lleva la
 * referencia ni la nota, que son texto libre del administrador: están en el pago.
 */
@RequiredArgsConstructor
public class PagosDeCuentaService implements RegistrarPagoUseCase, ConsultarPagosUseCase {
    private final PagoRepository pagos;
    private final SuscripcionRepository suscripciones;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public Pago registrar(ActorAdmin actor, UUID cuentaId, Comando c) {
        if (c == null) throw new DomainException("PAGO_INVALIDO", "Falta el pago que se quiere registrar");
        Instant ahora = clock.instant();
        return uow.ejecutar(() -> {
            Suscripcion activa = suscripciones.deLaCuenta(cuentaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "La cuenta no existe")).activa();
            Pago pago = Pago.registrar(UUID.randomUUID(), cuentaId, activa.id(), c.periodoDesde(), c.periodoHasta(), c.monto(), c.medio(), c.fechaDePago(), c.referencia(), c.nota(), ahora, null);
            if (pago.fechaDePago().isAfter(LocalDate.ofInstant(ahora, CicloMensual.ZONA)))
                throw new DomainException("FECHA_DE_PAGO_FUTURA", "La fecha de pago no puede ser futura: " + pago.fechaDePago());
            Instant nuevoVencimiento = c.extenderVencimiento() ? vencimientoNuevo(activa, pago) : null;
            if (nuevoVencimiento != null) pago = pago.conExtension(nuevoVencimiento);
            if (!pagos.registrar(pago))
                throw new DomainException("PAGO_DUPLICADO", "Esa cuenta ya tiene un pago por " + pago.medio() + " con la referencia «" + pago.referencia() + "»");
            if (nuevoVencimiento != null && !suscripciones.extenderVencimiento(activa.id(), activa.venceEn(), nuevoVencimiento))
                throw new DomainException("CAMBIO_CONCURRENTE", "Otro administrador movió el vencimiento de la cuenta mientras tanto: vuelve a mirarla");
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.REGISTRAR_PAGO, cuentaId, null, detalle(pago), ahora));
            return pago;
        });
    }

    @Override public Pagina deLaCuenta(UUID cuentaId, int pagina, int porPagina) {
        suscripciones.deLaCuenta(cuentaId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "La cuenta no existe"));
        return new Pagina(pagos.deLaCuenta(cuentaId, pagina, porPagina), pagos.contarDeLaCuenta(cuentaId));
    }

    /** El vencimiento al que llegaría la cuenta con este pago; falla si el plan no vence o si el pago no lo adelanta. */
    private static Instant vencimientoNuevo(Suscripcion activa, Pago pago) {
        if (activa.venceEn() == null) throw new DomainException("PLAN_SIN_VENCIMIENTO", "El plan de la cuenta no vence: no hay vencimiento que extender");
        Instant nuevo = pago.venceriaEn();
        if (!nuevo.isAfter(activa.venceEn()))
            throw new DomainException("EXTENSION_SIN_EFECTO", "La cuenta ya está pagada hasta esa fecha o más: el pago no extiende nada. Regístralo sin extender el vencimiento");
        return nuevo;
    }

    private static String detalle(Pago p) {
        return "pago=" + p.id() + " periodo=" + p.periodoDesde() + "/" + p.periodoHasta() + " monto=" + p.monto().toPlainString() + " medio=" + p.medio() + " fecha=" + p.fechaDePago()
                + " vence=" + (p.extendioHasta() == null ? "sin_cambio" : p.extendioHasta().atZone(CicloMensual.ZONA).toLocalDate());
    }
}
