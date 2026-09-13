package pe.factura.domain.documento;

import java.math.BigDecimal;
import java.util.List;

public record Totales(BigDecimal gravado, BigDecimal exonerado, BigDecimal inafecto,
                      BigDecimal igv, BigDecimal total, List<ItemCalculado> items) {

    public static Totales calcular(List<Item> items) {
        BigDecimal gravado = z(), exonerado = z(), inafecto = z(), igv = z();
        List<ItemCalculado> calculados = items.stream().map(ItemCalculado::de).toList();
        for (ItemCalculado c : calculados) {
            switch (c.item().afectacion()) {
                case GRAVADO -> gravado = gravado.add(c.valorVenta());
                case EXONERADO -> exonerado = exonerado.add(c.valorVenta());
                case INAFECTO -> inafecto = inafecto.add(c.valorVenta());
            }
            igv = igv.add(c.igv());
        }
        BigDecimal total = gravado.add(exonerado).add(inafecto).add(igv);
        return new Totales(gravado, exonerado, inafecto, igv, total, calculados);
    }
    private static BigDecimal z() { return BigDecimal.ZERO.setScale(2); }
}
