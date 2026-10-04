package pe.factura.application.port.in;

/** Qué cuentas muestra la tabla de consumo del backoffice (#193). */
public enum FiltroDeConsumo {
    TODAS,
    /** Las que ya usaron el umbral de alerta de su tope de documentos o más; los planes sin tope no entran. */
    CERCA_DEL_LIMITE,
    /** Las que tienen el plan vencido: en gracia o ya sin ella. */
    PLAN_VENCIDO
}
