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

    /**
     * Si el documento consume del plan de la cuenta (#192): lo que SUNAT aceptó (con o sin observaciones), **aunque después se haya dado de baja** —a
     * {@code ANULADO} solo se llega desde un aceptado, y lo aceptado ya consumió: anular no lo devuelve al cupo ni cambia el consumo de un mes cerrado—. No cuentan
     * los rechazados, los que no llegaron (error de envío, fuera de plazo) ni los que están en camino; la comunicación de baja no suma otro documento. Aparte de
     * {@link #esFinalAceptado()} a propósito: es una regla comercial, y si cambia no debe arrastrar al flujo de estados.
     */
    public boolean cuentaParaElConsumo() { return this == ACEPTADO || this == ACEPTADO_CON_OBS || this == ANULADO; }

    /**
     * Si el documento llegó a emitirse (#15): quedó firmado, con o sin respuesta de SUNAT. No cuentan los que solo se recibieron ni los que no pasaron la validación,
     * que no tienen XML firmado. Sí cuentan los rechazados, los que no llegaron y los dados de baja: se emitieron.
     */
    public boolean fueEmitido() { return this != RECIBIDO && this != INVALIDO; }

    /**
     * Si el documento pide que el emisor haga algo (#15): SUNAT lo rechazó, el envío falló o se pasó el plazo de envío. Es el «atención requerida» de las métricas del
     * portal. Un {@link #ENVIADO} sin respuesta todavía no lo es: aún no falló.
     */
    public boolean requiereAtencion() { return this == RECHAZADO || this == ERROR_ENVIO || this == FUERA_DE_PLAZO; }

    /**
     * Si el documento cuenta en el total facturado (#15): lo que SUNAT aceptó y lo que está en camino y todavía puede aceptarse. No cuentan los rechazados, los que se
     * pasaron del plazo, los dados de baja ni los que nunca se firmaron: no son plata facturada.
     */
    public boolean cuentaComoFacturado() { return this == ACEPTADO || this == ACEPTADO_CON_OBS || this == ENVIADO || this == FIRMADO || this == ERROR_ENVIO || this == PENDIENTE_AGRUPACION; }

    /**
     * Si el documento sigue su camino a SUNAT sin haber fallado ni resuelto (#195): firmado, enviado o esperando la agrupación en un resumen. Es lo que el monitor muestra
     * como «en camino». No incluye {@link #ERROR_ENVIO}, que ya pide atención aunque se vaya a reintentar.
     */
    public boolean estaEnCamino() { return this == FIRMADO || this == ENVIADO || this == PENDIENTE_AGRUPACION; }
}
