package pe.factura.application.port.in;

import pe.factura.domain.documento.Anticipo;
import pe.factura.domain.documento.Cargo;
import pe.factura.domain.documento.Descuento;
import pe.factura.domain.documento.Detraccion;
import pe.factura.domain.documento.Exportacion;
import pe.factura.domain.documento.Percepcion;
import pe.factura.domain.documento.RetencionIgv;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.Referencias;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EmitirFacturaCommand(String serie, Long correlativo, LocalDate fechaEmision, LocalDate fechaVencimiento, String moneda, String tipoOperacion,
                                   Receptor receptor, List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, List<Cargo> cargos, Detraccion detraccion, RetencionIgv retencionIgv, Percepcion percepcion,
                                   List<Anticipo> anticipos, Referencias referencias, BigDecimal redondeo, boolean enviarAutomatico, String observaciones, List<String> leyendas,
                                   Exportacion exportacion) {
    /** Sin datos de exportación (Incoterm, país de uso). */
    public EmitirFacturaCommand(String serie, Long correlativo, LocalDate fechaEmision, LocalDate fechaVencimiento, String moneda, String tipoOperacion,
                                Receptor receptor, List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, List<Cargo> cargos, Detraccion detraccion, RetencionIgv retencionIgv, Percepcion percepcion,
                                List<Anticipo> anticipos, Referencias referencias, BigDecimal redondeo, boolean enviarAutomatico, String observaciones, List<String> leyendas) {
        this(serie, correlativo, fechaEmision, fechaVencimiento, moneda, tipoOperacion, receptor, items, formaPago, descuentoGlobal, cargos, detraccion, retencionIgv, percepcion, anticipos, referencias, redondeo, enviarAutomatico, observaciones, leyendas, null);
    }
    /** Sin leyendas del catálogo 52. */
    public EmitirFacturaCommand(String serie, Long correlativo, LocalDate fechaEmision, LocalDate fechaVencimiento, String moneda, String tipoOperacion,
                                Receptor receptor, List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, List<Cargo> cargos, Detraccion detraccion, RetencionIgv retencionIgv, Percepcion percepcion,
                                List<Anticipo> anticipos, Referencias referencias, BigDecimal redondeo, boolean enviarAutomatico, String observaciones) {
        this(serie, correlativo, fechaEmision, fechaVencimiento, moneda, tipoOperacion, receptor, items, formaPago, descuentoGlobal, cargos, detraccion, retencionIgv, percepcion, anticipos, referencias, redondeo, enviarAutomatico, observaciones, List.of());
    }
    /** Sin observaciones (texto libre que solo va al PDF). */
    public EmitirFacturaCommand(String serie, Long correlativo, LocalDate fechaEmision, LocalDate fechaVencimiento, String moneda, String tipoOperacion,
                                Receptor receptor, List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, List<Cargo> cargos, Detraccion detraccion, RetencionIgv retencionIgv, Percepcion percepcion,
                                List<Anticipo> anticipos, Referencias referencias, BigDecimal redondeo, boolean enviarAutomatico) {
        this(serie, correlativo, fechaEmision, fechaVencimiento, moneda, tipoOperacion, receptor, items, formaPago, descuentoGlobal, cargos, detraccion, retencionIgv, percepcion, anticipos, referencias, redondeo, enviarAutomatico, null, List.of());
    }
}
