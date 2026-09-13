package pe.factura.domain.documento;

import java.math.BigDecimal;

public record Item(String codigo, String descripcion, String unidad, BigDecimal cantidad,
                   BigDecimal precioUnitario, TipoAfectacionIgv afectacion) {}
