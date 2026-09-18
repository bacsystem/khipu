package pe.factura.application.port.in;

import pe.factura.domain.documento.Cargo;
import pe.factura.domain.documento.Descuento;
import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.TipoDocumento;

import java.time.LocalDate;
import java.util.List;

/**
 * Nota de crédito (07) o débito (08) sobre una factura de la misma empresa. Con {@code items} vacío o nulo la nota copia
 * los ítems, el descuento global y los cargos de la factura (nota total: anulación 01, devolución total 06); con ítems,
 * cubre solo lo indicado (parcial). Receptor, moneda y tipo de operación se toman siempre de la factura modificada.
 */
public record EmitirNotaCommand(TipoDocumento tipo, String serie, Long correlativo, LocalDate fechaEmision,
                                String serieAfectada, long numeroAfectado, String motivo, String descripcion,
                                List<Item> items, Descuento descuentoGlobal, List<Cargo> cargos, FormaPago formaPago,
                                boolean enviarAutomatico) {
    public boolean copiaLaFactura() { return items == null || items.isEmpty(); }
}
