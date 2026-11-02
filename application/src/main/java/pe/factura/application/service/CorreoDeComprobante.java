package pe.factura.application.service;

import pe.factura.application.port.in.ConsultarComprobanteUseCase;
import pe.factura.application.port.out.Adjunto;
import pe.factura.domain.documento.Comprobante;
import pe.factura.domain.tenant.Tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El correo con que un comprobante aceptado llega a su cliente: asunto, cuerpo y adjuntos (PDF, XML firmado y, si lo hay, el CDR). Lo comparten el envío a mano
 * ({@link CompartirComprobanteService}) y el automático al aceptarse ({@link EnviarAlClienteService}, #325), para que el cliente reciba lo mismo por cualquiera de los dos caminos.
 */
record CorreoDeComprobante(String asunto, String cuerpo, List<Adjunto> adjuntos) {

    /** {@code mensaje}: texto opcional del emisor, que va al principio del cuerpo. */
    static CorreoDeComprobante de(Comprobante c, Tenant t, String mensaje, ConsultarComprobanteUseCase consultar) {
        String numero = c.serie() + "-" + c.numero();
        String asunto = nombre(c) + " " + numero + " - " + t.razonSocial();
        StringBuilder cuerpo = new StringBuilder();
        if (mensaje != null && !mensaje.isBlank()) cuerpo.append(mensaje.strip()).append("\n\n");
        // «el comprobante de retención … emitido», «la factura … emitida»: la concordancia sigue al nombre (#40).
        boolean masculino = c.tipo() == pe.factura.domain.documento.TipoDocumento.RETENCION || c.tipo() == pe.factura.domain.documento.TipoDocumento.PERCEPCION;
        cuerpo.append(masculino ? "Adjuntamos el " : "Adjuntamos la ").append(nombre(c).toLowerCase()).append(masculino ? " electrónico " : " electrónica ").append(numero)
                .append(masculino ? " emitido el " : " emitida el ").append(c.fechaEmision()).append(" por ").append(t.razonSocial()).append(" (RUC ").append(t.ruc()).append(")")
                .append(" por ").append(c.moneda()).append(" ").append(c.totales().total().toPlainString()).append(".\n")
                .append("Incluye la representación impresa (PDF), el XML firmado y la constancia de recepción de SUNAT (CDR).\n");

        UUID tenantId = c.tenantId();
        List<Adjunto> adjuntos = new ArrayList<>();
        adjuntos.add(new Adjunto(c.nombreArchivo() + ".pdf", "application/pdf", consultar.pdf(tenantId, c.id())));
        adjuntos.add(new Adjunto(c.nombreArchivo() + ".xml", "application/xml", consultar.xml(tenantId, c.id())));
        if (c.cdrKey() != null) adjuntos.add(new Adjunto("R-" + c.nombreArchivo() + ".zip", "application/zip", consultar.cdr(tenantId, c.id())));
        return new CorreoDeComprobante(asunto, cuerpo.toString(), List.copyOf(adjuntos));
    }

    /** Lo que pesan juntos los adjuntos, en bytes. */
    long pesoDeAdjuntos() { return adjuntos.stream().mapToLong(a -> a.contenido().length).sum(); }

    static String nombre(Comprobante c) {
        return switch (c.tipo()) {
            case FACTURA -> "Factura";
            case BOLETA -> "Boleta de venta";
            case LIQUIDACION_COMPRA -> "Liquidación de compra";
            case NOTA_CREDITO -> "Nota de crédito";
            case NOTA_DEBITO -> "Nota de débito";
            case RETENCION -> "Comprobante de retención";
            case PERCEPCION -> "Comprobante de percepción";
            // #39: la guía de remisión no es un Comprobante; no llega aquí.
            case GUIA_REMITENTE -> "Guía de remisión remitente";
            case GUIA_TRANSPORTISTA -> "Guía de remisión transportista";
        };
    }
}
