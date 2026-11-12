package pe.factura.domain.plan;

/**
 * Sobre qué periodo se cuenta el tope de documentos de Producción de un plan (#613). {@code MENSUAL}: el mes calendario de la fecha de emisión, y se renueva el día 1
 * (Emprende, Negocio, Pro). {@code TOTAL}: toda la vida de la cuenta, y no se renueva (Gratis: 10 comprobantes reales para comprobar que todo funciona con SUNAT
 * antes de pagar; al acabarse, la cuenta pasa a un plan de pago).
 */
public enum PeriodoDelTope {
    MENSUAL, TOTAL;

    public boolean seRenueva() { return this == MENSUAL; }
}
