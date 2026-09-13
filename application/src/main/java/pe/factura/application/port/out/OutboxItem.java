package pe.factura.application.port.out;

import java.util.UUID;

public record OutboxItem(UUID id, UUID tenantId, UUID agregadoId, String accion, int intentos) {}
