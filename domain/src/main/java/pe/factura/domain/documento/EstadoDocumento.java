package pe.factura.domain.documento;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public enum EstadoDocumento {
    RECIBIDO, INVALIDO, FIRMADO, ERROR_ENVIO, PENDIENTE_AGRUPACION, ENVIADO,
    ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO, ANULADO,
    /** No llegó a SUNAT dentro del plazo de envío ({@link PlazoEnvio}); terminal: hay que emitir de nuevo. */
    FUERA_DE_PLAZO;

    private Set<EstadoDocumento> siguientes = new HashSet<>();

    static {
        RECIBIDO.siguientes = EnumSet.of(FIRMADO, INVALIDO);
        FIRMADO.siguientes = EnumSet.of(ENVIADO, ERROR_ENVIO, PENDIENTE_AGRUPACION, FUERA_DE_PLAZO);
        ERROR_ENVIO.siguientes = EnumSet.of(ENVIADO, ERROR_ENVIO, FUERA_DE_PLAZO);
        PENDIENTE_AGRUPACION.siguientes = EnumSet.of(ENVIADO);
        ENVIADO.siguientes = EnumSet.of(ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO, ERROR_ENVIO, PENDIENTE_AGRUPACION);
        ACEPTADO.siguientes = EnumSet.of(ANULADO);
        ACEPTADO_CON_OBS.siguientes = EnumSet.of(ANULADO);
    }

    public boolean puedeTransitarA(EstadoDocumento destino) { return siguientes.contains(destino); }
    public boolean esEnviable() { return this == FIRMADO || this == ERROR_ENVIO; }
    public boolean esFinalAceptado() { return this == ACEPTADO || this == ACEPTADO_CON_OBS; }
}
