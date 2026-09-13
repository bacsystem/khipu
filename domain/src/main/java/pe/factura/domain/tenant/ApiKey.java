package pe.factura.domain.tenant;
import java.util.UUID;
public record ApiKey(UUID id, UUID tenantId, String hash, String prefijo, boolean activa) {}
