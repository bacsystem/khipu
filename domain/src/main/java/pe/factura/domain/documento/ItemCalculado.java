package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ItemCalculado(Item item, BigDecimal valorUnitario, BigDecimal valorVenta,
                            BigDecimal igv, BigDecimal precioVenta, BigDecimal porcentajeIgv) {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    private static final BigDecimal UNO_MAS_IGV = BigDecimal.ONE.add(TASA_IGV);

    public static ItemCalculado de(Item item) {
        BigDecimal precio = item.precioUnitario();
        BigDecimal valorUnitario = item.afectacion().gravado()
                ? precio.divide(UNO_MAS_IGV, 10, RoundingMode.HALF_UP)
                : precio.setScale(10, RoundingMode.HALF_UP);
        BigDecimal valorVenta = valorUnitario.multiply(item.cantidad()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igv = item.afectacion().gravado()
                ? valorVenta.multiply(TASA_IGV).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal precioVenta = valorVenta.add(igv);
        BigDecimal pct = item.afectacion().gravado() ? new BigDecimal("18.00") : new BigDecimal("0.00");
        return new ItemCalculado(item, valorUnitario, valorVenta, igv, precioVenta, pct);
    }
}
