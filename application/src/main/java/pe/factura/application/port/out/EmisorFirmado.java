package pe.factura.application.port.out;

import pe.factura.domain.tenant.EmisorImpreso;

import java.util.Optional;

/**
 * Lee del XML ya firmado la identidad con la que salió el comprobante.
 *
 * <p>La fuente es el XML y no una copia aparte porque el XML es el documento que SUNAT recibió y el único que existe
 * para los comprobantes emitidos antes de que esto se arreglara: guardar el emisor de ahora en adelante habría dejado
 * mal a todo el historial.
 */
public interface EmisorFirmado {
    /** Vacío si el XML no trae el bloque del emisor o no se puede leer: quien llama decide con qué seguir. */
    Optional<EmisorImpreso> leer(byte[] xmlFirmado);
}
