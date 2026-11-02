package pe.factura.domain.documento;

import pe.factura.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Otros tributos de una línea (tributo 9999 «OTROS» del catálogo 05, #317): una tasa sobre el valor de venta de la línea. A diferencia del ISC no entra en la base del IGV
 * (el IGV se calcula sobre el valor de venta más el ISC), pero sí suma al total: Factura2_0 3279 pide total valor de venta + ISC + otros tributos + ICBPER + IGV. El precio
 * unitario que envía el emisor <em>no</em> los incluye: se cobran encima. La tasa es la de 3102 (decimal positivo de 3 enteros y hasta 5 decimales) y el monto la
 * multiplica por la base (3109).
 */
public record OtrosTributos(BigDecimal tasa) {
    private static final BigDecimal MAXIMA = new BigDecimal("999.99999");

    public OtrosTributos {
        if (tasa == null || tasa.signum() <= 0 || tasa.scale() > 5 || tasa.compareTo(MAXIMA) > 0)
            throw new DomainException("OTROS_TRIBUTOS_INVALIDO", "3102 - La tasa de otros tributos es un porcentaje positivo de hasta 3 enteros y 5 decimales");
    }

    /** Monto sobre la base del tributo (el valor de venta de la línea): base × tasa / 100 a dos decimales. */
    public BigDecimal montoSobre(BigDecimal base) {
        return base.multiply(tasa).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
