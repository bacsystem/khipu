package pe.factura.application.service;

import java.time.LocalDate;
import java.util.UUID;

/** Controla lo que el plan deja emitir (vencimiento y tope de documentos) antes de numerar un comprobante (#18, #285). Corre dentro de la transacción que emite. */
public interface TopeDelPlan {
    /**
     * {@code PLAN_VENCIDO} si el plan de la cuenta de la empresa venció y pasó su gracia (#285); {@code LIMITE_PLAN} si una empresa en Producción ya llegó al tope
     * de documentos (del mes de {@code fechaEmision}, o en total, #613); {@code LIMITE_PRUEBAS} si una en Beta ya llegó al tope de pruebas del mes (#613).
     */
    void exigirDisponible(UUID tenantId, LocalDate fechaEmision);
}
