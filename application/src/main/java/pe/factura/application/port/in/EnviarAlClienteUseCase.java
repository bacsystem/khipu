package pe.factura.application.port.in;

import pe.factura.domain.documento.EnvioAlCliente;

import java.util.Optional;
import java.util.UUID;

/** El envío automático del comprobante aceptado a su cliente (#325): la preferencia de la empresa, el estado del envío de un comprobante y la pasada que los manda. */
public interface EnviarAlClienteUseCase {
    /**
     * La preferencia de la empresa. {@code correoDisponible} dice si el servidor puede mandar correos de verdad: sin SMTP no se puede activar, y lo que quedó pendiente se omite
     * en vez de darse por enviado.
     */
    record Preferencia(boolean automatico, boolean correoDisponible) {}

    Preferencia preferencia(UUID tenantId);

    /** Activar sin SMTP configurado lanza {@code CORREO_NO_CONFIGURADO}: la empresa creería que sus clientes reciben lo que nunca sale. Desactivar siempre se puede. */
    Preferencia fijarPreferencia(UUID tenantId, boolean automatico);

    /** El envío del comprobante, si se anotó (la empresa lo tenía activado y el comprobante traía correo cuando SUNAT lo aceptó). */
    Optional<EnvioAlCliente> envioDe(UUID tenantId, UUID documentoId);

    /** Manda los envíos cuyo turno llegó; devuelve cuántos atendió (enviados, fallidos u omitidos). */
    int enviarPendientes();
}
