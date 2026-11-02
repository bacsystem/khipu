package pe.factura.domain.plataforma;

import pe.factura.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

/**
 * Una entrada de la bitácora de auditoría: quién ({@link ActorAdmin}), qué ({@link AccionAdmin}), sobre qué cuenta o
 * empresa (ambas opcionales: hay acciones que no recaen en ninguna) y cuándo. {@code detalle} es contexto libre
 * y corto; nunca debe llevar secretos (contraseñas, API keys).
 */
public record RegistroAuditoria(UUID id, ActorAdmin actor, AccionAdmin accion, UUID cuentaId, UUID tenantId, String detalle, Instant ocurridoEn) {
    public RegistroAuditoria {
        if (id == null || actor == null || accion == null || ocurridoEn == null)
            throw new DomainException("AUDITORIA_INVALIDA", "Un registro de auditoría requiere id, actor, acción e instante");
    }

    public static RegistroAuditoria de(ActorAdmin actor, AccionAdmin accion, UUID cuentaId, UUID tenantId, String detalle, Instant ocurridoEn) {
        return new RegistroAuditoria(UUID.randomUUID(), actor, accion, cuentaId, tenantId, detalle, ocurridoEn);
    }
}
