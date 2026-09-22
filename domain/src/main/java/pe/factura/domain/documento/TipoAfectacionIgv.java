package pe.factura.domain.documento;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Afectación del IGV por ítem (catálogo 07) y el tributo del catálogo 05 que genera.
 * <ul>
 *   <li>{@code gravada}: se calcula IGV sobre la base (10 y las gratuitas gravadas 11–16) a la tasa del comprobante ({@link TasaIgv}: 18 % o la reducida del padrón).</li>
 *   <li>{@code gratuita}: transferencia sin contraprestación (11–16, 21, 31–37): el precio enviado es el valor
 *       referencial (catálogo 16, código 02), la línea no suma al importe a pagar y su IGV se informa aparte (9996).</li>
 * </ul>
 * El 17 (IVAP) usa la tasa del IVAP, no la del IGV; el 40 (exportación) va sin IGV y solo en los tipos de operación 0200–0208.
 */
@Getter
@RequiredArgsConstructor
public enum TipoAfectacionIgv {
    GRAVADO("10", Tributo.IGV, true, false),
    GRAVADO_RETIRO_PREMIO("11", Tributo.GRA, true, true),
    GRAVADO_RETIRO_DONACION("12", Tributo.GRA, true, true),
    GRAVADO_RETIRO("13", Tributo.GRA, true, true),
    GRAVADO_RETIRO_PUBLICIDAD("14", Tributo.GRA, true, true),
    GRAVADO_BONIFICACION("15", Tributo.GRA, true, true),
    GRAVADO_RETIRO_TRABAJADORES("16", Tributo.GRA, true, true),
    /** Gravado con el IVAP (4 %, tributo 1016) en vez del IGV; no se mezcla con otras afectaciones ni con ISC/ICBPER (2650, 3223). */
    IVAP("17", Tributo.IVAP, true, false),
    EXONERADO("20", Tributo.EXO, false, false),
    EXONERADO_GRATUITO("21", Tributo.GRA, false, true),
    INAFECTO("30", Tributo.INA, false, false),
    INAFECTO_RETIRO_BONIFICACION("31", Tributo.GRA, false, true),
    INAFECTO_RETIRO("32", Tributo.GRA, false, true),
    INAFECTO_RETIRO_MUESTRAS_MEDICAS("33", Tributo.GRA, false, true),
    INAFECTO_RETIRO_CONVENIO_COLECTIVO("34", Tributo.GRA, false, true),
    INAFECTO_RETIRO_PREMIO("35", Tributo.GRA, false, true),
    INAFECTO_RETIRO_PUBLICIDAD("36", Tributo.GRA, false, true),
    INAFECTO_TRANSFERENCIA_GRATUITA("37", Tributo.GRA, false, true),
    /** Exportación (tributo 9995, IGV 0 —3110—): todas las líneas de una factura 0200–0208 la llevan (2642) y ninguna otra (3107). */
    EXPORTACION("40", Tributo.EXP, false, false);

    private final String codigo;
    private final Tributo tributo;
    private final boolean gravada;
    private final boolean gratuita;

    public boolean gravado() { return gravada; }
    public boolean ivap() { return this == IVAP; }
    public boolean exportacion() { return this == EXPORTACION; }

    public static TipoAfectacionIgv porCodigo(String c) {
        for (TipoAfectacionIgv t : values()) if (t.codigo.equals(c)) return t;
        throw new IllegalArgumentException("Afectación IGV desconocida: " + c);
    }
}
