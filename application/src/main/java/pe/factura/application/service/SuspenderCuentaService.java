package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.SuspenderCuentaUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.application.port.out.SuspensionRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class SuspenderCuentaService implements SuspenderCuentaUseCase {
    private final CuentaRepository cuentas;
    private final SuspensionRepository suspensiones;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public EstadoDeCuenta suspender(ActorAdmin actor, UUID cuentaId, String motivo) {
        exigirQueExista(cuentaId);
        String m = motivo == null || motivo.isBlank() ? null : motivo.strip();
        if (m != null && m.length() > MOTIVO_MAX)
            throw new DomainException("MOTIVO_INVALIDO", "El motivo no puede pasar de " + MOTIVO_MAX + " caracteres");
        Instant ahora = clock.instant();
        // El cambio es condicional (solo si estaba activa) y va en la misma transacción que la bitácora: un doble clic no suspende dos veces
        // ni deja dos registros, y si la bitácora falla la cuenta tampoco queda suspendida.
        uow.ejecutar(() -> {
            if (!suspensiones.suspender(cuentaId, ahora)) throw new DomainException("CUENTA_YA_SUSPENDIDA", "La cuenta ya está suspendida");
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.SUSPENDER_CUENTA, cuentaId, null, m == null ? null : "motivo=" + m, ahora));
        });
        return new EstadoDeCuenta(cuentaId, ahora);
    }

    @Override public EstadoDeCuenta reactivar(ActorAdmin actor, UUID cuentaId) {
        exigirQueExista(cuentaId);
        Instant ahora = clock.instant();
        uow.ejecutar(() -> {
            if (!suspensiones.reactivar(cuentaId)) throw new DomainException("CUENTA_NO_SUSPENDIDA", "La cuenta no está suspendida");
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.REACTIVAR_CUENTA, cuentaId, null, null, ahora));
        });
        return new EstadoDeCuenta(cuentaId, null);
    }

    private void exigirQueExista(UUID cuentaId) {
        if (cuentaId == null || cuentas.buscar(cuentaId).isEmpty()) throw new DomainException("NO_ENCONTRADO", "La cuenta no existe");
    }
}
