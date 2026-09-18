package pe.factura.application.service;

import lombok.RequiredArgsConstructor;
import pe.factura.application.port.in.CompartirComprobanteUseCase;
import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.out.Adjunto;
import pe.factura.application.port.out.CorreoSender;
import pe.factura.application.port.out.TenantRepository;
import pe.factura.domain.DomainException;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.documento.EstadoDocumento;
import pe.factura.domain.tenant.Tenant;

import java.util.ArrayList;
import java.util.List;
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
        Tenant t = tenants.buscar(tenantId).orElseThrow(() -> new DomainException("NO_ENCONTRADO", "Tenant no encontrado"));

        String numero = c.serie() + "-" + c.numero();
        String asunto = nombre(c) + " " + numero + " - " + t.razonSocial();
        StringBuilder cuerpo = new StringBuilder();
        if (mensaje != null && !mensaje.isBlank()) cuerpo.append(mensaje.strip()).append("\n\n");
        cuerpo.append("Adjuntamos la ").append(nombre(c).toLowerCase()).append(" electrónica ").append(numero)
                .append(" emitida el ").append(c.fechaEmision()).append(" por ").append(t.razonSocial()).append(" (RUC ").append(t.ruc()).append(")")
                .append(" por ").append(c.moneda()).append(" ").append(c.totales().total().toPlainString()).append(".\n")
                .append("Incluye la representación impresa (PDF), el XML firmado y la constancia de recepción de SUNAT (CDR).\n");

        List<Adjunto> adjuntos = new ArrayList<>();
        adjuntos.add(new Adjunto(c.nombreArchivo() + ".pdf", "application/pdf", consultar.pdf(tenantId, id)));
        adjuntos.add(new Adjunto(c.nombreArchivo() + ".xml", "application/xml", consultar.xml(tenantId, id)));
        if (c.cdrKey() != null) adjuntos.add(new Adjunto("R-" + c.nombreArchivo() + ".zip", "application/zip", consultar.cdr(tenantId, id)));
        correo.enviar(email, asunto, cuerpo.toString(), adjuntos);
    }

    private static String nombre(Comprobante c) {
        return switch (c.tipo()) {
            case FACTURA -> "Factura";
            case BOLETA -> "Boleta de venta";
            case NOTA_CREDITO -> "Nota de crédito";
            case NOTA_DEBITO -> "Nota de débito";
        };
    }
}
