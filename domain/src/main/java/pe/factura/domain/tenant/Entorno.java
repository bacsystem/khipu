package pe.factura.domain.tenant;

public enum Entorno {
    BETA, PRODUCCION;

    /**
     * Si lo que se emite en este entorno consume el plan (#580): solo Producción. Las pruebas en Beta no valen ante SUNAT, así que no suman al consumo ni ocupan
     * el tope de documentos del mes, en ningún plan. Va junto a {@code EstadoDocumento.cuentaParaElConsumo}: un documento consume si cumple las dos.
     */
    public boolean consumeElPlan() { return this == PRODUCCION; }
}
