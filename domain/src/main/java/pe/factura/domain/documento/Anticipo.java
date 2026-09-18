package pe.factura.domain.documento;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Anticipo que se regulariza en esta factura: el cliente pagó por adelantado con una factura de anticipo (ya aceptada
 * por SUNAT) y ahora se descuenta ese importe del total. En el XML va tres veces (reglas 65–66 de la hoja Factura2_0):
 * <ul>
 *   <li>{@code cac:AdditionalDocumentReference}: serie-número de la factura de anticipo, tipo 02 y RUC del emisor (2505, 2520, 2521, 3216–3218).</li>
 *   <li>{@code cac:PrepaidPayment}: identificador de pago e importe pagado, IGV incluido (2503, 3211–3213).</li>
 *   <li>{@code cac:AllowanceCharge} global con código 04/05/06 por el valor sin IGV, que reduce la base del tributo
 *       correspondiente (3277, 3291) y {@code cbc:PrepaidAmount} con la suma de importes (2509, 3282, 3287).</li>
 * </ul>
 * {@code monto} es el valor sin IGV que se descuenta; para anticipos gravados khipu calcula el importe pagado sumando el IGV.
 */
public record Anticipo(String serie, long numero, BigDecimal monto, Afectacion afectacion, LocalDate fechaPago) {

    /** Afectación del anticipo (catálogo 53): decide qué base reduce y el código del descuento global. */
    @Getter
    @RequiredArgsConstructor
    public enum Afectacion {
        GRAVADO("04", Tributo.IGV), EXONERADO("05", Tributo.EXO), INAFECTO("06", Tributo.INA);
        private final String codigo;
        private final Tributo tributo;
    }

    /** Tipo de comprobante que se realizó el anticipo (catálogo 12): khipu solo emite facturas. */
    public static final String TIPO_COMPROBANTE = "02";
    private static final Pattern SERIE = Pattern.compile("F[A-Z0-9]{3}");

    public Anticipo {
        afectacion = afectacion == null ? Afectacion.GRAVADO : afectacion;
        if (serie == null || !SERIE.matcher(serie).matches() || numero <= 0 || numero > 99_999_999L)
            throw new DomainException("ANTICIPO_INVALIDO", "2521 - Indique serie y número de la factura de anticipo (F###-correlativo)");
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2)
            throw new DomainException("ANTICIPO_INVALIDO", "2503 - El monto del anticipo debe ser positivo con hasta 2 decimales");
    }

    public String comprobante() { return serie + "-" + numero; }
    public String codigoSunat() { return afectacion.codigo(); }

    /** Importe realmente pagado con el anticipo (cbc:PaidAmount): valor + IGV si es gravado. */
    public BigDecimal importePagado() {
        return afectacion == Afectacion.GRAVADO
                ? monto.multiply(BigDecimal.ONE.add(ItemCalculado.TASA_IGV)).setScale(2, RoundingMode.HALF_UP)
                : monto.setScale(2, RoundingMode.HALF_UP);
    }
}
