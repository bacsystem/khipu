package pe.factura.application.port.in;

import pe.factura.domain.documento.GuiaRemision;

import java.util.UUID;

/** Envío de una guía de remisión a SUNAT y consulta de su ticket (#39). Lo usan la API (a mano) y el outbox (solo). */
public interface EnviarGuiaRemisionUseCase {
    /** Envía una guía FIRMADO o en ERROR_ENVIO; si SUNAT da el ticket, lo consulta una vez en el acto y deja programadas las siguientes consultas. */
    GuiaRemision enviar(UUID tenantId, UUID guiaId);

    /**
     * Con {@code forzar}, retoma además una guía que quedó ENVIANDO sin respuesta hace más de 10 min (H2): no se sabe si SUNAT la recibió; úselo solo después de
     * revisar en SOL que no llegó.
     */
    GuiaRemision enviar(UUID tenantId, UUID guiaId, boolean forzar);

    /** El outbox agotó los intentos: la guía en ERROR_ENVIO deja de reintentarse sola, con el motivo (H4). */
    GuiaRemision agotarReintentos(UUID tenantId, UUID guiaId, int intentos);

    /** Consulta el ticket de una guía ENVIADO_PENDIENTE_TICKET y aplica la respuesta (CDR, rechazo o sigue en proceso). */
    GuiaRemision consultarTicket(UUID tenantId, UUID guiaId);
}
