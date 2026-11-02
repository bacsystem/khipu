package pe.factura.application.port.in;

import pe.factura.domain.documento.DatosDeGuia;
import pe.factura.domain.documento.DocumentoDeTraslado;
import pe.factura.domain.documento.ItemDeGuia;
import pe.factura.domain.documento.ParteDeGuia;
import pe.factura.domain.documento.Traslado;

import java.time.LocalDate;
import java.util.List;

/**
 * Pedido de emisión de una guía de remisión (#39). {@code datos} decide el tipo (remitente 09 o transportista 31). Los puntos de partida y de llegada pueden
 * traer solo el código de un establecimiento de la empresa: el servicio completa ubigeo y dirección.
 */
public record EmitirGuiaRemisionCommand(String serie, LocalDate fechaEmision, ParteDeGuia destinatario, DatosDeGuia datos, Traslado traslado, List<ItemDeGuia> items,
                                        List<DocumentoDeTraslado> documentos, String observaciones, boolean enviarAutomatico) {}
