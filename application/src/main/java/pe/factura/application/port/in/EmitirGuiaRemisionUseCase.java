package pe.factura.application.port.in;

import pe.factura.domain.documento.GuiaRemision;

import java.util.UUID;

/** Emisión de guías de remisión electrónicas (#39): validar, numerar en la serie T###/V###, generar el DespatchAdvice, firmar, validar el XSD, guardar y enviar. */
public interface EmitirGuiaRemisionUseCase {
    /** {@code repetida}: el pedido ya se había hecho con esa clave y se devuelve la guía de entonces, sin emitir otra. */
    record Emision(GuiaRemision guia, boolean repetida) {}

    /** Con {@code idempotencia} nula, cada llamada emite una guía nueva. Las claves son un espacio propio: la misma cadena en facturas o notas es otra clave. */
    Emision emitir(UUID tenantId, EmitirGuiaRemisionCommand cmd, Idempotencia idempotencia);

    /** La guía que {@link #emitir} armaría, con las mismas reglas y los establecimientos resueltos, sin numerarla, firmarla, guardarla ni enviarla. */
    GuiaRemision validar(UUID tenantId, EmitirGuiaRemisionCommand cmd);
}
