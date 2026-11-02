package pe.factura.application.port.in;

/**
 * Clave de idempotencia de una operación (#115, #219): {@code clave} la elige el cliente por intento; {@code huella} identifica el
 * contenido del pedido, para no aceptar la misma clave con otro contenido.
 */
public record Idempotencia(String clave, String huella) {}
