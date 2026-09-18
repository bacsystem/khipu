package pe.factura.application.port.in;

import pe.factura.domain.documento.FormaPago;
import pe.factura.domain.documento.Item;
import pe.factura.domain.documento.Receptor;
import java.time.LocalDate;
import java.util.List;

public record EmitirFacturaCommand(String serie, Long correlativo, LocalDate fechaEmision, String moneda, String tipoOperacion,
                                   Receptor receptor, List<Item> items, FormaPago formaPago, boolean enviarAutomatico) {}
