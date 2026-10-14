package pe.factura.application.port.in;

import pe.factura.domain.documento.Comprobante;

import java.util.List;
import java.util.UUID;

public interface RecuperarCdrUseCase {
    /**
     * Pide a SUNAT (getStatusCdr) la constancia de un comprobante propio ENVIADO, en ERROR_ENVIO o aceptado sin CDR en el
     * storage; si SUNAT la tiene, la guarda y aplica su resultado. Devuelve el comprobante (sin cambios si no hay CDR). Solo factura y sus notas (serie F): una boleta o una
     * nota B responde {@code CDR_NO_CONSULTABLE} (#357), porque SUNAT no les contesta getStatusCdr.
     */
    Comprobante recuperar(UUID tenantId, UUID comprobanteId);
    /**
     * Barrido: intenta recuperar el CDR de todos los comprobantes sin constancia de empresas en producción. Un comprobante que falla no corta el barrido (la consulta va
     * por fecha de emisión: uno atascado al frente dejaría sin recuperar a los más nuevos): el fallo vuelve en {@link Barrido#fallos()} para que quien llama lo registre.
     */
    Barrido recuperarPendientes();

    /** Lo recuperado y lo que falló en una pasada, cada fallo con su comprobante. */
    record Barrido(List<Comprobante> recuperados, List<Fallo> fallos) {}

    record Fallo(Comprobante comprobante, RuntimeException causa) {}
}
