package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;

import java.util.UUID;

/**
 * Una boleta que pasó el envío individual (1079: «Solo puede enviar el comprobante en un resumen diario») se informa en un resumen diario con su línea en
 * estado 1, hasta el séptimo día (274-H1). Queda ENVIADA hasta que SUNAT responda el ticket; el CDR del resumen la acepta o la rechaza.
 */
public interface InformarEnResumenUseCase {
    /** Devuelve la boleta con su estado nuevo (ENVIADO, o ya resuelto si SUNAT respondió en el acto). */
    Comprobante informar(UUID tenantId, UUID comprobanteId);
}
