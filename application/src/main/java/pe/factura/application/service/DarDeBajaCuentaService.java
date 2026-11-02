package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.DarDeBajaCuentaUseCase;
import pe.factura.application.port.out.AuditoriaAdminRepository;
import pe.factura.application.port.out.BajaDeCuentaRepository;
import pe.factura.application.port.out.CuentaRepository;
import pe.factura.application.port.out.UnitOfWork;
import pe.factura.domain.DomainException;
import pe.factura.domain.plataforma.AccionAdmin;
import pe.factura.domain.plataforma.ActorAdmin;
import pe.factura.domain.plataforma.RegistroAuditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class DarDeBajaCuentaService implements DarDeBajaCuentaUseCase {
    private final CuentaRepository cuentas;
    private final BajaDeCuentaRepository bajas;
    private final AuditoriaAdminRepository auditoria;
    private final UnitOfWork uow;
    private final Clock clock;

    @Override public EstadoDeBaja darDeBaja(ActorAdmin actor, UUID cuentaId, String motivo) {
        exigirQueExista(cuentaId);
        String m = motivo == null || motivo.isBlank() ? null : motivo.strip();
        if (m != null && m.length() > MOTIVO_MAX)
            throw new DomainException("MOTIVO_INVALIDO", "El motivo no puede pasar de " + MOTIVO_MAX + " caracteres");
        Instant ahora = clock.instant();
        // El cambio es condicional (solo si no estaba de baja) y va en la misma transacción que la bitácora: un doble clic no la da de baja dos
        // veces ni deja dos registros, y si la bitácora falla la cuenta tampoco queda de baja.
        uow.ejecutar(() -> {
            if (!bajas.darDeBaja(cuentaId, ahora)) throw new DomainException("CUENTA_YA_DE_BAJA", "La cuenta ya está dada de baja");
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.DAR_DE_BAJA_CUENTA, cuentaId, null, m == null ? null : "motivo=" + m, ahora));
        });
        return new EstadoDeBaja(cuentaId, ahora);
    }

    @Override public EstadoDeBaja reponer(ActorAdmin actor, UUID cuentaId) {
        exigirQueExista(cuentaId);
        Instant ahora = clock.instant();
        uow.ejecutar(() -> {
            if (!bajas.reponer(cuentaId)) throw new DomainException("CUENTA_NO_DE_BAJA", "La cuenta no está dada de baja");
            auditoria.registrar(RegistroAuditoria.de(actor, AccionAdmin.REPONER_CUENTA, cuentaId, null, null, ahora));
        });
        return new EstadoDeBaja(cuentaId, null);
    }

    private void exigirQueExista(UUID cuentaId) {
        if (cuentaId == null || cuentas.buscar(cuentaId).isEmpty()) throw new DomainException("NO_ENCONTRADO", "La cuenta no existe");
    }
}
