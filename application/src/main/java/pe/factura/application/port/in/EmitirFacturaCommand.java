package pe.factura.application.port.in;

import pe.factura.domain.documento.Anticipo;
import pe.factura.domain.documento.Cargo;
import pe.factura.domain.documento.Descuento;
import pe.factura.domain.documento.Detraccion;
import pe.factura.domain.documento.Percepcion;
import pe.factura.domain.documento.RetencionIgv;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import pe.factura.domain.documento.Referencias;
import java.time.LocalDate;
import java.util.List;

public record EmitirFacturaCommand(String serie, Long correlativo, LocalDate fechaEmision, String moneda, String tipoOperacion,
                                   Receptor receptor, List<Item> items, FormaPago formaPago, Descuento descuentoGlobal, List<Cargo> cargos, Detraccion detraccion, RetencionIgv retencionIgv, Percepcion percepcion,
                                   List<Anticipo> anticipos, Referencias referencias, boolean enviarAutomatico) {}
