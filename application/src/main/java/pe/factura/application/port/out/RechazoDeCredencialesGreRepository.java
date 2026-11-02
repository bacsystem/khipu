package pe.factura.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * SUNAT rechazó las credenciales de la guía de remisión de la empresa (#39, H3): las API SUNAT o la clave SOL, en el token del grant password. {@code huella}
 * identifica las credenciales rechazadas sin guardarlas: cuando la empresa las cambia, la huella ya no coincide y la marca deja de valer sola. Como en las facturas
 * (#107), mientras valga khipu no pide otro token.
 */
public interface RechazoDeCredencialesGreRepository {
    record Rechazo(String huella, String motivo, Instant en) {}

    /** Marca o reemplaza la marca de la empresa. */
    void marcar(UUID tenantId, String huella, String motivo, Instant en);

    Optional<Rechazo> buscar(UUID tenantId);

    /** SUNAT aceptó las credenciales: se quita la marca. */
    void levantar(UUID tenantId);
}
