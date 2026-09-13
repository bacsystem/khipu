package pe.factura.domain.documento;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public enum EstadoDocumento {
    RECIBIDO, INVALIDO, FIRMADO, ERROR_ENVIO, PENDIENTE_AGRUPACION, ENVIADO,
    ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO, ANULADO;

    private Set<EstadoDocumento> siguientes = new HashSet<>();

    static {
        RECIBIDO.siguientes = EnumSet.of(FIRMADO, INVALIDO);
        FIRMADO.siguientes = EnumSet.of(ENVIADO, ERROR_ENVIO, PENDIENTE_AGRUPACION);
        ERROR_ENVIO.siguientes = EnumSet.of(ENVIADO, ERROR_ENVIO);
        PENDIENTE_AGRUPACION.siguientes = EnumSet.of(ENVIADO);
        ENVIADO.siguientes = EnumSet.of(ACEPTADO, ACEPTADO_CON_OBS, RECHAZADO, ERROR_ENVIO, PENDIENTE_AGRUPACION);
        ACEPTADO.siguientes = EnumSet.of(ANULADO);
        ACEPTADO_CON_OBS.siguientes = EnumSet.of(ANULADO);
    }

    public boolean puedeTransitarA(EstadoDocumento destino) { return siguientes.contains(destino); }
    public boolean esEnviable() { return this == FIRMADO || this == ERROR_ENVIO; }
    public boolean esFinalAceptado() { return this == ACEPTADO || this == ACEPTADO_CON_OBS; }
}
