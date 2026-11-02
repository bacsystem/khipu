package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CompartirComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.util.UUID;

@RequiredArgsConstructor
public class CompartirComprobanteService implements CompartirComprobanteUseCase {
    private final ConsultarComprobanteUseCase consultar;
    private final TenantRepository tenants;
    private final CorreoSender correo;

    @Override
    public void enviarPorCorreo(UUID tenantId, UUID id, String email, String mensaje) {
        Comprobante c = consultar.obtener(tenantId, id);
        // Solo lo que SUNAT ya validó llega al cliente: un FIRMADO puede terminar rechazado y un ANULADO ya no vale.
        if (c.estado() != EstadoDocumento.ACEPTADO && c.estado() != EstadoDocumento.ACEPTADO_CON_OBS)
            throw new DomainException("NO_ACEPTADO", "Solo se envían comprobantes aceptados por SUNAT; " + c.serie() + "-" + c.numero() + " está " + c.estado());
        // Sin SMTP el adaptador escribe en el log y no lanza: responder «enviado» le diría al emisor que su cliente recibió una factura
        // que nunca le llegó. No se intenta: el log no sirve de entrega y armar los adjuntos sería trabajo para nada.
        if (!correo.entregaDeVerdad())
            throw new DomainException("CORREO_NO_CONFIGURADO", "El envío de correos no está habilitado en el servidor: " + c.serie() + "-" + c.numero()
                    + " no se envió. Descargue el PDF y envíelo por su cuenta, o contacte a soporte.");
        Tenant t = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));

        CorreoDeComprobante mail = CorreoDeComprobante.de(c, t, mensaje, consultar);
        try {
            correo.enviar(email, mail.asunto(), mail.cuerpo(), mail.adjuntos());
        } catch (RuntimeException e) {
            // El adaptador de correo lanza sus propias excepciones (SMTP caído, buzón rechazado); para quien pulsó "enviar" es un
            // fallo de entrega, no un error interno. Solo aquí: en la recuperación de contraseña un código específico revelaría si el correo existe.
            throw new DomainException("CORREO_NO_ENVIADO", "No se pudo enviar el correo a " + email + ": " + e.getMessage(), e);
        }
    }
}
