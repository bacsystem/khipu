package pe.factura.domain.tenant;

/**
 * Identidad del emisor tal como quedó en un comprobante ya firmado: lo que la representación impresa tiene que decir.
 *
 * <p>Existe porque el PDF se armaba con los datos de <em>hoy</em>. Si la empresa mudaba su domicilio fiscal, editaba el
 * de un anexo o reasignaba una serie, la impresa salía con la dirección nueva mientras el XML firmado y el CDR de SUNAT
 * llevaban la vieja: dos documentos del mismo comprobante diciendo cosas distintas, y cuál te tocaba dependía de si el
 * PDF ya estaba en caché. Estos cuatro campos son los únicos del emisor que el PDF imprime; el diseño (logo, color,
 * pie) sí es el actual a propósito, y por eso su huella entra en la clave de la caché.
 */
public record EmisorImpreso(String ruc, String razonSocial, String nombreComercial, Domicilio domicilio) {

    /** El tenant que se le pasa al generador del PDF: identidad congelada, diseño actual. */
    public Tenant sobre(Tenant actual) {
        return actual.conIdentidadImpresa(ruc, razonSocial, nombreComercial, domicilio);
    }
}
