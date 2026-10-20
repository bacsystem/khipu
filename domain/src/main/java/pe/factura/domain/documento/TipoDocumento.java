package pe.factura.domain.documento;

import lombok.Getter;

import java.util.regex.Pattern;

public enum TipoDocumento {
    FACTURA("01", "F"), BOLETA("03", "B"),
    /** Liquidación de compra (#327): la emite el comprador a quien vende sin RUC; serie {@code [L][A-Z0-9]{3}} (regla 1001 de LiquidacionCompra2_0). */
    LIQUIDACION_COMPRA("04", "L"),
    NOTA_CREDITO("07", "FB"), NOTA_DEBITO("08", "FB"),
    /** Comprobante de retención (#40): lo emite el agente de retención al proveedor al pagarle; serie {@code [R][A-Z0-9]{3}} (regla 1001 de Retenciones1_0). */
    RETENCION("20", "R"),
    /** Comprobante de percepción (#40): lo emite el agente de percepción al cliente al cobrarle; serie {@code [P][A-Z0-9]{3}} (regla 1001 de Percepciones1_0). */
    PERCEPCION("40", "P"),
    /**
     * Guías de remisión electrónicas (#39): la remitente (09, serie {@code T###}) y la transportista (31, serie {@code V###}), como las piden la ruta y el
     * nombre del archivo del servicio REST de la GRE (anexo «manual_url_gre_rest», servicio de envío, campo {@code numSerie}: error 502). No son
     * comprobantes de pago: no se emiten con {@code Comprobante} sino con {@link GuiaRemision}, y se envían por la API REST de SUNAT, no por SOAP.
     */
    GUIA_REMITENTE("09", "T"), GUIA_TRANSPORTISTA("31", "V");

    @Getter private final String codigo;
    private final Pattern serie;

    TipoDocumento(String codigo, String prefijos) {
        this.codigo = codigo;
        this.serie = Pattern.compile("[" + prefijos + "][A-Z0-9]{3}");
    }
    public boolean serieValida(String s) { return s != null && serie.matcher(s).matches(); }
    /**
     * Retención o percepción (#40): un documento del agente que SUNAT designó, UBL 2.0 ({@code Retention}/{@code Perception}), sin líneas de venta, que va a su propio servicio
     * ({@code ol-ti-itemision-otroscpe-gem}) y se revierte en un resumen de reversiones (RR), no en una comunicación de baja.
     */
    public boolean deAgente() { return this == RETENCION || this == PERCEPCION; }
    /** Guía de remisión (09 o 31): no es un comprobante de pago y no pasa por el flujo de {@code Comprobante}. */
    public boolean esGuia() { return this == GUIA_REMITENTE || this == GUIA_TRANSPORTISTA; }
    public static TipoDocumento porCodigo(String codigo) {
        for (TipoDocumento t : values()) if (t.codigo.equals(codigo)) return t;
        throw new IllegalArgumentException("Tipo de documento desconocido: " + codigo);
    }
}
