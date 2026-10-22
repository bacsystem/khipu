package pe.factura.application.port.in;

import pe.factura.domain.documento.ComprobanteDeAgente;
import pe.factura.domain.documento.Receptor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Comprobante de retención (20) o de percepción (40), #40: {@code datos.tipo()} dice cuál. {@code contraparte} es el proveedor al que se retiene (con RUC) o el cliente al que se
 * percibe; {@code datos}, el régimen y los documentos que se pagan o cobran. {@code redondeo} va sobre el importe total pagado o cobrado.
 */
public record EmitirAgenteCommand(String serie, Long correlativo, LocalDate fechaEmision, Receptor contraparte, ComprobanteDeAgente datos, BigDecimal redondeo,
                                  boolean enviarAutomatico, String observaciones) {}
