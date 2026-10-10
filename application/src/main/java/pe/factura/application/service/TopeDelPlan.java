package pe.factura.application.service;

import java.time.LocalDate;
import java.util.UUID;

/** Controla el tope de documentos del plan antes de numerar un comprobante (#18). Corre dentro de la transacción que emite. */
public interface TopeDelPlan {
    /** {@code LIMITE_PLAN} si la cuenta de la empresa ya llegó al tope de documentos del mes de {@code fechaEmision}. */
    void exigirDisponible(UUID tenantId, LocalDate fechaEmision);
}
