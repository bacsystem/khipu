package pe.factura.application.port.in;

import pe.factura.domain.documento.GuiaRemision;

import java.util.UUID;

/** Envío de una guía de remisión a SUNAT y consulta de su ticket (#39). Lo usan la API (a mano) y el outbox (solo). */
public interface EnviarGuiaRemisionUseCase {
    /** Envía una guía FIRMADO o en ERROR_ENVIO; si SUNAT da el ticket, lo consulta una vez en el acto y deja programadas las siguientes consultas. */
    GuiaRemision enviar(UUID tenantId, UUID guiaId);

    /** Consulta el ticket de una guía ENVIADO_PENDIENTE_TICKET y aplica la respuesta (CDR, rechazo o sigue en proceso). */
    GuiaRemision consultarTicket(UUID tenantId, UUID guiaId);
}
