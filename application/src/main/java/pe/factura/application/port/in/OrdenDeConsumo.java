package pe.factura.application.port.in;

/** Cómo se ordena la tabla de consumo (#193). */
public enum OrdenDeConsumo {
    /** Por porcentaje del tope usado, de más a menos; los planes sin tope al final. */
    PORCENTAJE,
    /** Por documentos consumidos, de más a menos. */
    DOCUMENTOS
}
