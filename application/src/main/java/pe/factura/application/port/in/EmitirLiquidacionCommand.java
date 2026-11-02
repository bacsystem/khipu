package pe.factura.application.port.in;

import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.LiquidacionCompra;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.Referencias;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Liquidación de compra (04, #327): la empresa compra a una persona sin RUC y le retiene el IGV y la renta. {@code vendedor} lleva el documento (1, 4 o 7), el nombre y la
 * dirección de su domicilio; {@code liquidacion}, el ubigeo y la condición de ese domicilio, el lugar de la operación y la tasa de la retención (nula = la vigente).
 */
public record EmitirLiquidacionCommand(String serie, Long correlativo, LocalDate fechaEmision, String moneda, String tipoOperacion, Receptor vendedor, LiquidacionCompra liquidacion,
                                       List<Item> items, Referencias referencias, BigDecimal redondeo, BigDecimal tipoCambio, boolean enviarAutomatico, String observaciones) {}
