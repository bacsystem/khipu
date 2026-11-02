package pe.factura.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * La baja lógica de una cuenta de cliente (#201): una marca con fecha en la cuenta, nada más. (No es la baja de comprobantes ante SUNAT, de
 * {@link BajaRepository}.) No toca sus empresas, sus comprobantes, sus XML ni sus CDR (la retención es una obligación legal del emisor) y tampoco
 * libera su RUC: la empresa sigue ahí, así que nadie más puede registrarlo.
 */
public interface BajaDeCuentaRepository {
    /** Da de baja la cuenta. {@code true} si no lo estaba; {@code false} si ya estaba de baja (no cambia nada). Atómico: de dos pedidos a la vez, uno solo lo logra. */
    boolean darDeBaja(UUID cuentaId, Instant cuando);

    /** Repone la cuenta. {@code true} si estaba de baja; {@code false} si no lo estaba. Atómico, igual que {@link #darDeBaja}. */
    boolean reponer(UUID cuentaId);

    /** Desde cuándo está de baja la cuenta; {@code null} si está en servicio (o no existe). */
    Instant bajaEn(UUID cuentaId);
}
