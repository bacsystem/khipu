package pe.factura.domain.documento;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de una guía de remisión. La GRE va por la API REST de SUNAT y es asíncrona: el envío devuelve un ticket y el CDR llega al consultarlo
 * («manual_url_gre_rest», servicio {@code consultarTicket}: {@code codRespuesta} 98 en proceso, 0 aceptado, 99 con error).
 */
public enum EstadoGuia {
    /** Armada y validada, aún sin firmar: no se guarda nunca en este estado. */
    RECIBIDO,
    /** Numerada y firmada; no se envió (se emitió con {@code enviar_automatico: false}). */
    FIRMADO,
    /** El envío falló (SUNAT no respondió, rechazó las credenciales o no recibió el archivo); ver {@code ultimo_error} y {@code reintento_automatico}. */
    ERROR_ENVIO,
    /**
     * Reservada para un envío en curso: la base la pasa aquí con una actualización condicional antes de llamar a SUNAT, así dos envíos (el outbox y uno a
     * mano) no mandan el mismo número dos veces. No se reenvía sola: si queda aquí sin respuesta (el proceso murió a mitad), no se sabe si SUNAT la recibió,
     * y solo se retoma a mano y a sabiendas ({@code forzar}) pasado un rato.
     */
    ENVIANDO,
    /** SUNAT recibió la guía y dio un ticket; khipu lo consulta hasta tener el CDR. */
    ENVIADO_PENDIENTE_TICKET,
    ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO;

    private Set<EstadoGuia> siguientes = Set.of();

    static {
        RECIBIDO.siguientes = EnumSet.of(FIRMADO);
        FIRMADO.siguientes = EnumSet.of(ENVIANDO, ENVIADO_PENDIENTE_TICKET, ERROR_ENVIO);
        ERROR_ENVIO.siguientes = EnumSet.of(ENVIANDO, ENVIADO_PENDIENTE_TICKET, ERROR_ENVIO);
        ENVIANDO.siguientes = EnumSet.of(ENVIANDO, ENVIADO_PENDIENTE_TICKET, ERROR_ENVIO);
        ENVIADO_PENDIENTE_TICKET.siguientes = EnumSet.of(ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO, ERROR_ENVIO);
    }

    public boolean puedeTransitarA(EstadoGuia destino) { return siguientes.contains(destino); }
    public boolean esEnviable() { return this == FIRMADO || this == ERROR_ENVIO; }
    public boolean esperaTicket() { return this == ENVIADO_PENDIENTE_TICKET; }
    public boolean esFinal() { return this == ACEPTADO || this == ACEPTADO_CON_OBS || this == RECHAZADO; }
}
