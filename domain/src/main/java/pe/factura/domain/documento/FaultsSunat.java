package pe.factura.domain.documento;

import java.util.Set;

/**
 * Faults SOAP de SUNAT que no son un rechazo del documento sino la señal de que <b>ya lo tiene</b>: un envío anterior llegó aunque khipu no recibió la
 * respuesta (timeout, caída entre el sendBill y el guardado). 1032/1033 «comprobante registrado previamente», 2223 y 4000 (variantes del mismo aviso). Tratarlos
 * como rechazo definitivo dejaba RECHAZADO en khipu un comprobante válido en SUNAT (LOG-01): el cliente lo reemitía y declaraba dos veces. La guía de remisión
 * los conocía desde #39; el comprobante los usa para recuperar la constancia por getStatusCdr en vez de rechazar.
 */
public final class FaultsSunat {
    static final Set<String> YA_EN_SUNAT = Set.of("1032", "1033", "2223", "4000");

    private FaultsSunat() {
    }

    public static boolean yaEnSunat(String codigo) { return codigo != null && YA_EN_SUNAT.contains(codigo.strip()); }
}
