package pe.factura.domain.tenant;

import java.time.Instant;
import java.util.UUID;

/** Solo se guarda el hash; el prefijo permite identificar la key en listados sin exponer el secreto. */
public record ApiKey(UUID id, UUID tenantId, String hash, String prefijo, boolean activa, Instant creadaEn, Instant revocadaEn) {
    public ApiKey revocar(Instant en) { return new ApiKey(id, tenantId, hash, prefijo, false, creadaEn, en); }
}
