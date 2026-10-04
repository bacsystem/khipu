package pe.factura.application.port.out;

import pe.factura.domain.tenant.Entorno;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que necesitan las acciones del administrador sobre una empresa (#187). Los dos cambios son condicionales (solo si el estado era el esperado), así
 * de dos pedidos a la vez solo uno cambia algo.
 */
public interface AccionesDeEmpresaRepository {
    Optional<Entorno> entornoDe(UUID tenantId);

    /** Cambia el entorno solo si seguía siendo {@code desde}; {@code false} si ya no lo era (o la empresa no existe). Solo toca esa columna. */
    boolean cambiarEntorno(UUID tenantId, Entorno desde, Entorno hacia);

    /** Tareas que el outbox todavía tiene que enviar o reintentar para la empresa. */
    long enviosPendientes(UUID tenantId);

    /** La key {@code apiKeyId} de esa empresa; vacío si no existe o es de otra empresa. */
    Optional<ApiKeyDeEmpresa> apiKey(UUID tenantId, UUID apiKeyId);

    /** Revoca la key solo si estaba activa y es de esa empresa; {@code false} si no cambió nada. */
    boolean revocarApiKey(UUID tenantId, UUID apiKeyId, Instant cuando);

    /** Solo el prefijo (la parte visible) y el estado: el hash de la clave ni se lee. */
    record ApiKeyDeEmpresa(String prefijo, boolean activa) {}
}
